package ru.yandex.practicum.order.exception;

/**
 * Техническая недоступность product-service: сервис не ответил, вернул 5xx,
 * не уложился в таймаут или вызов заблокировала открытая цепь Circuit Breaker.
 * В отличие от бизнес-отказа, такой вызов не отклоняет заказ, а переводит его
 * в статус {@code PENDING_CONFIRMATION}.
 */
public class ProductServiceUnavailableException extends RuntimeException {

    public ProductServiceUnavailableException(String message) {
        super(message);
    }

    public ProductServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
