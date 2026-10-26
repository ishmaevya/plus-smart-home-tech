package ru.yandex.practicum.order.client;

import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.order.client.dto.InventoryRequest;
import ru.yandex.practicum.order.client.dto.InventoryResponse;
import ru.yandex.practicum.order.exception.InventoryServiceUnavailableException;

/**
 * Резервный путь вызовов склада: обычный вызов не прошёл, поэтому фиксируем
 * в логе причину сбоя и сообщаем оркестратору заказа о технической деградации.
 * Бизнес-отказ (4xx) не маскируется под деградацию и пробрасывается как есть.
 */
@Component
public class InventoryClientFallbackFactory implements FallbackFactory<InventoryClient> {

    private static final Logger log = LoggerFactory.getLogger(InventoryClientFallbackFactory.class);

    @Override
    public InventoryClient create(Throwable cause) {
        return new InventoryClient() {

            @Override
            public InventoryResponse reserve(InventoryRequest request) {
                throw unavailable("резервировании", request, cause);
            }

            @Override
            public InventoryResponse release(InventoryRequest request) {
                throw unavailable("снятии резерва", request, cause);
            }
        };
    }

    private RuntimeException unavailable(String operation, InventoryRequest request, Throwable cause) {
        FeignException businessResponse = ClientFailures.businessResponse(cause);
        if (businessResponse != null) {
            return businessResponse;
        }
        log.error("inventory-service недоступен при {} товара {}: {}",
                operation, request.productId(), ClientFailures.describe(cause));
        return new InventoryServiceUnavailableException(
                "inventory-service недоступен при " + operation + " товара " + request.productId(), cause);
    }
}
