package ru.yandex.practicum.order.client;

import feign.FeignException;

/**
 * Разбирает причину сбоя вызова Feign-клиента, чтобы отличить бизнес-отказ
 * соседнего сервиса (4xx) от технической деградации (5xx, таймаут, обрыв
 * соединения, открытая цепь Circuit Breaker).
 *
 * <p>С Circuit Breaker исключение приходит из fallback-фабрики, часто обёрнутое
 * в {@code ExecutionException} таймаутера, поэтому причина ищется по всей цепочке.
 */
public final class ClientFailures {

    private static final int MAX_CAUSE_DEPTH = 10;

    private ClientFailures() {
    }

    /**
     * Возвращает бизнес-ответ соседнего сервиса из цепочки причин или {@code null},
     * если сбой технический.
     */
    public static FeignException businessResponse(Throwable cause) {
        Throwable current = cause;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current instanceof FeignException feignException && isBusinessStatus(feignException.status())) {
                return feignException;
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return null;
    }

    /**
     * Краткое описание корневой причины сбоя для лога: таймаут, обрыв соединения
     * или открытая цепь Circuit Breaker.
     */
    public static String describe(Throwable cause) {
        Throwable root = cause;
        for (int depth = 0; root.getCause() != null && root.getCause() != root && depth < MAX_CAUSE_DEPTH; depth++) {
            root = root.getCause();
        }
        String message = root.getMessage();
        return message == null ? root.getClass().getSimpleName() : root.getClass().getSimpleName() + ": " + message;
    }

    private static boolean isBusinessStatus(int status) {
        return status >= 400 && status < 500;
    }
}
