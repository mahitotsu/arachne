package com.mahitotsu.arachne.samples.securedownstreamtools;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CustomerProfileClient {

    private final RestClient restClient;

    public CustomerProfileClient(DownstreamProfileStubServer stubServer) {
        this.restClient = RestClient.builder()
                .baseUrl(stubServer.baseUrl())
                .build();
    }

    public CustomerProfileSummary fetchProfile(String customerId, String bearerToken) {
        CustomerProfilePayload payload = restClient.get()
                .uri("/profiles/{customerId}", customerId)
                .headers(headers -> headers.setBearerAuth(bearerToken))
                .retrieve()
                .body(CustomerProfilePayload.class);
        if (payload == null) {
            throw new IllegalStateException("Downstream profile payload must not be null");
        }
        return new CustomerProfileSummary(
                payload.customerId(),
                payload.displayName(),
                payload.status(),
                payload.accountFlags());
    }
}