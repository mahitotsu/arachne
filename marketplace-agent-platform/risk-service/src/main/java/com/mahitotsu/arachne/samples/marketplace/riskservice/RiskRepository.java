package com.mahitotsu.arachne.samples.marketplace.riskservice;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class RiskRepository {

    private final JdbcTemplate jdbcTemplate;

    RiskRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void ensureRiskReview(String caseId, String orderId, String caseType, String disputeSummary, String operatorRole) {
        var count = jdbcTemplate.queryForObject(
                "select count(*) from risk_reviews where case_id = ?",
                Integer.class,
                caseId);
        if (count != null && count > 0) {
            return;
        }
        RiskReviewTemplate template = findReviewTemplate(orderId).orElse(null);
        RiskScenario scenario = template != null
            ? new RiskScenario(
                template.indicatorSummary(),
                template.manualReviewRequired(),
                template.policyFlags(),
                template.summary())
            : scenario(caseType, disputeSummary);
        jdbcTemplate.update(
                "insert into risk_reviews (case_id, order_id, operator_role, indicator_summary, manual_review_required, policy_flags, summary, updated_at) values (?, ?, ?, ?, ?, ?, ?, ?)",
                caseId,
                orderId,
            template != null ? template.operatorRole() : operatorRole,
                scenario.indicatorSummary(),
                scenario.manualReviewRequired(),
                String.join(",", scenario.policyFlags()),
                scenario.summary(),
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

    private Optional<RiskReviewTemplate> findReviewTemplate(String orderId) {
        var records = jdbcTemplate.query(
                "select * from risk_review_templates where order_id = ?",
                (rs, rowNum) -> new RiskReviewTemplate(
                        rs.getString("order_id"),
                        rs.getString("case_type"),
                        rs.getString("operator_role"),
                        rs.getString("indicator_summary"),
                        rs.getBoolean("manual_review_required"),
                        parseFlags(rs.getString("policy_flags")),
                        rs.getString("summary"),
                        offsetDateTime(rs, "updated_at")),
                orderId);
        return records.stream().findFirst();
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

    private RiskScenario scenario(String caseType, String disputeSummary) {
        String normalizedCaseType = normalize(caseType);
        String normalizedSummary = normalize(disputeSummary);
        if (normalizedCaseType.contains("high_risk_settlement_hold")) {
            return new RiskScenario(
                    "Elevated fraud and account-takeover indicators are present for the current order.",
                    true,
                    List.of("HIGH_RISK_SETTLEMENT_HOLD", "ACCOUNT_TAKEOVER_REVIEW", "FINANCE_CONTROL_REVIEW_REQUIRED"),
                    "Risk review identified elevated fraud indicators and requires a settlement hold until controls are cleared.");
        }
        if (normalizedCaseType.contains("delivered_but_damaged")) {
            return new RiskScenario(
                    "No elevated fraud signal detected, but the damage dispute needs seller and inspection evidence.",
                    false,
                    List.of("DAMAGE_EVIDENCE_REQUIRED"),
                    "Risk review found no fraud escalation, but the damage claim still needs corroborating evidence before settlement changes.");
        }
        if (normalizedCaseType.contains("seller_cancellation_after_authorization") || normalizedSummary.contains("cancel")) {
            return new RiskScenario(
                    "No elevated fraud signal detected for the cancelled fulfillment path.",
                    false,
                    List.of("SELLER_OPERATIONS_REVIEW"),
                    "Risk review found no elevated fraud signal and the cancellation pattern aligns with a seller-side fulfillment failure.");
        }
        return new RiskScenario(
                "No elevated fraud signal detected for the current order.",
                true,
                List.of("FINANCE_CONTROL_REVIEW_REQUIRED"),
                "Risk review found no elevated fraud signal but requires finance control confirmation for settlement-changing actions.");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}

record RiskScenario(
        String indicatorSummary,
        boolean manualReviewRequired,
        List<String> policyFlags,
        String summary) {
}

record RiskReviewTemplate(
    String orderId,
    String caseType,
    String operatorRole,
    String indicatorSummary,
    boolean manualReviewRequired,
    List<String> policyFlags,
    String summary,
    OffsetDateTime updatedAt) {
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