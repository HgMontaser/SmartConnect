package com.smartconnect.commons.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unknownPathIsNotFound() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/demo/hello");
        ResponseEntity<ErrorResponse> response = handler.handleGeneric(
                new NoResourceFoundException(HttpMethod.GET, "api/demo/hello"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().status()).isEqualTo(404);
        assertThat(response.getBody().path()).isEqualTo("/api/demo/hello");
    }

    @Test
    void wrongMethodIsMethodNotAllowed() {
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/users");
        ResponseEntity<ErrorResponse> response = handler.handleGeneric(
                new HttpRequestMethodNotSupportedException("DELETE"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(405);
    }

    @Test
    void anythingElseIsStillAServerError() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users");
        ResponseEntity<ErrorResponse> response = handler.handleGeneric(new IllegalStateException("boom"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().message()).isEqualTo("Unexpected error");
    }
}
