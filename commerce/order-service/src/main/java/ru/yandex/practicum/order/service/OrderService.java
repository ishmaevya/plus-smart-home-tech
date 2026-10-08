package ru.yandex.practicum.order.service;

import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import ru.yandex.practicum.order.exception.NotFoundException;
import ru.yandex.practicum.order.exception.OrderProcessingException;
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
     */
    public OrderDto create(CreateOrderRequest request) {
        Map<Long, Integer> quantityByProductId = groupQuantities(request.items());

        List<Reservation> reservations = new ArrayList<>();
        try {
            Map<Long, ProductResponse> products = fetchProducts(quantityByProductId.keySet());

            for (Map.Entry<Long, Integer> entry : quantityByProductId.entrySet()) {
                reserveStock(entry.getKey(), entry.getValue());
                reservations.add(new Reservation(entry.getKey(), entry.getValue()));
            }

            Order order = buildOrder(request, quantityByProductId, products);
            Order saved = orderRepository.save(order);
            return orderMapper.toDto(saved);
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

    private Map<Long, ProductResponse> fetchProducts(Iterable<Long> productIds) {
        Map<Long, ProductResponse> products = new LinkedHashMap<>();
        for (Long productId : productIds) {
            ProductResponse product = fetchProduct(productId);
            if (!Boolean.TRUE.equals(product.active())) {
                throw new OrderProcessingException("Товар снят с продажи: " + product.name());
            }
            products.put(productId, product);
        }
        return products;
    }

    private ProductResponse fetchProduct(Long productId) {
        ProductResponse product;
        try {
            product = productClient.getProduct(productId);
        } catch (FeignException.NotFound e) {
            throw new OrderProcessingException("Товар не найден: " + productId);
        } catch (FeignException e) {
            log.error("product-service не ответил на запрос товара {}", productId, e);
            throw new OrderProcessingException("Не удалось получить данные товара: " + productId);
        } catch (RuntimeException e) {
            log.error("Не удалось получить товар {} из product-service", productId, e);
            throw new OrderProcessingException("Не удалось получить данные товара: " + productId);
        }
        if (product == null) {
            throw new OrderProcessingException("Не удалось получить данные товара: " + productId);
        }
        return product;
    }

    private void reserveStock(Long productId, Integer quantity) {
        try {
            inventoryClient.reserve(new InventoryRequest(productId, quantity));
        } catch (FeignException.NotFound e) {
            throw new OrderProcessingException("Складская запись не найдена: товар " + productId);
        } catch (FeignException.Conflict e) {
            throw new OrderProcessingException("Товара недостаточно на складе: " + productId);
        } catch (FeignException e) {
            log.error("inventory-service не зарезервировал товар {} в количестве {}", productId, quantity, e);
            throw new OrderProcessingException("Не удалось зарезервировать товар: " + productId);
        } catch (RuntimeException e) {
            log.error("Не удалось зарезервировать товар {} в количестве {}", productId, quantity, e);
            throw new OrderProcessingException("Не удалось зарезервировать товар: " + productId);
        }
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
                             Map<Long, ProductResponse> products) {
        Order order = new Order();
        order.setCustomerName(request.customerName());
        order.setCustomerEmail(request.customerEmail());
        order.setStatus(OrderStatus.CONFIRMED);

        List<OrderItem> items = new ArrayList<>();
        BigDecimal totalPrice = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> entry : quantityByProductId.entrySet()) {
            ProductResponse product = products.get(entry.getKey());
            items.add(orderMapper.toOrderItem(
                    product.id(), product.name(), entry.getValue(), product.price(), order));
            totalPrice = totalPrice.add(product.price().multiply(BigDecimal.valueOf(entry.getValue())));
        }
        order.setItems(items);
        order.setTotalPrice(totalPrice);
        return order;
    }

    private record Reservation(Long productId, Integer quantity) {
    }
}
