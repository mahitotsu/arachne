package com.mahitotsu.arachne.samples.delivery.orderservice;

import java.util.Optional;

interface OrderSessionStore {

    Optional<OrderSession> load(String sessionId);

    void save(OrderSession session);
}