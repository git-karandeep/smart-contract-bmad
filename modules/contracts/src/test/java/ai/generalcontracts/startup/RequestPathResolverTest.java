package ai.generalcontracts.startup;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class RequestPathResolverTest {
    @Test
    void prefersServletPathWhenAvailable() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setServletPath("/orders");
        request.setRequestURI("/context/orders");
        request.setContextPath("/context");

        assertThat(RequestPathResolver.resolve(request)).isEqualTo("/orders");
    }

    @Test
    void removesContextPathWhenServletPathIsUnavailable() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/context/orders");
        request.setContextPath("/context");

        assertThat(RequestPathResolver.resolve(request)).isEqualTo("/orders");
    }
}
