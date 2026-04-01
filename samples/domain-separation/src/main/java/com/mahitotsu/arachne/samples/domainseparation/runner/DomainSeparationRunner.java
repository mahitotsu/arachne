package com.mahitotsu.arachne.samples.domainseparation.runner;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.mahitotsu.arachne.samples.domainseparation.domain.AccountOperationType;
import com.mahitotsu.arachne.samples.domainseparation.domain.AccountOperationRequest;
import com.mahitotsu.arachne.samples.domainseparation.domain.AccountOperationWorkflowSummary;
import com.mahitotsu.arachne.samples.domainseparation.domain.ApprovalDecision;
import com.mahitotsu.arachne.samples.domainseparation.security.OperatorAuthorizationContext;
import com.mahitotsu.arachne.samples.domainseparation.service.AccountDirectoryService;
import com.mahitotsu.arachne.samples.domainseparation.service.DomainSeparationWorkflowService;

@Component
@ConditionalOnProperty(name = "sample.domain-separation.runner.enabled", havingValue = "true", matchIfMissing = true)
public class DomainSeparationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DomainSeparationRunner.class);

    private final DomainSeparationWorkflowService workflowService;
    private final AccountDirectoryService accountDirectoryService;

    public DomainSeparationRunner(
            DomainSeparationWorkflowService workflowService,
            AccountDirectoryService accountDirectoryService) {
        this.workflowService = workflowService;
        this.accountDirectoryService = accountDirectoryService;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<AccountOperationType> supportedOperations = List.of(AccountOperationType.values());
        String workflowId = "account-unlock-approval-001";
        AccountOperationRequest request = new AccountOperationRequest(
                AccountOperationType.ACCOUNT_UNLOCK,
                "acct-007",
                "operator-7");
        String reason = "Manual review completed";
        OperatorAuthorizationContext operatorAuthorization = new OperatorAuthorizationContext(
                "operator-7",
                "demo-token",
                List.of("account:unlock"));

        accountDirectoryService.resetDemoState();

        log.info("Arachne domain separation sample");
        log.info("phase> approval-backed session workflow");
        log.info("supported.operations> {}", supportedOperations);
        log.info("workflow.id> {}", workflowId);

        AccountOperationWorkflowSummary pending = workflowService.startWorkflow(workflowId, request, reason, operatorAuthorization);
        log.info("initial.status> {}", pending.status());
        log.info("initial.approval.status> {}", pending.approval().status());
        log.info("summary.operationType> {}", pending.operationType());
        log.info("summary.accountId> {}", pending.accountId());
        log.info("summary.preparation.status> {}", pending.preparation().preparedStatus());
        log.info("session.restored.messages.beforeResume> {}", workflowService.restoredMessageCount(workflowId));

        AccountOperationWorkflowSummary completed = workflowService.resumeWorkflow(
                workflowId,
                new ApprovalDecision(true, "operator-approver-2", "Approved after reviewing lock history"),
                operatorAuthorization);

        log.info("final.status> {}", completed.status());
        log.info("final.approval.status> {}", completed.approval().status());
        log.info("summary.operationType> {}", completed.operationType());
        log.info("summary.accountId> {}", completed.accountId());
        log.info("summary.preparation.status> {}", completed.preparation().preparedStatus());
        log.info("summary.execution.outcome> {}", completed.execution().outcome());
        log.info("summary.execution.authorizedOperator> {}", completed.execution().authorizedOperatorId());
    }
}