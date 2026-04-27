package com.mahitotsu.arachne.samples.delivery.orderservice;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class OrderRepository {

    private final JdbcClient jdbcClient;

    OrderRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    String saveConfirmedOrder(
            String customerId,
            List<OrderLineItem> items,
            BigDecimal subtotal,
            BigDecimal total,
            String etaLabel,
            String paymentStatus) {
        String orderId = "ord-" + UUID.randomUUID().toString().substring(0, 8);
        String itemSummary = items.stream()
                .map(item -> item.quantity() + "x " + item.name())
                .reduce((left, right) -> left + ", " + right)
                .orElse("draft pending");
        jdbcClient.sql("""
                insert into delivery_orders (order_id, customer_id, item_summary, subtotal, total, eta_label, payment_status, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """)
                .params(orderId, customerId, itemSummary, subtotal, total, etaLabel, paymentStatus, Timestamp.from(Instant.now()))
                .update();
        return orderId;
    }

    Optional<StoredOrder> findLatestOrderForUser(String customerId) {
        return jdbcClient.sql("""
                select order_id, item_summary, subtotal, total, eta_label, payment_status
                from delivery_orders
                where customer_id = ?
                order by created_at desc
                limit 1
                """)
                .param(customerId)
                .query(this::mapStoredOrder)
                .optional();
    }

    List<StoredOrderSummary> findRecentOrdersForUser(String customerId, int limit) {
        return jdbcClient.sql("""
                select order_id, item_summary, total, eta_label, payment_status, created_at
                from delivery_orders
                where customer_id = ?
                order by created_at desc
                limit ?
                """)
                .params(customerId, limit)
                .query(this::mapStoredOrderSummary)
                .list();
    }

    private StoredOrder mapStoredOrder(ResultSet resultSet, int row) throws SQLException {
        return new StoredOrder(
                resultSet.getString("order_id"),
                resultSet.getString("item_summary"),
                resultSet.getBigDecimal("subtotal"),
                resultSet.getBigDecimal("total"),
                resultSet.getString("eta_label"),
                resultSet.getString("payment_status"));
    }

    private StoredOrderSummary mapStoredOrderSummary(ResultSet resultSet, int row) throws SQLException {
        Timestamp createdAt = resultSet.getTimestamp("created_at");
        return new StoredOrderSummary(
                resultSet.getString("order_id"),
                resultSet.getString("item_summary"),
                resultSet.getBigDecimal("total"),
                resultSet.getString("eta_label"),
                resultSet.getString("payment_status"),
                createdAt == null ? "" : createdAt.toInstant().toString());
    }
}