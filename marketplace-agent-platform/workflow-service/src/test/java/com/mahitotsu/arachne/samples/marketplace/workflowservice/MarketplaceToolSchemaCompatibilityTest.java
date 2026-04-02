package com.mahitotsu.arachne.samples.marketplace.workflowservice;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import com.mahitotsu.arachne.strands.agent.AgentState;
import com.mahitotsu.arachne.strands.hooks.BeforeModelCallEvent;
import com.mahitotsu.arachne.strands.model.ToolSelection;
import com.mahitotsu.arachne.strands.types.ContentBlock;
import com.mahitotsu.arachne.strands.types.Message;

class MarketplaceToolSchemaCompatibilityTest {

    @Test
    void customMarketplaceToolsExposeBedrockCompatibleObjectSchemas() {
        ToolSchemaAssertions.assertObjectSchema(new MarketplaceFinanceControlApprovalPlugin().tools().getFirst().spec());
        ToolSchemaAssertions.assertObjectSchema(new MarketplaceSettlementShortcutSteering().tools().getFirst().spec());
        ToolSchemaAssertions.assertObjectSchema(
                new MarketplaceOperatorContextPlugin(new OperatorAuthorizationContextHolder()).tools().getFirst().spec());
    }

        @Test
        void approvalHookForcesFinanceToolUntilDecisionResponseArrives() {
        MarketplaceApprovalInterruptHookProvider hookProvider = new MarketplaceApprovalInterruptHookProvider();
        var financeToolSpec = new MarketplaceFinanceControlApprovalPlugin().tools().getFirst().spec();
        BeforeModelCallEvent initialEvent = new BeforeModelCallEvent(
            List.of(new Message(Message.Role.USER, List.of(new ContentBlock.Text("mode=approval-start")))),
            List.of(financeToolSpec),
            null,
            null,
            new AgentState());

        hookProvider.apply(initialEvent);

        assertThat(initialEvent.toolSelection()).isEqualTo(ToolSelection.force(MarketplaceFinanceControlApprovalPlugin.TOOL_NAME));

        BeforeModelCallEvent resumedEvent = new BeforeModelCallEvent(
            List.of(new Message(
                Message.Role.USER,
                List.of(new ContentBlock.ToolResult("workflow-approval", Map.of("decision", "APPROVE"), "success")))),
            List.of(financeToolSpec),
            null,
            null,
            new AgentState());

        hookProvider.apply(resumedEvent);

        assertThat(resumedEvent.toolSelection()).isNull();
        }

    private static final class ToolSchemaAssertions {

        private ToolSchemaAssertions() {
        }

        private static void assertObjectSchema(com.mahitotsu.arachne.strands.model.ToolSpec spec) {
            assertThat(spec.inputSchema()).as("input schema for %s", spec.name()).isNotNull();
            assertThat(spec.inputSchema().isObject()).as("input schema for %s", spec.name()).isTrue();
            assertThat(spec.inputSchema().path("type").asText()).isEqualTo("object");
        }
    }
}