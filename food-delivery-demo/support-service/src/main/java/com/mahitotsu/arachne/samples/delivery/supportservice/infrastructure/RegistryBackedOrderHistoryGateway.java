package com.mahitotsu.arachne.samples.delivery.supportservice.infrastructure;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.mahitotsu.arachne.samples.delivery.supportservice.domain.CustomerOrderHistoryEntry;

@Component
public class RegistryBackedOrderHistoryGateway implements OrderHistoryGateway {

    private final RestClient restClient;
    private final ServiceEndpointResolver endpointResolver;
    private final String orderServiceName;
    private final String fallbackOrderServiceBaseUrl;

    RegistryBackedOrderHistoryGateway(
            RestClient.Builder restClientBuilder,
            ServiceEndpointResolver endpointResolver,
            @Value("${ORDER_SERVICE_NAME:order-service}") String orderServiceName,
            @Value("${ORDER_SERVICE_BASE_URL:}") String orderServiceBaseUrl) {
        this.restClient = restClientBuilder.build();
        this.endpointResolver = endpointResolver;
        this.orderServiceName = orderServiceName;
        this.fallbackOrderServiceBaseUrl = orderServiceBaseUrl;
    }

    @Override
    public List<CustomerOrderHistoryEntry> recentOrders(String accessToken) {
        String orderHistoryUrl = endpointResolver.resolveUrl(orderServiceName, fallbackOrderServiceBaseUrl, "/api/orders/history");
        if (!StringUtils.hasText(orderHistoryUrl) || !StringUtils.hasText(accessToken)) {
            return List.of();
        }
        try {
            CustomerOrderHistoryEntry[] response = restClient.get()
                    .uri(orderHistoryUrl)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(CustomerOrderHistoryEntry[].class);
            return response == null ? List.of() : List.of(response);
        } catch (Exception ignored) {
            return List.of();
        }
    }
}