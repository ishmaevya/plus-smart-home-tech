package ru.yandex.practicum.order;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import feign.Request;
import feign.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import ru.yandex.practicum.order.client.InventoryClient;
import ru.yandex.practicum.order.client.ProductClient;
import ru.yandex.practicum.order.client.dto.InventoryRequest;
import ru.yandex.practicum.order.client.dto.InventoryResponse;
import ru.yandex.practicum.order.client.dto.ProductResponse;
import ru.yandex.practicum.order.dto.CreateOrderRequest;
import ru.yandex.practicum.order.dto.OrderItemRequest;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@SuppressWarnings("unchecked")
class OrderServiceAcceptanceTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @MockBean
    private ProductClient productClient;

    @MockBean
    private InventoryClient inventoryClient;

    @BeforeEach
    void stubCatalog() {
        when(productClient.getProduct(1L))
                .thenReturn(new ProductResponse(1L, "Acceptance Smart Lamp",
                        new BigDecimal("3490.00"), true));
        when(productClient.getProduct(2L))
                .thenReturn(new ProductResponse(2L, "Acceptance Smart Plug",
                        new BigDecimal("1290.00"), true));
        when(inventoryClient.reserve(any(InventoryRequest.class)))
                .thenReturn(new InventoryResponse(true, 100, "Товар успешно зарезервирован"));
        when(inventoryClient.release(any(InventoryRequest.class)))
                .thenReturn(new InventoryResponse(true, 100, "Резерв снят"));
    }

    @Test
    void shouldCreateOrderStoreProductSnapshotAndFindOrderByIdAndEmail() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(
                "Acceptance Buyer",
                "acceptance-buyer@example.com",
                List.of(
                        new OrderItemRequest(1L, 2),
                        new OrderItemRequest(2L, 1)
                )
        );

        MvcResult createResponse = postJson("/api/orders", request);

        assertThat(status(createResponse))
                .as("POST /api/orders должен создавать заказ и возвращать HTTP 201 Created")
                .isEqualTo(201);
        Map<String, Object> created = readMap(createResponse);
        Long orderId = asLong(created.get("id"));
        assertThat(orderId)
                .as("Созданный заказ должен содержать поле id")
                .isNotNull();
        assertThat(created.get("status"))
                .as("Заказ с зарезервированным товаром должен сохраняться в статусе CONFIRMED")
                .isEqualTo("CONFIRMED");
        assertThat(asDecimal(created.get("totalPrice")))
                .as("totalPrice должен рассчитываться по ценам из product-service")
                .isEqualByComparingTo("8270.00");
        assertThat((List<?>) created.get("items"))
                .as("Заказ должен хранить позиции заказа")
                .hasSize(2)
                .anySatisfy(item -> assertThat((Map<String, Object>) item)
                        .as("Позиция заказа должна хранить снимок названия и цены из product-service")
                        .containsEntry("productName", "Acceptance Smart Lamp"));

        verify(inventoryClient).reserve(new InventoryRequest(1L, 2));
        verify(inventoryClient).reserve(new InventoryRequest(2L, 1));

        MvcResult byIdResponse = mvc.perform(get("/api/orders/{id}", orderId)).andReturn();

        assertThat(status(byIdResponse))
                .as("GET /api/orders/{id} должен возвращать созданный заказ")
                .isEqualTo(200);
        assertThat(readMap(byIdResponse).get("customerEmail"))
                .as("GET /api/orders/{id} должен вернуть заказ с ожидаемым email клиента")
                .isEqualTo("acceptance-buyer@example.com");

        MvcResult byEmailResponse = mvc.perform(get("/api/orders/by-email")
                .param("email", "acceptance-buyer@example.com"))
                .andReturn();

        assertThat(status(byEmailResponse))
                .as("GET /api/orders/by-email?email=... должен возвращать заказы клиента")
                .isEqualTo(200);
        assertThat(readList(byEmailResponse))
                .as("Поиск заказов по email должен вернуть созданный заказ")
                .anySatisfy(item -> assertThat(item)
                        .containsEntry("customerEmail", "acceptance-buyer@example.com"));
    }

    @Test
    void shouldReturnBadRequestForInvalidOrderPayload() throws Exception {
        CreateOrderRequest invalidRequest = new CreateOrderRequest(
                "",
                "not-an-email",
                List.of()
        );

        MvcResult response = postJson("/api/orders", invalidRequest);

        assertThat(status(response))
                .as("POST /api/orders с невалидным телом запроса должен возвращать HTTP 400 Bad Request")
                .isEqualTo(400);
        assertThat(readMap(response))
                .as("Ответ ошибки должен содержать сообщение и детали валидации")
                .containsKeys("message", "validationErrors");
    }

    @Test
    void shouldReturnUnprocessableEntityWhenProductNotFound() throws Exception {
        when(productClient.getProduct(999L)).thenThrow(feignError(404));

        CreateOrderRequest request = new CreateOrderRequest(
                "Acceptance Buyer",
                "acceptance-buyer@example.com",
                List.of(new OrderItemRequest(999L, 1))
        );

        MvcResult response = postJson("/api/orders", request);

        assertThat(status(response))
                .as("Заказ с несуществующим товаром должен отклоняться с HTTP 422")
                .isEqualTo(422);
        verify(inventoryClient, never()).reserve(any(InventoryRequest.class));
    }

    @Test
    void shouldRejectInactiveProductWithoutReservingStock() throws Exception {
        when(productClient.getProduct(3L))
                .thenReturn(new ProductResponse(3L, "Acceptance Inactive Lamp",
                        new BigDecimal("990.00"), false));

        CreateOrderRequest request = new CreateOrderRequest(
                "Acceptance Buyer",
                "acceptance-buyer@example.com",
                List.of(new OrderItemRequest(3L, 1))
        );

        MvcResult response = postJson("/api/orders", request);

        assertThat(status(response))
                .as("Заказ с товаром, снятым с продажи, должен отклоняться с HTTP 422")
                .isEqualTo(422);
        verify(inventoryClient, never()).reserve(any(InventoryRequest.class));
    }

    private MvcResult postJson(String url, Object body) throws Exception {
        return mvc.perform(post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)))
                .andReturn();
    }

    private static FeignException feignError(int status) {
        Request request = Request.create(Request.HttpMethod.GET, "/api/products/test",
                Collections.emptyMap(), new byte[0], StandardCharsets.UTF_8);
        Response response = Response.builder()
                .status(status)
                .reason("error")
                .request(request)
                .headers(Collections.emptyMap())
                .body("", StandardCharsets.UTF_8)
                .build();
        return FeignException.errorStatus("getProduct", response);
    }

    private static int status(MvcResult result) {
        return result.getResponse().getStatus();
    }

    private Map<String, Object> readMap(MvcResult result) throws Exception {
        return json.readValue(result.getResponse().getContentAsString(), new TypeReference<>() {
        });
    }

    private List<Map<String, Object>> readList(MvcResult result) throws Exception {
        return json.readValue(result.getResponse().getContentAsString(), new TypeReference<>() {
        });
    }

    private static Long asLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    private static BigDecimal asDecimal(Object value) {
        return new BigDecimal(value.toString());
    }
}
