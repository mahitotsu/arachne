package com.mahitotsu.arachne.samples.delivery.supportservice.infrastructure;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.mahitotsu.arachne.samples.delivery.supportservice.config.SupportServiceProperties;
import com.mahitotsu.arachne.samples.delivery.supportservice.domain.CustomerOrderHistoryEntry;

@Component
public class RegistryBackedOrderHistoryGateway implements OrderHistoryGateway {

    private final RestClient restClient;
    private final ServiceEndpointResolver endpointResolver;
    private final DownstreamObservationSupport observationSupport;
    private final String orderServiceName;
    private final String fallbackOrderServiceBaseUrl;

    RegistryBackedOrderHistoryGateway(
            RestClient.Builder restClientBuilder,
            ServiceEndpointResolver endpointResolver,
            DownstreamObservationSupport observationSupport,
            SupportServiceProperties properties) {
        this.restClient = restClientBuilder.build();
        this.endpointResolver = endpointResolver;
        this.observationSupport = observationSupport;
        this.orderServiceName = properties.getDownstream().getOrder().getServiceName();
        this.fallbackOrderServiceBaseUrl = properties.getDownstream().getOrder().getBaseUrl();
    }

    @Override
    public List<CustomerOrderHistoryEntry> recentOrders(String accessToken) {
        String orderHistoryUrl = endpointResolver.resolveUrl(orderServiceName, fallbackOrderServiceBaseUrl, "/api/orders/history");
        if (!StringUtils.hasText(orderHistoryUrl) || !StringUtils.hasText(accessToken)) {
            return List.of();
        }
        try {
            CustomerOrderHistoryEntry[] response = observationSupport.observe(
                    "delivery.support.downstream",
                    orderServiceName,
                    "recent-orders",
                    () -> restClient.get()
                            .uri(orderHistoryUrl)
                            .headers(headers -> headers.setBearerAuth(accessToken))
                            .retrieve()
                            .body(CustomerOrderHistoryEntry[].class));
            return response == null ? List.of() : List.of(response);
        } catch (Exception ignored) {
            return List.of();
        }
    }
}