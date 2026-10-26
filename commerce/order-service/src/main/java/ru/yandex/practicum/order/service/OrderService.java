package ru.yandex.practicum.order.service;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.yandex.practicum.order.client.ClientFailures;
import ru.yandex.practicum.order.client.InventoryClient;
import ru.yandex.practicum.order.client.ProductClient;
import ru.yandex.practicum.order.client.dto.InventoryRequest;
import ru.yandex.practicum.order.client.dto.ProductResponse;
import ru.yandex.practicum.order.dto.CreateOrderRequest;
import ru.yandex.practicum.order.dto.OrderDto;
import ru.yandex.practicum.order.dto.OrderItemRequest;
import ru.yandex.practicum.order.entity.Order;
import ru.yandex.practicum.order.entity.OrderItem;
import ru.yandex.practicum.order.entity.OrderStatus;
import ru.yandex.practicum.order.exception.InventoryServiceUnavailableException;
import ru.yandex.practicum.order.exception.NotFoundException;
import ru.yandex.practicum.order.exception.OrderProcessingException;
import ru.yandex.practicum.order.exception.ProductServiceUnavailableException;
import ru.yandex.practicum.order.mapper.OrderMapper;
import ru.yandex.practicum.order.repository.OrderRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private static final String CATALOG_DEGRADATION_REASON =
            "не удалось получить данные каталога, товары ожидают проверки";
    private static final String INVENTORY_DEGRADATION_REASON =
            "не удалось подтвердить резерв на складе";

    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final ProductClient productClient;
    private final InventoryClient inventoryClient;

    @Transactional(readOnly = true)
    public OrderDto getById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Заказ не найден"));
        return orderMapper.toDto(order);
    }

    @Transactional(readOnly = true)
    public List<OrderDto> getAll() {
        return orderRepository.findAll().stream()
                .map(orderMapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<OrderDto> getByEmail(String email) {
        return orderRepository.findByCustomerEmail(email).stream()
                .map(orderMapper::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Создаёт заказ: данные товаров берутся из product-service, остатки резервируются
     * в inventory-service. Вызовы Feign выполняются вне транзакции БД, поэтому
     * транзакция на методе не открывается — save() заводит собственную короткую
     * транзакцию только на запись заказа.
     *
     * <p>Сценарий различает три результата обращения к соседним сервисам:
     * <ul>
     *     <li>успех — заказ сохраняется как {@code CONFIRMED};</li>
     *     <li>бизнес-отказ (товар не найден, снят с продажи, склад ответил отказом) —
     *     заказ не создаётся, уже поставленные резервы снимаются;</li>
     *     <li>техническая деградация (5xx, таймаут, недоступность, открытая цепь
     *     Circuit Breaker) — заказ сохраняется как {@code PENDING_CONFIRMATION}
     *     с пояснением в {@code statusDetails}, поставленные резервы остаются.</li>
     * </ul>
     */
    public OrderDto create(CreateOrderRequest request) {
        Map<Long, Integer> quantityByProductId = groupQuantities(request.items());

        Map<Long, ProductResponse> products = new LinkedHashMap<>();
        boolean catalogDegraded = fetchCatalog(quantityByProductId.keySet(), products);

        List<Reservation> reservations = new ArrayList<>();
        boolean inventoryDegraded;
        try {
            inventoryDegraded = reserveStock(quantityByProductId, reservations);
        } catch (RuntimeException e) {
            releaseReservations(reservations);
            throw e;
        }

        Order order = buildOrder(request, quantityByProductId, products, catalogDegraded, inventoryDegraded);
        try {
            return orderMapper.toDto(orderRepository.save(order));
        } catch (RuntimeException e) {
            releaseReservations(reservations);
            throw e;
        }
    }

    private Map<Long, Integer> groupQuantities(List<OrderItemRequest> items) {
        Map<Long, Integer> quantityByProductId = new LinkedHashMap<>();
        for (OrderItemRequest item : items) {
            quantityByProductId.merge(item.productId(), item.quantity(), Integer::sum);
        }
        return quantityByProductId;
    }

    /**
     * Забирает данные каталога по всем товарам заказа. Бизнес-отказ каталога
     * прерывает создание заказа, техническая недоступность возвращает признак
     * деградации, а сам заказ можно продолжить собирать по productId.
     */
    private boolean fetchCatalog(Iterable<Long> productIds, Map<Long, ProductResponse> products) {
        boolean catalogDegraded = false;
        for (Long productId : productIds) {
            try {
                ProductResponse product = fetchProduct(productId);
                if (!Boolean.TRUE.equals(product.active())) {
                    throw new OrderProcessingException("Товар снят с продажи: " + product.name());
                }
                products.put(productId, product);
            } catch (ProductServiceUnavailableException e) {
                log.warn("product-service недоступен при получении товара {}: {}", productId, e.getMessage());
                catalogDegraded = true;
            }
        }
        return catalogDegraded;
    }

    private ProductResponse fetchProduct(Long productId) {
        ProductResponse product;
        try {
            product = productClient.getProduct(productId);
        } catch (ProductServiceUnavailableException e) {
            throw e;
        } catch (FeignException.NotFound e) {
            throw new OrderProcessingException("Товар не найден: " + productId);
        } catch (RuntimeException e) {
            throw catalogFailure(productId, e);
        }
        if (product == null) {
            throw new ProductServiceUnavailableException(
                    "product-service вернул пустой ответ на запрос товара " + productId);
        }
        return product;
    }

    private RuntimeException catalogFailure(Long productId, RuntimeException cause) {
        if (ClientFailures.businessResponse(cause) != null) {
            log.warn("product-service отклонил запрос товара {}: {}", productId, cause.getMessage());
            return new OrderProcessingException("Не удалось получить данные товара: " + productId);
        }
        log.error("product-service недоступен при получении товара {}", productId, cause);
        return new ProductServiceUnavailableException(
                "product-service недоступен при получении товара " + productId, cause);
    }

    /**
     * Резервирует остатки по товарам заказа. Возвращает признак технической
     * недоступности склада: в этом случае уже поставленные резервы не снимаются,
     * а заказ переводится в {@code PENDING_CONFIRMATION}.
     */
    private boolean reserveStock(Map<Long, Integer> quantityByProductId, List<Reservation> reservations) {
        for (Map.Entry<Long, Integer> entry : quantityByProductId.entrySet()) {
            try {
                reserveStock(entry.getKey(), entry.getValue());
            } catch (InventoryServiceUnavailableException e) {
                log.warn("inventory-service недоступен при резервировании товара {}: {}",
                        entry.getKey(), e.getMessage());
                return true;
            }
            reservations.add(new Reservation(entry.getKey(), entry.getValue()));
        }
        return false;
    }

    private void reserveStock(Long productId, Integer quantity) {
        try {
            inventoryClient.reserve(new InventoryRequest(productId, quantity));
        } catch (InventoryServiceUnavailableException e) {
            throw e;
        } catch (FeignException.NotFound e) {
            throw new OrderProcessingException("Складская запись не найдена: товар " + productId);
        } catch (FeignException.Conflict e) {
            throw new OrderProcessingException("Товара недостаточно на складе: " + productId);
        } catch (RuntimeException e) {
            throw inventoryFailure(productId, e);
        }
    }

    private RuntimeException inventoryFailure(Long productId, RuntimeException cause) {
        if (ClientFailures.businessResponse(cause) != null) {
            log.warn("inventory-service отклонил резервирование товара {}: {}", productId, cause.getMessage());
            return new OrderProcessingException("Не удалось зарезервировать товар: " + productId);
        }
        log.error("inventory-service недоступен при резервировании товара {}", productId, cause);
        return new InventoryServiceUnavailableException(
                "inventory-service недоступен при резервировании товара " + productId, cause);
    }

    private void releaseReservations(List<Reservation> reservations) {
        for (Reservation reservation : reservations) {
            try {
                inventoryClient.release(new InventoryRequest(reservation.productId(), reservation.quantity()));
                log.info("Резерв товара {} ({} шт.) снят после сбоя заказа",
                        reservation.productId(), reservation.quantity());
            } catch (RuntimeException e) {
                log.error("Не удалось снять резерв товара {} ({} шт.) после сбоя заказа",
                        reservation.productId(), reservation.quantity(), e);
            }
        }
    }

    private Order buildOrder(CreateOrderRequest request, Map<Long, Integer> quantityByProductId,
                             Map<Long, ProductResponse> products, boolean catalogDegraded,
                             boolean inventoryDegraded) {
        Order order = new Order();
        order.setCustomerName(request.customerName());
        order.setCustomerEmail(request.customerEmail());

        List<String> degradationReasons = new ArrayList<>();
        if (catalogDegraded) {
            degradationReasons.add(CATALOG_DEGRADATION_REASON);
        }
        if (inventoryDegraded) {
            degradationReasons.add(INVENTORY_DEGRADATION_REASON);
        }

        if (degradationReasons.isEmpty()) {
            order.setStatus(OrderStatus.CONFIRMED);
        } else {
            order.setStatus(OrderStatus.PENDING_CONFIRMATION);
            order.setStatusDetails("Заказ требует ручной проверки: " + String.join("; ", degradationReasons));
        }

        List<OrderItem> items = new ArrayList<>();
        BigDecimal totalPrice = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> entry : quantityByProductId.entrySet()) {
            Long productId = entry.getKey();
            Integer quantity = entry.getValue();
            ProductResponse product = products.get(productId);
            String productName = product == null
                    ? "Товар #" + productId + " (ожидает проверки)"
                    : product.name();
            BigDecimal price = product == null ? BigDecimal.ZERO : product.price();
            items.add(orderMapper.toOrderItem(productId, productName, quantity, price, order));
            totalPrice = totalPrice.add(price.multiply(BigDecimal.valueOf(quantity)));
        }
        order.setItems(items);
        order.setTotalPrice(totalPrice);
        return order;
    }

    private record Reservation(Long productId, Integer quantity) {
    }
}
