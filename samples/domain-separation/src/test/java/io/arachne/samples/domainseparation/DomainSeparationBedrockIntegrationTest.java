package io.arachne.samples.domainseparation;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.ActiveProfiles;

import io.arachne.samples.domainseparation.domain.AccountOperationRequest;
import io.arachne.samples.domainseparation.domain.AccountOperationType;
import io.arachne.samples.domainseparation.domain.AccountOperationWorkflowSummary;
import io.arachne.samples.domainseparation.domain.ApprovalDecision;
import io.arachne.samples.domainseparation.security.OperatorAuthorizationContext;
import io.arachne.samples.domainseparation.service.AccountDirectoryService;
import io.arachne.samples.domainseparation.service.DomainSeparationWorkflowService;

@Tag("integration")
@ActiveProfiles("bedrock")
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(properties = {
        "sample.domain-separation.runner.enabled=false",
        "sample.domain-separation.demo-logging.enabled=true"
})
@EnabledIfSystemProperty(named = "arachne.integration.bedrock", matches = "true")
class DomainSeparationBedrockIntegrationTest {

    @Autowired
    private DomainSeparationWorkflowService workflowService;

    @Autowired
    private AccountDirectoryService accountDirectoryService;

        @SuppressWarnings("unused")
    @BeforeEach
    void resetDemoState() {
        accountDirectoryService.resetDemoState();
    }

    @Test
    void bedrockWorkflowActivatesSkillDelegatesAndCompletes(CapturedOutput output) {
        String workflowId = "workflow-bedrock-001";
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

        assertThat(pending.status()).isEqualTo("PENDING_APPROVAL");
        assertThat(pending.preparation()).isNotNull();
        assertThat(pending.preparation().preparedStatus()).isIn("LOCKED", "READY");
        assertThat(output).contains("demo.trace> coordinator requests skill activation: account-unlock");
        assertThat(output).contains("demo.trace> delegating prepare request to operations-executor for ACCOUNT_UNLOCK acct-007");
        assertThat(output).contains("demo.trace> executor runs find_account for acct-007");

        AccountOperationWorkflowSummary completed = workflowService.resumeWorkflow(
                workflowId,
                new ApprovalDecision(true, "approver-2", "Approved after checking audit history"),
                authorization);

        assertThat(completed.status()).isEqualTo("COMPLETED");
        assertThat(completed.execution()).isNotNull();
        assertThat(completed.execution().outcome()).isEqualTo("UNLOCKED");
        assertThat(output).contains("demo.trace> workflow resumed with external approval: approved=true approverId=approver-2");
        assertThat(output).contains("demo.trace> delegating execution request to operations-executor for ACCOUNT_UNLOCK acct-007");
        assertThat(output).contains("demo.trace> executor runs unlock_account for acct-007");
    }
}