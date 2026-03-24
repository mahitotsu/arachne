package io.arachne.samples.domainseparation;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import io.arachne.samples.domainseparation.domain.AccountOperationRequest;
import io.arachne.samples.domainseparation.domain.AccountOperationType;
import io.arachne.samples.domainseparation.domain.ApprovalDecision;
import io.arachne.samples.domainseparation.security.OperatorAuthorizationContext;
import io.arachne.samples.domainseparation.service.AccountDirectoryService;
import io.arachne.samples.domainseparation.service.DomainSeparationWorkflowService;

@SpringBootTest(properties = {
        "sample.domain-separation.runner.enabled=false",
        "sample.domain-separation.demo-logging.verbose-executor=true"
})
@ExtendWith(OutputCaptureExtension.class)
class DomainSeparationVerboseExecutorLoggingIntegrationTest {

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
    void workflowCanEmitVerboseExecutorRequestAndResponseLogs(CapturedOutput output) {
        String workflowId = "workflow-verbose-logging-001";
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

        assertThat(output).contains("executor.llm.trace> request begin");
        assertThat(output).contains("executor.llm.trace> system-prompt> You are the operations executor.");
        assertThat(output).contains("executor.llm.trace> prompt | mode=prepare");
        assertThat(output).contains("executor.llm.trace> prompt | mode=execute");
        assertThat(output).contains("executor.llm.trace> tools> find_account");
        assertThat(output).contains("executor.llm.trace> tools> structured_output");
        assertThat(output).contains("executor.llm.trace> last-tool-result>");
        assertThat(output).contains("executor.llm.trace> stop-reason>");
        assertThat(output).contains("executor.llm.trace> response end");
    }
}