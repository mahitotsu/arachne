package com.mahitotsu.arachne.samples.delivery.supportservice.infrastructure;

import java.util.List;

import com.mahitotsu.arachne.samples.delivery.supportservice.domain.CustomerOrderHistoryEntry;

public interface OrderHistoryGateway {

    List<CustomerOrderHistoryEntry> recentOrders(String accessToken);
}