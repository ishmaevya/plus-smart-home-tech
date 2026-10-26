package ru.yandex.practicum.gateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import ru.yandex.practicum.gateway.ApiGateway;

import static org.springframework.web.reactive.function.server.RequestPredicates.path;

@SpringBootTest(classes = ApiGateway.class)
@AutoConfigureWebTestClient
@Import(GatewaySecurityConfigTest.TestBackendConfig.class)
class GatewaySecurityConfigTest {

    @Autowired
    private WebTestClient webTestClient;

    @TestConfiguration
    static class TestBackendConfig {
        @Bean
        public RouterFunction<ServerResponse> testRoutes() {
            return RouterFunctions.route(path("/api/products/**"), req -> ServerResponse.ok().build())
                    .andRoute(path("/api/categories/**"), req -> ServerResponse.ok().build())
                    .andRoute(path("/api/inventory/**"), req -> ServerResponse.ok().build())
                    .andRoute(path("/api/orders/**"), req -> ServerResponse.ok().build())
                    .andRoute(path("/api/unknown/**"), req -> ServerResponse.ok().build());
        }
    }

    @Test
    void optionsShouldBePermitAll() {
        webTestClient.options().uri("/api/orders")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void getProductsAnonymousShouldBePermitAll() {
        webTestClient.get().uri("/api/products/1")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void getCategoriesAnonymousShouldBePermitAll() {
        webTestClient.get().uri("/api/categories/1")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void getInventoryAnonymousShouldBePermitAll() {
        webTestClient.get().uri("/api/inventory/1")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void postOrdersAnonymousShouldBeUnauthorized() {
        webTestClient.post().uri("/api/orders")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void postOrdersIvanShouldBeOk() {
        webTestClient.post().uri("/api/orders")
                .headers(headers -> headers.setBasicAuth("ivan", "ivan"))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void postProductsIvanShouldBeForbidden() {
        webTestClient.post().uri("/api/products")
                .headers(headers -> headers.setBasicAuth("ivan", "ivan"))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void postProductsAnnaShouldBeOk() {
        webTestClient.post().uri("/api/products")
                .headers(headers -> headers.setBasicAuth("anna", "anna"))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void getOrdersIvanShouldBeForbidden() {
        webTestClient.get().uri("/api/orders")
                .headers(headers -> headers.setBasicAuth("ivan", "ivan"))
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void getOrdersAnnaShouldBeOk() {
        webTestClient.get().uri("/api/orders")
                .headers(headers -> headers.setBasicAuth("anna", "anna"))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void getOrdersByEmailIvanShouldBeOk() {
        webTestClient.get().uri("/api/orders/by-email?email=test@test.com")
                .headers(headers -> headers.setBasicAuth("ivan", "ivan"))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void getOrdersByIdIvanShouldBeOk() {
        webTestClient.get().uri("/api/orders/123")
                .headers(headers -> headers.setBasicAuth("ivan", "ivan"))
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    void getOrdersByIdAnonymousShouldBeUnauthorized() {
        webTestClient.get().uri("/api/orders/123")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void unknownRouteAnnaShouldBeForbidden() {
        webTestClient.get().uri("/api/unknown/route")
                .headers(headers -> headers.setBasicAuth("anna", "anna"))
                .exchange()
                .expectStatus().isForbidden();
    }
}
