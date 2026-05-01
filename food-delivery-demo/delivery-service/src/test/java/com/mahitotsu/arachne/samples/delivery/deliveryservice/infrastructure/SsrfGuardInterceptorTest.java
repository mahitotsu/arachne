package com.mahitotsu.arachne.samples.delivery.deliveryservice.infrastructure;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.HttpClientErrorException;

class SsrfGuardInterceptorTest {

    private SsrfGuardInterceptor interceptor;
    private ClientHttpRequestExecution execution;
    private ClientHttpResponse dummyResponse;

    @BeforeEach
    void setUp() throws IOException {
        EtaServiceUrlAllowlist allowlist = url -> url.startsWith("http://hermes-adapter:8080")
                || url.startsWith("http://idaten-adapter:8080");
        interceptor = new SsrfGuardInterceptor(allowlist);
        dummyResponse = mock(ClientHttpResponse.class);
        execution = mock(ClientHttpRequestExecution.class);
        when(execution.execute(any(), any())).thenReturn(dummyResponse);
    }

    @Test
    void allowsUrlMatchingRegisteredEndpoint() throws IOException {
        HttpRequest request = requestFor("http://hermes-adapter:8080/adapter/eta");

        ClientHttpResponse response = interceptor.intercept(request, new byte[0], execution);

        assertThat(response).isSameAs(dummyResponse);
        verify(execution).execute(any(), any());
    }

    @Test
    void blocksUrlNotInAllowlist() throws Exception {
        HttpRequest request = requestFor("http://169.254.169.254/latest/meta-data");

        assertThatThrownBy(() -> interceptor.intercept(request, new byte[0], execution))
                .isInstanceOf(HttpClientErrorException.class)
                .satisfies(ex -> assertThat(((HttpClientErrorException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.FORBIDDEN));

        verify(execution, never()).execute(any(), any());
    }

    @Test
    void blocksArbitraryInternalServiceUrl() throws Exception {
        HttpRequest request = requestFor("http://order-service:8080/internal/order");

        assertThatThrownBy(() -> interceptor.intercept(request, new byte[0], execution))
                .isInstanceOf(HttpClientErrorException.class);

        verify(execution, never()).execute(any(), any());
    }

    @Test
    void blocksUrlWithInvalidScheme() throws Exception {
        HttpRequest request = requestFor("http://attacker.internal.example/evil");

        assertThatThrownBy(() -> interceptor.intercept(request, new byte[0], execution))
                .isInstanceOf(HttpClientErrorException.class);

        verify(execution, never()).execute(any(), any());
    }

    private static HttpRequest requestFor(String url) {
        return new HttpRequest() {
            @Override
            public URI getURI() {
                return URI.create(url);
            }

            @Override
            public HttpMethod getMethod() {
                return HttpMethod.POST;
            }

            @Override
            public Map<String, Object> getAttributes() {
                return Map.of();
            }

            @Override
            public org.springframework.http.HttpHeaders getHeaders() {
                return org.springframework.http.HttpHeaders.EMPTY;
            }
        };
    }
}
