package ai.generalcontracts.startup.observability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestCorrelationFilterTest {
    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

    @Test
    void propagatesSafeIdentifiersAndClearsThreadContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "request-42");
        request.addHeader("X-Correlation-Id", "correlation-42");
        request.addHeader("X-Transaction-Id", "tx-42");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            assertThat(MDC.get("requestId")).isEqualTo("request-42");
            assertThat(MDC.get("correlationId")).isEqualTo("correlation-42");
            assertThat(MDC.get("transactionId")).isEqualTo("tx-42");
        });

        assertThat(response.getHeader("X-Request-Id")).isEqualTo("request-42");
        assertThat(response.getHeader("X-Correlation-Id")).isEqualTo("correlation-42");
        assertThat(response.getHeader("X-Transaction-Id")).isEqualTo("tx-42");
        assertThat(MDC.get("requestId")).isNull();
        assertThat(MDC.get("correlationId")).isNull();
        assertThat(MDC.get("transactionId")).isNull();
    }

    @Test
    void replacesUntrustedIdentifiers() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-Id", "bad\r\nforged=true");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
        });

        assertThat(response.getHeader("X-Correlation-Id")).matches("[0-9a-f-]{36}");
        assertThat(response.getHeader("X-Request-Id")).matches("[0-9a-f-]{36}");
        assertThat(response.getHeader("X-Transaction-Id")).isNull();
    }
}
