package ru.yandex.practicum.order.client;

import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

class RequestIdInterceptorTest {

    private final RequestIdInterceptor interceptor = new RequestIdInterceptor();

    @AfterEach
    void resetContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void shouldPropagateIncomingRequestIdHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "incoming-request-42");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertThat(template.headers().get("X-Request-Id"))
                .containsExactly("incoming-request-42");
    }

    @Test
    void shouldGenerateRequestIdWhenIncomingHeaderIsMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertThat(template.headers().get("X-Request-Id"))
                .hasSize(1)
                .first()
                .asString()
                .isNotBlank();
    }

    @Test
    void shouldGenerateRequestIdWhenThereIsNoRequestContext() {
        RequestTemplate template = new RequestTemplate();
        interceptor.apply(template);

        assertThat(template.headers().get("X-Request-Id"))
                .hasSize(1)
                .first()
                .asString()
                .isNotBlank();
    }
}
