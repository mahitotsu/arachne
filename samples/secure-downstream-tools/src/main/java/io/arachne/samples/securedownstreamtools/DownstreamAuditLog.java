package io.arachne.samples.securedownstreamtools;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class DownstreamAuditLog {

    private final List<String> requestedCustomerIds = new ArrayList<>();
    private final List<String> observedAuthorizationHeaders = new ArrayList<>();

    public synchronized void record(String customerId, String authorizationHeader) {
        requestedCustomerIds.add(customerId);
        observedAuthorizationHeaders.add(authorizationHeader);
    }

    public synchronized List<String> requestedCustomerIds() {
        return List.copyOf(requestedCustomerIds);
    }

    public synchronized List<String> observedAuthorizationHeaders() {
        return List.copyOf(observedAuthorizationHeaders);
    }

    public synchronized void reset() {
        requestedCustomerIds.clear();
        observedAuthorizationHeaders.clear();
    }
}