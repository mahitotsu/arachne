package com.mahitotsu.arachne.samples.domainseparation;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.mahitotsu.arachne.samples.domainseparation.domain.AccountOperationRequest;
import com.mahitotsu.arachne.samples.domainseparation.domain.AccountOperationType;
import com.mahitotsu.arachne.samples.domainseparation.domain.AccountOperationWorkflowSummary;
import com.mahitotsu.arachne.samples.domainseparation.domain.ApprovalDecision;
import com.mahitotsu.arachne.samples.domainseparation.security.OperatorAuthorizationContext;
import com.mahitotsu.arachne.samples.domainseparation.service.AccountDirectoryService;
import com.mahitotsu.arachne.samples.domainseparation.service.DomainSeparationWorkflowService;
import io.arachne.strands.agent.Agent;
import io.arachne.strands.skills.Skill;
import io.arachne.strands.spring.AgentFactory;

@SpringBootTest(properties = "sample.domain-separation.runner.enabled=false")
@ExtendWith(OutputCaptureExtension.class)
class DomainSeparationIntegrationTest {

    @Autowired
    private AgentFactory agentFactory;

    @Autowired
    private DomainSeparationWorkflowService workflowService;

    @Autowired
    private AccountDirectoryService accountDirectoryService;

    @Autowired
    @Qualifier("domainSeparationCoordinatorSkills")
    private List<Skill> coordinatorSkills;

        @SuppressWarnings("unused")
        @BeforeEach
        void resetDemoState() {
        accountDirectoryService.resetDemoState();
    }

    @Test
    void workflowPausesAtApprovalBoundaryAndResumesWithSessionState() {
        String workflowId = "workflow-approval-001";
        AccountOperationRequest request = new AccountOperationRequest(
                AccountOperationType.ACCOUNT_UNLOCK,
                "acct-007",
                "operator-7");
        OperatorAuthorizationContext authorization = new OperatorAuthorizationContext(
                "operator-7",
                "test-token",
                List.of("account:unlock"));

        AccountOperationWorkflowSummary pending = workflowService.startWorkflow(
                workflowId,
                request,
                "Manual review completed",
                authorization);

        assertThat(pending.workflowId()).isEqualTo(workflowId);
        assertThat(pending.status()).isEqualTo("PENDING_APPROVAL");
        assertThat(pending.operationType()).isEqualTo(AccountOperationType.ACCOUNT_UNLOCK);
        assertThat(pending.accountId()).isEqualTo("acct-007");
        assertThat(pending.approval().required()).isTrue();
        assertThat(pending.approval().status()).isEqualTo("PENDING");
        assertThat(pending.preparation().phase()).isEqualTo("preparation");
        assertThat(pending.preparation().preparedStatus()).isEqualTo("LOCKED");
        assertThat(pending.execution()).isNull();
        assertThat(workflowService.restoredMessageCount(workflowId)).isGreaterThan(0);

        AccountOperationWorkflowSummary completed = workflowService.resumeWorkflow(
                workflowId,
                new ApprovalDecision(true, "approver-2", "Approved after checking audit history"),
                authorization);

        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(completed.approval().status()).isEqualTo("APPROVED");
        assertThat(completed.approval().approverId()).isEqualTo("approver-2");
        assertThat(completed.execution().phase()).isEqualTo("execution");
        assertThat(completed.execution().outcome()).isEqualTo("UNLOCKED");
        assertThat(completed.execution().authorizedOperatorId()).isEqualTo("operator-7");
    }

    @Test
    void workflowCanBeRejectedByExternalApprovalInput() {
        String workflowId = "workflow-approval-002";
        AccountOperationRequest request = new AccountOperationRequest(
                AccountOperationType.ACCOUNT_UNLOCK,
                "acct-007",
                "operator-7");
        OperatorAuthorizationContext authorization = new OperatorAuthorizationContext(
                "operator-7",
                "test-token",
                List.of("account:unlock"));

        workflowService.startWorkflow(workflowId, request, "Manual review completed", authorization);
        AccountOperationWorkflowSummary rejected = workflowService.resumeWorkflow(
                workflowId,
                new ApprovalDecision(false, "approver-3", "Approval denied for this window"),
                authorization);

        assertThat(rejected.status()).isEqualTo("REJECTED");
        assertThat(rejected.approval().status()).isEqualTo("REJECTED");
        assertThat(rejected.execution()).isNull();
    }

