package com.mahitotsu.arachne.samples.marketplace.escrowservice;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class EscrowRepository {

    private final JdbcTemplate jdbcTemplate;

    EscrowRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void ensureCaseRecord(String caseId, BigDecimal amount, String currency) {
        var count = jdbcTemplate.queryForObject(
                "select count(*) from escrow_cases where case_id = ?",
                Integer.class,
                caseId);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update(
                "insert into escrow_cases (case_id, hold_state, amount, currency, prior_settlement_status, updated_at) values (?, ?, ?, ?, ?, ?)",
                caseId,
                "HELD",
                amount,
                currency,
                "NO_PRIOR_REFUND",
                timestamp(OffsetDateTime.now(ZoneOffset.UTC)));
    }

    Optional<EscrowRecord> findCaseRecord(String caseId) {
        var records = jdbcTemplate.query(
                "select * from escrow_cases where case_id = ?",
                this::mapEscrowRecord,
                caseId);
        return records.stream().findFirst();
    }

    void recordSettlement(String caseId, String holdState, EscrowContracts.SettlementOutcome outcome) {
        jdbcTemplate.update(
                "update escrow_cases set hold_state = ?, prior_settlement_status = ?, updated_at = ? where case_id = ?",
                holdState,
                outcome.outcomeType(),
                timestamp(outcome.settledAt()),
                caseId);
        jdbcTemplate.update(
                "insert into escrow_settlement_audit (audit_id, case_id, outcome_type, outcome_status, settled_at, settlement_reference, summary) values (?, ?, ?, ?, ?, ?, ?)",
                outcome.settlementReference(),
                caseId,
                outcome.outcomeType(),
                outcome.outcomeStatus(),
                timestamp(outcome.settledAt()),
                outcome.settlementReference(),
                outcome.summary());
    }

    void deleteAll() {
        jdbcTemplate.update("delete from escrow_settlement_audit");
        jdbcTemplate.update("delete from escrow_cases");
    }

    private EscrowRecord mapEscrowRecord(ResultSet rs, int rowNum) throws SQLException {
        return new EscrowRecord(
                rs.getString("case_id"),
                rs.getString("hold_state"),
                rs.getBigDecimal("amount"),
                rs.getString("currency"),
                rs.getString("prior_settlement_status"),
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

record EscrowRecord(
        String caseId,
        String holdState,
        BigDecimal amount,
        String currency,
        String priorSettlementStatus,
        OffsetDateTime updatedAt) {
}