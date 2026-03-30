package io.arachne.samples.marketplace.caseservice;

import io.arachne.samples.marketplace.caseservice.CaseContracts.ActivityEvent;
import io.arachne.samples.marketplace.caseservice.CaseContracts.ApprovalStateView;
import io.arachne.samples.marketplace.caseservice.CaseContracts.ApprovalStatus;
import io.arachne.samples.marketplace.caseservice.CaseContracts.CaseDetailView;
import io.arachne.samples.marketplace.caseservice.CaseContracts.CaseListItem;
import io.arachne.samples.marketplace.caseservice.CaseContracts.CaseStatus;
import io.arachne.samples.marketplace.caseservice.CaseContracts.CaseSummaryView;
import io.arachne.samples.marketplace.caseservice.CaseContracts.EvidenceView;
import io.arachne.samples.marketplace.caseservice.CaseContracts.OutcomeType;
import io.arachne.samples.marketplace.caseservice.CaseContracts.OutcomeView;
import io.arachne.samples.marketplace.caseservice.CaseContracts.Recommendation;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class CaseProjectionRepository {

    private final JdbcTemplate jdbcTemplate;

    CaseProjectionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void insertCase(CaseDetailView detail, OffsetDateTime now) {
        jdbcTemplate.update(
                """
                insert into cases (
                    case_id, case_type, case_status, order_id, transaction_id, amount, currency,
                    current_recommendation, approval_required, approval_status, requested_role,
                    requested_at, decision_at, decision_by, approval_comment, outcome_type,
                    outcome_status, settled_at, settlement_reference, outcome_summary,
                    shipment_evidence, escrow_evidence, risk_evidence, policy_reference,
                    created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                detail.caseId(),
                detail.caseType(),
                detail.caseStatus().name(),
                detail.orderId(),
                detail.transactionId(),
                detail.amount(),
                detail.currency(),
                detail.currentRecommendation().name(),
                detail.approvalState().approvalRequired(),
                detail.approvalState().approvalStatus().name(),
                detail.approvalState().requestedRole(),
                timestamp(detail.approvalState().requestedAt()),
                timestamp(detail.approvalState().decisionAt()),
                detail.approvalState().decisionBy(),
                detail.approvalState().comment(),
                detail.outcome() != null ? detail.outcome().outcomeType().name() : null,
                detail.outcome() != null ? detail.outcome().outcomeStatus() : null,
                timestamp(detail.outcome() != null ? detail.outcome().settledAt() : null),
                detail.outcome() != null ? detail.outcome().settlementReference() : null,
                detail.outcome() != null ? detail.outcome().summary() : null,
                detail.evidence().shipmentEvidence(),
                detail.evidence().escrowEvidence(),
                detail.evidence().riskEvidence(),
                detail.evidence().policyReference(),
                timestamp(now),
                timestamp(now));
    }

    void updateCase(CaseDetailView detail, OffsetDateTime now) {
        jdbcTemplate.update(
                """
                update cases
                set case_status = ?,
                    current_recommendation = ?,
                    approval_required = ?,
                    approval_status = ?,
                    requested_role = ?,
                    requested_at = ?,
                    decision_at = ?,
                    decision_by = ?,
                    approval_comment = ?,
                    outcome_type = ?,
                    outcome_status = ?,
                    settled_at = ?,
                    settlement_reference = ?,
                    outcome_summary = ?,
                    shipment_evidence = ?,
                    escrow_evidence = ?,
                    risk_evidence = ?,
                    policy_reference = ?,
                    updated_at = ?
                where case_id = ?
                """,
                detail.caseStatus().name(),
                detail.currentRecommendation().name(),
                detail.approvalState().approvalRequired(),
                detail.approvalState().approvalStatus().name(),
                detail.approvalState().requestedRole(),
                timestamp(detail.approvalState().requestedAt()),
                timestamp(detail.approvalState().decisionAt()),
                detail.approvalState().decisionBy(),
                detail.approvalState().comment(),
                detail.outcome() != null ? detail.outcome().outcomeType().name() : null,
                detail.outcome() != null ? detail.outcome().outcomeStatus() : null,
                timestamp(detail.outcome() != null ? detail.outcome().settledAt() : null),
                detail.outcome() != null ? detail.outcome().settlementReference() : null,
                detail.outcome() != null ? detail.outcome().summary() : null,
                detail.evidence().shipmentEvidence(),
                detail.evidence().escrowEvidence(),
                detail.evidence().riskEvidence(),
                detail.evidence().policyReference(),
                timestamp(now),
                detail.caseId());
    }

    void appendActivities(String caseId, List<ActivityEvent> events) {
        for (var event : events) {
            jdbcTemplate.update(
                    "insert into case_activity (event_id, case_id, event_timestamp, kind, source, message, structured_payload) values (?, ?, ?, ?, ?, ?, ?)",
                    event.eventId(),
                    caseId,
                    timestamp(event.timestamp()),
                    event.kind(),
                    event.source(),
                    event.message(),
                    event.structuredPayload());
        }
    }

    Optional<CaseDetailView> findDetail(String caseId) {
        var rows = jdbcTemplate.query("select * from cases where case_id = ?", this::mapDetailWithoutHistory, caseId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        var detail = rows.getFirst();
        return Optional.of(withHistory(detail, activityHistory(caseId)));
    }

    boolean exists(String caseId) {
        var count = jdbcTemplate.queryForObject("select count(*) from cases where case_id = ?", Integer.class, caseId);
        return count != null && count > 0;
    }

    List<CaseListItem> listCases(String searchText, String caseType, String caseStatus) {
        return jdbcTemplate.query("select * from cases order by updated_at desc", this::mapListItem).stream()
                .filter(item -> matchesSearch(item, searchText))
                .filter(item -> caseType == null || caseType.isBlank() || item.caseType().equalsIgnoreCase(caseType))
                .filter(item -> caseStatus == null || caseStatus.isBlank() || item.caseStatus().name().equalsIgnoreCase(caseStatus))
                .toList();
    }

    void deleteAll() {
        jdbcTemplate.update("delete from case_activity");
        jdbcTemplate.update("delete from cases");
    }

    private boolean matchesSearch(CaseListItem item, String searchText) {
        if (searchText == null || searchText.isBlank()) {
            return true;
        }
        var normalized = searchText.toLowerCase();
        return item.caseId().toLowerCase().contains(normalized)
                || item.orderId().toLowerCase().contains(normalized)
                || item.caseType().toLowerCase().contains(normalized);
    }

    private List<ActivityEvent> activityHistory(String caseId) {
        return jdbcTemplate.query(
                "select * from case_activity where case_id = ? order by event_timestamp asc",
                (rs, rowNum) -> new ActivityEvent(
                        rs.getString("event_id"),
                        rs.getString("case_id"),
                        offsetDateTime(rs, "event_timestamp"),
                        rs.getString("kind"),
                        rs.getString("source"),
                        rs.getString("message"),
                        rs.getString("structured_payload")),
                caseId);
    }

    private CaseListItem mapListItem(ResultSet rs, int rowNum) throws SQLException {
        return new CaseListItem(
                rs.getString("case_id"),
                rs.getString("case_type"),
                CaseStatus.valueOf(rs.getString("case_status")),
                rs.getString("order_id"),
                rs.getBigDecimal("amount"),
                rs.getString("currency"),
                Recommendation.valueOf(rs.getString("current_recommendation")),
                ApprovalStatus.PENDING_FINANCE_CONTROL.name().equals(rs.getString("approval_status")),
                offsetDateTime(rs, "updated_at"));
    }

    private CaseDetailView mapDetailWithoutHistory(ResultSet rs, int rowNum) throws SQLException {
        var caseId = rs.getString("case_id");
        var caseType = rs.getString("case_type");
        var status = CaseStatus.valueOf(rs.getString("case_status"));
        var orderId = rs.getString("order_id");
        var transactionId = rs.getString("transaction_id");
        var amount = rs.getBigDecimal("amount");
        var currency = rs.getString("currency");
        var recommendation = Recommendation.valueOf(rs.getString("current_recommendation"));
        var summary = new CaseSummaryView(caseId, caseType, status, orderId, transactionId, amount, currency, recommendation);
        var evidence = new EvidenceView(
                rs.getString("shipment_evidence"),
                rs.getString("escrow_evidence"),
                rs.getString("risk_evidence"),
                rs.getString("policy_reference"));
        var approvalState = new ApprovalStateView(
                rs.getBoolean("approval_required"),
                ApprovalStatus.valueOf(rs.getString("approval_status")),
                rs.getString("requested_role"),
                offsetDateTime(rs, "requested_at"),
                offsetDateTime(rs, "decision_at"),
                rs.getString("decision_by"),
                rs.getString("approval_comment"));
        var outcomeType = rs.getString("outcome_type");
        var outcome = outcomeType == null
                ? null
                : new OutcomeView(
                        OutcomeType.valueOf(outcomeType),
                        rs.getString("outcome_status"),
                        offsetDateTime(rs, "settled_at"),
                        rs.getString("settlement_reference"),
                        rs.getString("outcome_summary"));
        return new CaseDetailView(
                caseId,
                caseType,
                status,
                orderId,
                transactionId,
                amount,
                currency,
                recommendation,
                summary,
                evidence,
                List.of(),
                approvalState,
                outcome);
    }

    private CaseDetailView withHistory(CaseDetailView detail, List<ActivityEvent> history) {
        return new CaseDetailView(
                detail.caseId(),
                detail.caseType(),
                detail.caseStatus(),
                detail.orderId(),
                detail.transactionId(),
                detail.amount(),
                detail.currency(),
                detail.currentRecommendation(),
                detail.caseSummary(),
                detail.evidence(),
                history,
                detail.approvalState(),
                detail.outcome());
    }

    private Timestamp timestamp(OffsetDateTime value) {
        return value == null ? null : Timestamp.from(value.toInstant());
    }

    private OffsetDateTime offsetDateTime(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant().atOffset(ZoneOffset.UTC);
    }
}