    @Test
    void authorizationFailuresRemainDeterministicSystemOutcomes() {
        AccountOperationWorkflowSummary failed = workflowService.startWorkflow(
                "workflow-auth-001",
                new AccountOperationRequest(AccountOperationType.ACCOUNT_UNLOCK, "acct-007", "operator-9"),
                "Manual review completed",
                new OperatorAuthorizationContext("operator-9", "test-token", List.of("account:read")));

        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.preparation().preparedStatus()).isEqualTo("AUTHORIZATION_FAILED");
        assertThat(failed.execution()).isNull();
    }

    @Test
    void namedAgentsExposeScopedToolSurfaces() {
        Agent coordinator = coordinator();
        Agent executor = agentFactory.builder("operations-executor").build();

        assertThat(coordinator.getTools()).extracting(tool -> tool.spec().name())
                .contains("activate_skill", "read_skill_resource", "prepare_account_operation", "execute_account_operation")
                .doesNotContain("find_account", "unlock_account");

        assertThat(executor.getTools()).extracting(tool -> tool.spec().name())
                .contains("find_account", "unlock_account")
                .doesNotContain("activate_skill", "read_skill_resource", "prepare_account_operation", "execute_account_operation");
    }

    @Test
    void coordinatorRegistersInitialPackagedSkills() {
        assertThat(coordinatorSkills).extracting(Skill::name)
                .containsExactly(
                        "account-creation",
                        "password-reset-support",
                        "account-unlock",
                        "account-deletion");
    }

    @Test
    void workflowEmitsDemoTraceLogsForSkillActivationDelegationAndApproval(CapturedOutput output) {
        String workflowId = "workflow-logging-001";
        AccountOperationRequest request = new AccountOperationRequest(
                AccountOperationType.ACCOUNT_UNLOCK,
                "acct-007",
                "operator-7");
        OperatorAuthorizationContext authorization = new OperatorAuthorizationContext(
                "operator-7",
                "test-token",
                List.of("account:unlock"));

        workflowService.startWorkflow(workflowId, request, "Manual review completed", authorization);
        workflowService.resumeWorkflow(
                workflowId,
                new ApprovalDecision(true, "approver-2", "Approved after checking audit history"),
                authorization);

        assertThat(output).contains("demo.trace> coordinator requests skill activation: account-unlock");
        assertThat(output).contains("demo.trace> skill activated: account-unlock");
        assertThat(output).contains("llm.trace> assistant requested tools: activate_skill");
        assertThat(output).contains("llm.trace> assistant requested tools: prepare_account_operation");
        assertThat(output).contains("demo.trace> coordinator calls prepare_account_operation for ACCOUNT_UNLOCK acct-007");
        assertThat(output).contains("demo.trace> delegating prepare request to operations-executor for ACCOUNT_UNLOCK acct-007");
        assertThat(output).contains("demo.trace> executor prepare prompt begin");
        assertThat(output).contains("demo.trace> executor prompt | mode=prepare");
        assertThat(output).contains("demo.trace> executor prompt | operationType=ACCOUNT_UNLOCK");
        assertThat(output).contains("demo.trace> executor prompt | accountId=acct-007");
        assertThat(output).contains("demo.trace> executor prepare response phase=preparation preparedStatus=LOCKED authorizedOperatorId=operator-7");
        assertThat(output).contains("demo.trace> executor runs find_account for acct-007");
        assertThat(output).contains("system.trace> account directory lookup accountId=acct-007 observedStatus=LOCKED operatorId=operator-7");
        assertThat(output).contains("demo.trace> approval required before execute_account_operation can run; workflow interrupted");
        assertThat(output).contains("demo.trace> workflow resumed with external approval: approved=true approverId=approver-2");
        assertThat(output).contains("llm.trace> assistant requested tools: unlock_account");
        assertThat(output).contains("demo.trace> delegating execution request to operations-executor for ACCOUNT_UNLOCK acct-007");
        assertThat(output).contains("demo.trace> executor execute prompt begin");
        assertThat(output).contains("demo.trace> executor prompt | mode=execute");
        assertThat(output).contains("demo.trace> executor execute response phase=execution outcome=UNLOCKED authorizedOperatorId=operator-7");
        assertThat(output).contains("demo.trace> executor runs unlock_account for acct-007");
        assertThat(output).contains("system.trace> account directory unlock applied accountId=acct-007 fromStatus=LOCKED toStatus=UNLOCKED operatorId=operator-7 reason=Manual review completed");
        assertThat(output).contains("demo.trace> execution returned outcome UNLOCKED");
        assertThat(output).doesNotContain("executor.llm.trace>");
        assertThat(output).contains("llm.trace> assistant text begin");
        assertThat(output).contains("llm.trace> | Workflow completed.");
        assertThat(output).contains("llm.trace> assistant text end");
    }

    private Agent coordinator() {
        return agentFactory.builder("operations-coordinator")
                .skills(coordinatorSkills)
                .build();
    }
}