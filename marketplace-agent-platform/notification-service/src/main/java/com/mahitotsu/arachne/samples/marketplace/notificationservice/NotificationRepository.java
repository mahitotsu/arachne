package com.mahitotsu.arachne.samples.marketplace.notificationservice;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class NotificationRepository {

    private final JdbcTemplate jdbcTemplate;

    NotificationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    NotificationDispatchRecord recordDispatch(
            NotificationContracts.NotificationDispatchCommand command,
            NotificationContracts.NotificationComposition composition) {
        var existing = findBySettlementReference(command.settlementReference());
        if (existing.isPresent()) {
            return existing.orElseThrow();
        }
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbcTemplate.update(
                "insert into notification_dispatch (dispatch_id, case_id, outcome_type, outcome_status, settlement_reference, dispatch_status, delivery_status, participant_channel, operator_channel, participant_summary, operator_summary, summary, created_at) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                command.settlementReference(),
                command.caseId(),
                command.outcomeType(),
                command.outcomeStatus(),
                command.settlementReference(),
                "QUEUED",
                "PENDING_DELIVERY",
                composition.participantChannel(),
                composition.operatorChannel(),
                composition.participantSummary(),
                composition.operatorSummary(),
                composition.summary(),
                timestamp(now));
        return findBySettlementReference(command.settlementReference())
                .orElseThrow(() -> new IllegalStateException("Notification record missing after insert for " + command.settlementReference()));
    }

    Optional<NotificationDispatchRecord> findBySettlementReference(String settlementReference) {
        var records = jdbcTemplate.query(
                "select * from notification_dispatch where settlement_reference = ?",
                (rs, rowNum) -> mapRecord(rs),
                settlementReference);
        return records.stream().findFirst();
    }

    void deleteAll() {
        jdbcTemplate.update("delete from notification_dispatch");
    }

    private NotificationDispatchRecord mapRecord(ResultSet rs) throws SQLException {
        return new NotificationDispatchRecord(
                rs.getString("dispatch_id"),
                rs.getString("case_id"),
                rs.getString("outcome_type"),
                rs.getString("outcome_status"),
                rs.getString("settlement_reference"),
                rs.getString("dispatch_status"),
                rs.getString("delivery_status"),
                rs.getString("participant_channel"),
                rs.getString("operator_channel"),
                rs.getString("participant_summary"),
                rs.getString("operator_summary"),
                rs.getString("summary"),
                offsetDateTime(rs, "created_at"));
    }

    private Timestamp timestamp(OffsetDateTime value) {
        return Timestamp.from(value.toInstant());
    }

    private OffsetDateTime offsetDateTime(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant().atOffset(ZoneOffset.UTC);
    }
}

record NotificationDispatchRecord(
        String dispatchId,
        String caseId,
        String outcomeType,
        String outcomeStatus,
        String settlementReference,
        String dispatchStatus,
        String deliveryStatus,
        String participantChannel,
        String operatorChannel,
        String participantSummary,
        String operatorSummary,
        String summary,
        OffsetDateTime createdAt) {
}