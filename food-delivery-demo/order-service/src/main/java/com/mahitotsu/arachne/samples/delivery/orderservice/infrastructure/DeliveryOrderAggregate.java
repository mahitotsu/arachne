package com.mahitotsu.arachne.samples.delivery.orderservice.infrastructure;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Table("delivery_orders")
public class DeliveryOrderAggregate implements Persistable<String> {

    @Id
    @Column("order_id")
    private final String orderId;

    @Column("customer_id")
    private final String customerId;

    @Column("item_summary")
    private final String itemSummary;

    @Column("subtotal")
    private final BigDecimal subtotal;

    @Column("total")
    private final BigDecimal total;

    @Column("eta_label")
    private final String etaLabel;

    @Column("payment_status")
    private final String paymentStatus;

    @Column("created_at")
    private final Timestamp createdAt;

    public DeliveryOrderAggregate(
            String orderId,
            String customerId,
            String itemSummary,
            BigDecimal subtotal,
            BigDecimal total,
            String etaLabel,
            String paymentStatus,
            Timestamp createdAt) {
        this.orderId = orderId;
        this.customerId = customerId;
        this.itemSummary = itemSummary;
        this.subtotal = subtotal;
        this.total = total;
        this.etaLabel = etaLabel;
        this.paymentStatus = paymentStatus;
        this.createdAt = createdAt;
    }

    static DeliveryOrderAggregate createConfirmed(
            String customerId,
            String itemSummary,
            BigDecimal subtotal,
            BigDecimal total,
            String etaLabel,
            String paymentStatus) {
        return new DeliveryOrderAggregate(
                "ord-" + UUID.randomUUID().toString().substring(0, 8),
                customerId,
                itemSummary,
                subtotal,
                total,
                etaLabel,
                paymentStatus,
                Timestamp.from(Instant.now()));
    }

    public String orderId() {
        return orderId;
    }

    @Override
    public String getId() {
        return orderId;
    }

    @Override
    public boolean isNew() {
        return true;
    }

    public String customerId() {
        return customerId;
    }

    public String itemSummary() {
        return itemSummary;
    }

    public BigDecimal subtotal() {
        return subtotal;
    }

    public BigDecimal total() {
        return total;
    }

    public String etaLabel() {
        return etaLabel;
    }

    public String paymentStatus() {
        return paymentStatus;
    }

    public Timestamp createdAt() {
        return createdAt;
    }
}