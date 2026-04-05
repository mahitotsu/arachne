package com.mahitotsu.arachne.samples.marketplace.shipmentservice;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class ShipmentRepository {

    private final JdbcTemplate jdbcTemplate;

    ShipmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void ensureShipmentRecord(String caseId, String orderId, String caseType, String disputeSummary) {
        var count = jdbcTemplate.queryForObject(
                "select count(*) from shipment_cases where case_id = ?",
                Integer.class,
                caseId);
        if (count != null && count > 0) {
            return;
        }
        ShipmentScenario scenario = scenario(caseType, orderId, disputeSummary);
        jdbcTemplate.update(
                "insert into shipment_cases (case_id, order_id, tracking_number, milestone_summary, delivery_confidence, shipping_exception_summary, updated_at) values (?, ?, ?, ?, ?, ?, ?)",
                caseId,
                orderId,
                scenario.trackingNumber(),
                scenario.milestoneSummary(),
                scenario.deliveryConfidence(),
                scenario.shippingExceptionSummary(),
                timestamp(OffsetDateTime.now(ZoneOffset.UTC)));
    }

    Optional<ShipmentRecord> findShipmentRecord(String caseId) {
        var records = jdbcTemplate.query(
                "select * from shipment_cases where case_id = ?",
                (rs, rowNum) -> mapShipmentRecord(rs),
                caseId);
        return records.stream().findFirst();
    }

    void deleteAll() {
        jdbcTemplate.update("delete from shipment_cases");
    }

    private ShipmentRecord mapShipmentRecord(ResultSet rs) throws SQLException {
        return new ShipmentRecord(
                rs.getString("case_id"),
                rs.getString("order_id"),
                rs.getString("tracking_number"),
                rs.getString("milestone_summary"),
                rs.getString("delivery_confidence"),
                rs.getString("shipping_exception_summary"),
                offsetDateTime(rs, "updated_at"));
    }

    private Timestamp timestamp(OffsetDateTime value) {
        return Timestamp.from(value.toInstant());
    }

    private OffsetDateTime offsetDateTime(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant().atOffset(ZoneOffset.UTC);
    }

    private ShipmentScenario scenario(String caseType, String orderId, String disputeSummary) {
        String normalizedCaseType = normalize(caseType);
        String trackingNumber = "TRACK-" + orderId;
        if (normalizedCaseType.contains("delivered_but_damaged")) {
            return new ShipmentScenario(
                    trackingNumber,
                    "Carrier tracking shows final delivery on the doorstep with photo proof captured the same day.",
                    "HIGH",
                    "Shipment was delivered, but the package exterior shows impact damage and moisture exposure.");
        }
        if (normalizedCaseType.contains("high_risk_settlement_hold")) {
            return new ShipmentScenario(
                    trackingNumber,
                    "Carrier tracking shows delivery completion, but the handoff location and recipient pattern diverge from the account's normal history.",
                    "MEDIUM",
                    "Shipment completed, but delivery evidence is unusual enough to support a settlement hold pending risk review.");
        }
        if (normalizedCaseType.contains("seller_cancellation_after_authorization") || normalize(disputeSummary).contains("cancel")) {
            return new ShipmentScenario(
                    trackingNumber,
                    "Carrier tracking shows label creation followed by a void event before carrier handoff.",
                    "LOW",
                    "Seller cancelled fulfillment before the package entered the carrier network.");
        }
        return new ShipmentScenario(
                trackingNumber,
                "Carrier tracking shows label creation and in-transit milestones but no final delivery scan.",
                "LOW",
                "Shipment remains in a not-delivered state for the current case.");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}

record ShipmentScenario(
        String trackingNumber,
        String milestoneSummary,
        String deliveryConfidence,
        String shippingExceptionSummary) {
}

record ShipmentRecord(
        String caseId,
        String orderId,
        String trackingNumber,
        String milestoneSummary,
        String deliveryConfidence,
        String shippingExceptionSummary,
        OffsetDateTime updatedAt) {
}