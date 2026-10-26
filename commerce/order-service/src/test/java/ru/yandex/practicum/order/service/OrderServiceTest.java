package ru.yandex.practicum.order.service;

import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.yandex.practicum.order.client.InventoryClient;
import ru.yandex.practicum.order.client.ProductClient;
import ru.yandex.practicum.order.client.dto.InventoryRequest;
import ru.yandex.practicum.order.client.dto.InventoryResponse;
import ru.yandex.practicum.order.client.dto.ProductResponse;
import ru.yandex.practicum.order.dto.CreateOrderRequest;
import ru.yandex.practicum.order.dto.OrderDto;
import ru.yandex.practicum.order.dto.OrderItemDto;
import ru.yandex.practicum.order.dto.OrderItemRequest;
import ru.yandex.practicum.order.entity.Order;
import ru.yandex.practicum.order.exception.InventoryServiceUnavailableException;
import ru.yandex.practicum.order.exception.OrderProcessingException;
import ru.yandex.practicum.order.exception.ProductServiceUnavailableException;
import ru.yandex.practicum.order.mapper.OrderMapper;
import ru.yandex.practicum.order.repository.OrderRepository;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductClient productClient;

    @Mock
    private InventoryClient inventoryClient;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, new OrderMapper(), productClient, inventoryClient);
    }

    @Test
    void shouldCreateConfirmedOrderWithProductSnapshotFromCatalog() {
        when(productClient.getProduct(1L))
                .thenReturn(new ProductResponse(1L, "Лампа", new BigDecimal("100.50"), true));
        when(productClient.getProduct(2L))
                .thenReturn(new ProductResponse(2L, "Розетка", new BigDecimal("50.00"), true));
        stubSave();

        OrderDto result = orderService.create(orderRequest(
                new OrderItemRequest(1L, 2),
                new OrderItemRequest(2L, 1)
        ));

        assertThat(result.status()).isEqualTo("CONFIRMED");
        assertThat(result.totalPrice()).isEqualByComparingTo("251.00");
        assertThat(result.items())
                .hasSize(2)
                .anySatisfy(item -> assertThat(item)
                        .extracting(OrderItemDto::productName, OrderItemDto::price)
                        .containsExactly("Лампа", new BigDecimal("100.50")));
        verify(inventoryClient).reserve(new InventoryRequest(1L, 2));
        verify(inventoryClient).reserve(new InventoryRequest(2L, 1));
        verify(inventoryClient, never()).release(any(InventoryRequest.class));
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void shouldFetchAndReserveProductOnceWhenProductIdIsDuplicated() {
        when(productClient.getProduct(1L))
                .thenReturn(new ProductResponse(1L, "Лампа", new BigDecimal("100.00"), true));
        stubSave();

        OrderDto result = orderService.create(orderRequest(
                new OrderItemRequest(1L, 2),
                new OrderItemRequest(1L, 3)
        ));

        verify(productClient, times(1)).getProduct(1L);
        verify(inventoryClient, times(1)).reserve(new InventoryRequest(1L, 5));
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).quantity()).isEqualTo(5);
        assertThat(result.totalPrice()).isEqualByComparingTo("500.00");
    }

    @Test
    void shouldRejectInactiveProductWithoutReserving() {
        when(productClient.getProduct(1L))
                .thenReturn(new ProductResponse(1L, "Снятая лампа", new BigDecimal("100.00"), false));

        assertThatThrownBy(() -> orderService.create(orderRequest(new OrderItemRequest(1L, 1))))
                .isInstanceOf(OrderProcessingException.class)
                .hasMessageContaining("снят с продажи");

        verifyNoInteractions(inventoryClient);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void shouldRejectUnknownProductWithoutReserving() {
        when(productClient.getProduct(404L)).thenThrow(feignError(404));

        assertThatThrownBy(() -> orderService.create(orderRequest(new OrderItemRequest(404L, 1))))
                .isInstanceOf(OrderProcessingException.class)
                .hasMessageContaining("не найден");

        verifyNoInteractions(inventoryClient);
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void shouldReleaseSuccessfulReservationsWhenStockIsInsufficient() {
        when(productClient.getProduct(1L))
                .thenReturn(new ProductResponse(1L, "Лампа", new BigDecimal("100.00"), true));
        when(productClient.getProduct(2L))
                .thenReturn(new ProductResponse(2L, "Розетка", new BigDecimal("50.00"), true));
        when(inventoryClient.reserve(new InventoryRequest(1L, 5)))
                .thenReturn(new InventoryResponse(true, 95, "зарезервировано"));
        when(inventoryClient.reserve(new InventoryRequest(2L, 10)))
                .thenThrow(feignError(409));

        assertThatThrownBy(() -> orderService.create(orderRequest(
                new OrderItemRequest(1L, 5),
                new OrderItemRequest(2L, 10)
        )))
                .isInstanceOf(OrderProcessingException.class)
                .hasMessageContaining("недостаточно");

        verify(inventoryClient).release(new InventoryRequest(1L, 5));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void shouldNotReleaseAnythingWhenFirstReserveFails() {
        when(productClient.getProduct(1L))
                .thenReturn(new ProductResponse(1L, "Лампа", new BigDecimal("100.00"), true));
        when(inventoryClient.reserve(any(InventoryRequest.class))).thenThrow(feignError(409));

        assertThatThrownBy(() -> orderService.create(orderRequest(new OrderItemRequest(1L, 5))))
                .isInstanceOf(OrderProcessingException.class);

        verify(inventoryClient, never()).release(any(InventoryRequest.class));
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void shouldSavePendingConfirmationOrderWhenCatalogIsUnavailable() {
        when(productClient.getProduct(7L)).thenThrow(new ProductServiceUnavailableException("каталог недоступен"));
        stubSave();

        OrderDto result = orderService.create(orderRequest(new OrderItemRequest(7L, 2)));

        assertThat(result.status()).isEqualTo("PENDING_CONFIRMATION");
        assertThat(result.statusDetails()).isNotBlank();
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).productName()).isEqualTo("Товар #7 (ожидает проверки)");
        assertThat(result.items().get(0).price()).isEqualByComparingTo("0");
        assertThat(result.totalPrice()).isEqualByComparingTo("0");
        verify(inventoryClient).reserve(new InventoryRequest(7L, 2));
    }

    @Test
    void shouldSavePendingConfirmationOrderWhenInventoryIsUnavailable() {
        when(productClient.getProduct(1L))
                .thenReturn(new ProductResponse(1L, "Лампа", new BigDecimal("100.00"), true));
        when(inventoryClient.reserve(any(InventoryRequest.class)))
                .thenThrow(new InventoryServiceUnavailableException("склад недоступен"));
        stubSave();

        OrderDto result = orderService.create(orderRequest(new OrderItemRequest(1L, 2)));

        assertThat(result.status()).isEqualTo("PENDING_CONFIRMATION");
        assertThat(result.statusDetails()).contains("склад");
        assertThat(result.items().get(0).productName()).isEqualTo("Лампа");
        assertThat(result.items().get(0).price()).isEqualByComparingTo("100.00");
        assertThat(result.totalPrice()).isEqualByComparingTo("200.00");
        verify(inventoryClient, never()).release(any(InventoryRequest.class));
    }

    @Test
    void shouldMixCatalogSnapshotAndPlaceholderWhenOnlyOneProductIsUnavailable() {
        when(productClient.getProduct(1L))
                .thenReturn(new ProductResponse(1L, "Лампа", new BigDecimal("100.00"), true));
        when(productClient.getProduct(2L))
                .thenThrow(new ProductServiceUnavailableException("каталог недоступен"));
        stubSave();

        OrderDto result = orderService.create(orderRequest(
                new OrderItemRequest(1L, 1),
                new OrderItemRequest(2L, 2)
        ));

        assertThat(result.status()).isEqualTo("PENDING_CONFIRMATION");
        assertThat(result.statusDetails()).isNotBlank();
        assertThat(result.items())
                .hasSize(2)
                .anySatisfy(item -> assertThat(item)
                        .extracting(OrderItemDto::productName, OrderItemDto::price)
                        .containsExactly("Лампа", new BigDecimal("100.00")))
                .anySatisfy(item -> assertThat(item)
                        .extracting(OrderItemDto::productName, OrderItemDto::price)
                        .containsExactly("Товар #2 (ожидает проверки)", BigDecimal.ZERO));
        assertThat(result.totalPrice()).isEqualByComparingTo("100.00");
        verify(inventoryClient).reserve(new InventoryRequest(1L, 1));
        verify(inventoryClient).reserve(new InventoryRequest(2L, 2));
    }

    @Test
    void shouldRejectOrderOnBusinessFailureEvenWhenCatalogIsUnavailable() {
        when(productClient.getProduct(1L)).thenThrow(new ProductServiceUnavailableException("каталог недоступен"));
        when(inventoryClient.reserve(any(InventoryRequest.class))).thenThrow(feignError(409));

        assertThatThrownBy(() -> orderService.create(orderRequest(new OrderItemRequest(1L, 5))))
                .isInstanceOf(OrderProcessingException.class)
                .hasMessageContaining("недостаточно");

        verify(orderRepository, never()).save(any(Order.class));
    }

    private void stubSave() {
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static CreateOrderRequest orderRequest(OrderItemRequest... items) {
        return new CreateOrderRequest("Тестовый покупатель", "test@example.com", List.of(items));
    }

    private static FeignException feignError(int status) {
        Request request = Request.create(Request.HttpMethod.GET, "/api/test",
                Collections.emptyMap(), new byte[0], StandardCharsets.UTF_8);
        Response response = Response.builder()
                .status(status)
                .reason("error")
                .request(request)
                .headers(Collections.emptyMap())
                .body("", StandardCharsets.UTF_8)
                .build();
        return FeignException.errorStatus("call", response);
    }
}
