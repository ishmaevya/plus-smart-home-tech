package ru.yandex.practicum.order.exception;

/**
 * Техническая недоступность inventory-service: сервис не ответил, вернул 5xx,
 * не уложился в таймаут или вызов заблокировала открытая цепь Circuit Breaker.
 * В отличие от бизнес-отказа, такой вызов не отклоняет заказ, а переводит его
 * в статус {@code PENDING_CONFIRMATION}.
 */
public class InventoryServiceUnavailableException extends RuntimeException {

    public InventoryServiceUnavailableException(String message) {
        super(message);
    }

    public InventoryServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
