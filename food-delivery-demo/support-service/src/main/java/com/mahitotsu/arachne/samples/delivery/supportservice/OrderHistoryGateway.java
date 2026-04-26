package com.mahitotsu.arachne.samples.delivery.supportservice;

import java.util.List;

interface OrderHistoryGateway {

    List<CustomerOrderHistoryEntry> recentOrders(String accessToken);
}