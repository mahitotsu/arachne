package com.mahitotsu.arachne.samples.delivery.supportservice;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
class OrderHistorySnapshotStore {

    private final Map<String, List<CustomerOrderHistoryEntry>> snapshots = new LinkedHashMap<>();

    synchronized void cache(String customerId, List<CustomerOrderHistoryEntry> orders) {
        snapshots.put(customerId, List.copyOf(orders));
    }

    synchronized List<CustomerOrderHistoryEntry> get(String customerId) {
        return snapshots.getOrDefault(customerId, List.of());
    }
}