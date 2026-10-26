package ru.yandex.practicum.order.client;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

/**
 * Пробрасывает технический заголовок X-Request-Id во все исходящие Feign-запросы:
 * если входящий HTTP-запрос уже содержит такой заголовок — передаём его дальше,
 * иначе генерируем новый идентификатор.
 */
@Component
public class RequestIdInterceptor implements RequestInterceptor {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    public void apply(RequestTemplate template) {
        String requestId = null;
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            requestId = servletAttributes.getRequest().getHeader(REQUEST_ID_HEADER);
        }
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }
        template.header(REQUEST_ID_HEADER, requestId);
    }
}
