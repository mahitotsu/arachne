package com.mahitotsu.arachne.samples.marketplace.caseservice;

import com.mahitotsu.arachne.samples.marketplace.caseservice.CaseContracts.ActivityEvent;
import com.mahitotsu.arachne.samples.marketplace.caseservice.CaseContracts.ApprovalStateView;
import com.mahitotsu.arachne.samples.marketplace.caseservice.CaseContracts.ApprovalStatus;
import com.mahitotsu.arachne.samples.marketplace.caseservice.CaseContracts.CaseDetailView;
import com.mahitotsu.arachne.samples.marketplace.caseservice.CaseContracts.CaseListItem;
import com.mahitotsu.arachne.samples.marketplace.caseservice.CaseContracts.CaseStatus;
import com.mahitotsu.arachne.samples.marketplace.caseservice.CaseContracts.CaseSummaryView;
import com.mahitotsu.arachne.samples.marketplace.caseservice.CaseContracts.EvidenceView;
import com.mahitotsu.arachne.samples.marketplace.caseservice.CaseContracts.OutcomeType;
import com.mahitotsu.arachne.samples.marketplace.caseservice.CaseContracts.OutcomeView;
import com.mahitotsu.arachne.samples.marketplace.caseservice.CaseContracts.Recommendation;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
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
        return jdbcTemplate.query("select * from cases order by updated_at desc, case_id asc", this::mapListItem).stream()
                .filter(item -> caseType == null || caseType.isBlank() || item.caseType().equalsIgnoreCase(caseType))
                .filter(item -> caseStatus == null || caseStatus.isBlank() || item.caseStatus().name().equalsIgnoreCase(caseStatus))
            .map(item -> new RankedCase(item, searchScore(item, searchText)))
            .filter(RankedCase::matches)
            .sorted(Comparator.comparingInt(RankedCase::score).reversed()
                .thenComparing((RankedCase ranked) -> ranked.item().updatedAt(), Comparator.reverseOrder())
                .thenComparing(ranked -> ranked.item().caseId()))
            .map(RankedCase::item)
                .toList();
    }

    void deleteAll() {
        jdbcTemplate.update("delete from case_activity");
        jdbcTemplate.update("delete from cases");
    }

    private int searchScore(CaseListItem item, String searchText) {
        if (searchText == null || searchText.isBlank()) {
            return 0;
        }

        var normalizedQuery = normalize(searchText);
        var tokens = normalizedTokens(searchText);
        if (tokens.isEmpty()) {
            return 0;
        }

        int totalScore = wholeQueryScore(item, normalizedQuery);
        for (var token : tokens) {
            int tokenScore = tokenScore(item, token);
            if (tokenScore == 0) {
                return -1;
            }
            totalScore += tokenScore;
        }

        return totalScore;
    }

    private int wholeQueryScore(CaseListItem item, String normalizedQuery) {
        if (normalizedQuery.isBlank()) {
            return 0;
        }

        if (exactTerms(item).stream().anyMatch(term -> term.equals(normalizedQuery))) {
            return 240;
        }

        if (item.caseId().toLowerCase(Locale.ROOT).startsWith(normalizedQuery)
                || item.orderId().toLowerCase(Locale.ROOT).startsWith(normalizedQuery)) {
            return 180;
        }

        if (searchTerms(item).stream().anyMatch(term -> term.contains(normalizedQuery))) {
            return 80;
        }

        return 0;
    }

    private int tokenScore(CaseListItem item, String token) {
        var exactTerms = exactTerms(item);
        if (exactTerms.stream().anyMatch(term -> term.equals(token))) {
            if (item.caseId().equalsIgnoreCase(token) || item.orderId().equalsIgnoreCase(token)) {
                return 160;
            }
            return 100;
        }

        if (item.caseId().toLowerCase(Locale.ROOT).startsWith(token)
                || item.orderId().toLowerCase(Locale.ROOT).startsWith(token)) {
            return 120;
        }

        if (searchTerms(item).stream().anyMatch(term -> term.contains(token))) {
            return 40;
        }

        return 0;
    }

    private List<String> exactTerms(CaseListItem item) {
        var terms = new ArrayList<String>();
        terms.add(normalize(item.caseId()));
        terms.add(normalize(item.orderId()));
        terms.add(normalize(item.caseType()));
        terms.add(normalize(item.caseStatus().name()));
        terms.add(normalize(item.currentRecommendation().name()));
        terms.add(normalize(item.approvalStatus().name()));
        if (item.requestedRole() != null) {
            terms.add(normalize(item.requestedRole()));
        }
        if (item.outcomeType() != null) {
            terms.add(normalize(item.outcomeType().name()));
        }
        return terms;
    }

    private List<String> searchTerms(CaseListItem item) {
        var terms = new ArrayList<>(exactTerms(item));
        terms.addAll(searchAliases(item));
        return terms;
    }

    private List<String> searchAliases(CaseListItem item) {
        var aliases = new ArrayList<String>();

        if (item.pendingApproval()) {
            aliases.add("pending approval");
            aliases.add("awaiting finance control approval");
            aliases.add("finance control review");
        }

        if (item.approvalStatus() == ApprovalStatus.REJECTED) {
            aliases.add("approval rejected");
            aliases.add("returned to evidence gathering");
        }

        if (item.approvalStatus() == ApprovalStatus.APPROVED) {
            aliases.add("approval approved");
        }

        if (item.outcomeType() == OutcomeType.REFUND_EXECUTED) {
            aliases.add("refund executed");
            aliases.add("refund completed");
        }

        if (item.outcomeType() == OutcomeType.CONTINUED_HOLD_RECORDED) {
            aliases.add("continued hold recorded");
            aliases.add("hold recorded");
        }

        if (item.caseStatus() == CaseStatus.GATHERING_EVIDENCE) {
            aliases.add("needs more evidence");
        }

        return aliases.stream().map(this::normalize).toList();
    }

    private List<String> normalizedTokens(String searchText) {
        return List.of(normalize(searchText).split(" ")).stream()
                .filter(token -> !token.isBlank())
                .toList();
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ').trim().replaceAll("\\s+", " ");
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
        var approvalStatus = ApprovalStatus.valueOf(rs.getString("approval_status"));
        var outcomeType = rs.getString("outcome_type");
        return new CaseListItem(
                rs.getString("case_id"),
                rs.getString("case_type"),
                CaseStatus.valueOf(rs.getString("case_status")),
                rs.getString("order_id"),
                rs.getBigDecimal("amount"),
                rs.getString("currency"),
                Recommendation.valueOf(rs.getString("current_recommendation")),
                approvalStatus,
                rs.getString("requested_role"),
                outcomeType == null ? null : OutcomeType.valueOf(outcomeType),
                ApprovalStatus.PENDING_FINANCE_CONTROL == approvalStatus,
                offsetDateTime(rs, "updated_at"));
    }

    private record RankedCase(CaseListItem item, int score) {
        private boolean matches() {
            return score >= 0;
        }
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