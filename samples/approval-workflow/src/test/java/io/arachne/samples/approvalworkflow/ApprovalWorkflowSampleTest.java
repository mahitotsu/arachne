package io.arachne.samples.approvalworkflow;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest
class ApprovalWorkflowSampleTest {

    @Autowired
    private LifecycleEventCollector lifecycleEventCollector;

    @Test
    void sampleBootsAndPrintsExpectedInterruptAndResumeFlow(CapturedOutput output) {
        assertThat(output)
                .contains("Arachne approval workflow sample")
                .contains("request> Book a Kyoto trip that needs operator approval.")
                .contains("initial.stopReason> interrupt")
                .contains("interrupt.name> operatorApproval")
                .contains("interrupt.toolName> approvalTool")
                .contains("interrupt.input> {destination=Kyoto, nights=2}")
                .contains("resume.response> {approved=true, operator=demo-operator}")
                .contains("final.stopReason> end_turn")
                .contains("final.reply> Approval recorded for Kyoto: approved=true by demo-operator")
                .contains("state.workflow> approval-workflow-demo")
                .contains("state.approvalRequested> true")
                .contains("lifecycle.events> beforeInvocation");

        assertThat(lifecycleEventCollector.types())
                .contains("beforeInvocation", "beforeModelCall", "messageAdded");
    }
}