package com.smartconnect.commons.correlation;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdServletFilterTest {

    private final CorrelationIdServletFilter filter = new CorrelationIdServletFilter();

    @Test
    void generatesIdWhenHeaderAbsentAndClearsMdcAfterRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        String generated = response.getHeader(CorrelationConstants.HEADER_NAME);
        assertThat(generated).isNotBlank();
        assertThat(MDC.get(CorrelationConstants.MDC_KEY)).isNull();
    }

    @Test
    void propagatesIncomingCorrelationId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        request.addHeader(CorrelationConstants.HEADER_NAME, "test-correlation-id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader(CorrelationConstants.HEADER_NAME)).isEqualTo("test-correlation-id");
    }
}
