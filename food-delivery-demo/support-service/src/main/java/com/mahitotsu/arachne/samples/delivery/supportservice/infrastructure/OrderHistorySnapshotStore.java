package com.mahitotsu.arachne.samples.delivery.supportservice.infrastructure;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.mahitotsu.arachne.samples.delivery.supportservice.domain.CustomerOrderHistoryEntry;

@Component
public class OrderHistorySnapshotStore {

    private final Map<String, List<CustomerOrderHistoryEntry>> snapshots = new LinkedHashMap<>();

    public synchronized void cache(String customerId, List<CustomerOrderHistoryEntry> orders) {
        snapshots.put(customerId, List.copyOf(orders));
    }

    public synchronized List<CustomerOrderHistoryEntry> get(String customerId) {
        return snapshots.getOrDefault(customerId, List.of());
    }
}