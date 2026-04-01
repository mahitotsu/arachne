package com.mahitotsu.arachne.samples.marketplace.caseservice;

import com.mahitotsu.arachne.samples.marketplace.caseservice.CaseContracts.ActivityEvent;
import com.mahitotsu.arachne.samples.marketplace.caseservice.CaseContracts.AddCaseMessageCommand;
import com.mahitotsu.arachne.samples.marketplace.caseservice.CaseContracts.ApprovalStateView;
import com.mahitotsu.arachne.samples.marketplace.caseservice.CaseContracts.ApprovalSubmissionResult;
import com.mahitotsu.arachne.samples.marketplace.caseservice.CaseContracts.CaseDetailView;
import com.mahitotsu.arachne.samples.marketplace.caseservice.CaseContracts.CaseListItem;
import com.mahitotsu.arachne.samples.marketplace.caseservice.CaseContracts.CaseSummaryView;
import com.mahitotsu.arachne.samples.marketplace.caseservice.CaseContracts.CreateCaseCommand;
import com.mahitotsu.arachne.samples.marketplace.caseservice.CaseContracts.SubmitApprovalCommand;
import com.mahitotsu.arachne.samples.marketplace.caseservice.WorkflowContracts.ContinueWorkflowCommand;
import com.mahitotsu.arachne.samples.marketplace.caseservice.WorkflowContracts.ResumeWorkflowCommand;
import com.mahitotsu.arachne.samples.marketplace.caseservice.WorkflowContracts.StartWorkflowCommand;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
class CaseApplicationService {

    private final CaseProjectionRepository repository;
    private final WorkflowGateway workflowGateway;
    private final CaseActivityStreamRegistry activityStreamRegistry;
    private final Clock clock;

    CaseApplicationService(
            CaseProjectionRepository repository,
            WorkflowGateway workflowGateway,
            CaseActivityStreamRegistry activityStreamRegistry,
            Clock clock) {
        this.repository = repository;
        this.workflowGateway = workflowGateway;
        this.activityStreamRegistry = activityStreamRegistry;
        this.clock = clock;
    }

    @Transactional
    CaseDetailView createCase(CreateCaseCommand command) {
        requireCaseOperator(command.operatorRole());

        var caseId = UUID.randomUUID().toString();
        var now = now();
        var workflowResult = workflowGateway.start(new StartWorkflowCommand(
                caseId,
                command.caseType(),
                command.orderId(),
                command.amount(),
                command.currency(),
                command.initialMessage(),
                command.operatorId(),
                command.operatorRole(),
                now));

        var detail = detailView(
                caseId,
                command.caseType(),
                command.orderId(),
                command.amount(),
                command.currency(),
                workflowResult.workflowStatus(),
                workflowResult.currentRecommendation(),
                workflowResult.evidence(),
                workflowResult.approvalState(),
                workflowResult.outcome(),
                List.of());

        repository.insertCase(detail, now);
        var activities = mapActivities(caseId, workflowResult.activities());
        repository.appendActivities(caseId, activities);
        activityStreamRegistry.publish(caseId, activities);
        return repository.findDetail(caseId).orElseThrow(() -> notFound(caseId));
    }

    List<CaseListItem> listCases(String searchText, String caseType, String caseStatus) {
        return repository.listCases(searchText, caseType, caseStatus);
    }

    CaseDetailView getCase(String caseId) {
        return repository.findDetail(caseId).orElseThrow(() -> notFound(caseId));
    }

    @Transactional
    CaseDetailView addMessage(String caseId, AddCaseMessageCommand command) {
        requireCaseOperator(command.operatorRole());
        var existing = repository.findDetail(caseId).orElseThrow(() -> notFound(caseId));
        var workflowResult = workflowGateway.continueWorkflow(
                new ContinueWorkflowCommand(caseId, command.message(), command.operatorId(), command.operatorRole(), now()));
        var updated = detailView(
                existing.caseId(),
                existing.caseType(),
                existing.orderId(),
                existing.amount(),
                existing.currency(),
                workflowResult.workflowStatus(),
                workflowResult.currentRecommendation(),
                workflowResult.evidence(),
                workflowResult.approvalState(),
                workflowResult.outcome(),
                existing.activityHistory());
        repository.updateCase(updated, now());
        var activities = mapActivities(caseId, workflowResult.activities());
        repository.appendActivities(caseId, activities);
        activityStreamRegistry.publish(caseId, activities);
        return repository.findDetail(caseId).orElseThrow(() -> notFound(caseId));
    }

    @Transactional
    ApprovalSubmissionResult submitApproval(String caseId, SubmitApprovalCommand command) {
        requireFinanceControl(command.actorRole());
        var existing = repository.findDetail(caseId).orElseThrow(() -> notFound(caseId));
        var workflowResult = workflowGateway.resume(
                new ResumeWorkflowCommand(
                        caseId,
                        command.decision(),
                        command.comment(),
                        command.actorId(),
                        command.actorRole(),
                        now()));
        var updated = detailView(
                existing.caseId(),
                existing.caseType(),
                existing.orderId(),
                existing.amount(),
                existing.currency(),
                workflowResult.workflowStatus(),
                workflowResult.currentRecommendation(),
                workflowResult.evidence(),
                workflowResult.approvalState(),
                workflowResult.outcome(),
                existing.activityHistory());
        repository.updateCase(updated, now());
        var activities = mapActivities(caseId, workflowResult.activities());
        repository.appendActivities(caseId, activities);
        activityStreamRegistry.publish(caseId, activities);
        return new ApprovalSubmissionResult(
                caseId,
                workflowResult.approvalState(),
                workflowResult.workflowStatus(),
                true,
                workflowResult.message());
    }

    private CaseDetailView detailView(
            String caseId,
            String caseType,
            String orderId,
            java.math.BigDecimal amount,
            String currency,
            CaseContracts.CaseStatus status,
            CaseContracts.Recommendation recommendation,
            CaseContracts.EvidenceView evidence,
            ApprovalStateView approvalState,
            CaseContracts.OutcomeView outcome,
            List<ActivityEvent> history) {
        var transactionId = "txn-" + caseId.substring(0, 8);
        var summary = new CaseSummaryView(caseId, caseType, status, orderId, transactionId, amount, currency, recommendation);
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
                history,
                approvalState,
                outcome);
    }

    private List<ActivityEvent> mapActivities(String caseId, List<WorkflowContracts.WorkflowActivity> activities) {
        return activities.stream()
                .map(activity -> new ActivityEvent(
                        UUID.randomUUID().toString(),
                        caseId,
                        activity.timestamp(),
                        activity.kind(),
                        activity.source(),
                        activity.message(),
                        activity.structuredPayload()))
                .toList();
    }

    private void requireCaseOperator(String operatorRole) {
        if (!"CASE_OPERATOR".equalsIgnoreCase(operatorRole) && !"FINANCE_CONTROL".equalsIgnoreCase(operatorRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "CASE_OPERATOR role required");
        }
    }

    private void requireFinanceControl(String actorRole) {
        if (!"FINANCE_CONTROL".equalsIgnoreCase(actorRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "FINANCE_CONTROL role required");
        }
    }

    private ResponseStatusException notFound(String caseId) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Case not found: " + caseId);
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock);
    }
}