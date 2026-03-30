package io.arachne.samples.marketplace.riskservice;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class RiskRepository {

    private final JdbcTemplate jdbcTemplate;

    RiskRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void ensureRiskReview(String caseId, String orderId, String operatorRole) {
        var count = jdbcTemplate.queryForObject(
                "select count(*) from risk_reviews where case_id = ?",
                Integer.class,
                caseId);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update(
                "insert into risk_reviews (case_id, order_id, operator_role, indicator_summary, manual_review_required, policy_flags, summary, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?)",
                caseId,
                orderId,
                operatorRole,
                "No elevated fraud signal detected for the current order.",
                true,
                "FINANCE_CONTROL_REVIEW_REQUIRED",
                "Risk review found no elevated fraud signal but requires finance control confirmation for settlement-changing actions.",
                timestamp(OffsetDateTime.now(ZoneOffset.UTC)));
    }

    Optional<RiskReviewRecord> findRiskReview(String caseId) {
        var records = jdbcTemplate.query(
                "select * from risk_reviews where case_id = ?",
                (rs, rowNum) -> mapRiskReview(rs),
                caseId);
        return records.stream().findFirst();
    }

    void deleteAll() {
        jdbcTemplate.update("delete from risk_reviews");
    }

    private RiskReviewRecord mapRiskReview(ResultSet rs) throws SQLException {
        return new RiskReviewRecord(
                rs.getString("case_id"),
                rs.getString("order_id"),
                rs.getString("operator_role"),
                rs.getString("indicator_summary"),
                rs.getBoolean("manual_review_required"),
                parseFlags(rs.getString("policy_flags")),
                rs.getString("summary"),
                offsetDateTime(rs, "updated_at"));
    }

    private List<String> parseFlags(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(flag -> !flag.isEmpty())
                .toList();
    }

    private Timestamp timestamp(OffsetDateTime value) {
        return Timestamp.from(value.toInstant());
    }

    private OffsetDateTime offsetDateTime(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant().atOffset(ZoneOffset.UTC);
    }
}

record RiskReviewRecord(
        String caseId,
        String orderId,
        String operatorRole,
        String indicatorSummary,
        boolean manualReviewRequired,
        List<String> policyFlags,
        String summary,
        OffsetDateTime updatedAt) {
}