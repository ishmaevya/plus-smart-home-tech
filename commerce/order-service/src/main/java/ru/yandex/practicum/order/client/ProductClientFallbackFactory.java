package ru.yandex.practicum.order.client;

import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;
import ru.yandex.practicum.order.exception.ProductServiceUnavailableException;

/**
 * Резервный путь вызовов каталога: обычный вызов не прошёл, поэтому фиксируем
 * в логе причину сбоя и сообщаем оркестратору заказа о технической деградации.
 * Бизнес-отказ (4xx) не маскируется под деградацию и пробрасывается как есть.
 */
@Component
public class ProductClientFallbackFactory implements FallbackFactory<ProductClient> {

    private static final Logger log = LoggerFactory.getLogger(ProductClientFallbackFactory.class);

    @Override
    public ProductClient create(Throwable cause) {
        return productId -> {
            FeignException businessResponse = ClientFailures.businessResponse(cause);
            if (businessResponse != null) {
                throw businessResponse;
            }
            log.error("product-service недоступен при получении товара {}: {}",
                    productId, ClientFailures.describe(cause));
            throw new ProductServiceUnavailableException(
                    "product-service недоступен при получении товара " + productId, cause);
        };
    }
}
