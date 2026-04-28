package com.mahitotsu.arachne.samples.delivery.orderservice.infrastructure;

import static com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

@Component
public class OrderRepository {

    private final DeliveryOrderJdbcRepository jdbcRepository;

    OrderRepository(DeliveryOrderJdbcRepository jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    public String saveConfirmedOrder(
            String customerId,
            List<OrderLineItem> items,
            BigDecimal subtotal,
            BigDecimal total,
            String etaLabel,
            String paymentStatus) {
        DeliveryOrderAggregate saved = jdbcRepository.save(DeliveryOrderAggregate.createConfirmed(
                customerId,
                summarizeItems(items),
                subtotal,
                total,
                etaLabel,
                paymentStatus));
        return saved.orderId();
    }

    public Optional<StoredOrder> findLatestOrderForUser(String customerId) {
        return jdbcRepository.findTopByCustomerIdOrderByCreatedAtDesc(customerId)
                .map(this::toStoredOrder);
    }

    public List<StoredOrderSummary> findRecentOrdersForUser(String customerId, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return jdbcRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .limit(limit)
                .map(this::toStoredOrderSummary)
                .toList();
    }

    private String summarizeItems(List<OrderLineItem> items) {
        return items.stream()
                .map(item -> item.quantity() + "x " + item.name())
                .reduce((left, right) -> left + ", " + right)
                .orElse("draft pending");
    }

    private StoredOrder toStoredOrder(DeliveryOrderAggregate aggregate) {
        return new StoredOrder(
                aggregate.orderId(),
                aggregate.itemSummary(),
                aggregate.subtotal(),
                aggregate.total(),
                aggregate.etaLabel(),
                aggregate.paymentStatus());
    }

    private StoredOrderSummary toStoredOrderSummary(DeliveryOrderAggregate aggregate) {
        return new StoredOrderSummary(
                aggregate.orderId(),
                aggregate.itemSummary(),
                aggregate.total(),
                aggregate.etaLabel(),
                aggregate.paymentStatus(),
                aggregate.createdAt() == null ? "" : aggregate.createdAt().toInstant().toString());
    }
}