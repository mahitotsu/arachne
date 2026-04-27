package com.mahitotsu.arachne.samples.delivery.orderservice.infrastructure;

import static com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.*;

import java.util.Optional;

public interface OrderSessionStore {

    Optional<OrderSession> load(String sessionId);

    void save(OrderSession session);
}