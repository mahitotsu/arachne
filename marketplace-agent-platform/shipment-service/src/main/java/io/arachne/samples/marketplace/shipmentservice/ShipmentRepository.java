package io.arachne.samples.marketplace.shipmentservice;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class ShipmentRepository {

    private final JdbcTemplate jdbcTemplate;

    ShipmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void ensureShipmentRecord(String caseId, String orderId) {
        var count = jdbcTemplate.queryForObject(
                "select count(*) from shipment_cases where case_id = ?",
                Integer.class,
                caseId);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update(
                "insert into shipment_cases (case_id, order_id, tracking_number, milestone_summary, delivery_confidence, shipping_exception_summary, updated_at) values (?, ?, ?, ?, ?, ?, ?)",
                caseId,
                orderId,
                "TRACK-" + orderId,
                "Carrier tracking shows label creation and in-transit milestones but no final delivery scan.",
                "LOW",
                "Shipment remains in a not-delivered state for the current case.",
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