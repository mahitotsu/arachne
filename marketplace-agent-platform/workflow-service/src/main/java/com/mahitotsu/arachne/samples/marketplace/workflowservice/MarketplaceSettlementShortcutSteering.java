package com.mahitotsu.arachne.samples.marketplace.workflowservice;

import java.util.List;
import java.util.Map;

import com.mahitotsu.arachne.strands.hooks.BeforeToolCallEvent;
import com.mahitotsu.arachne.strands.model.ToolSpec;
import com.mahitotsu.arachne.strands.steering.Guide;
import com.mahitotsu.arachne.strands.steering.Proceed;
import com.mahitotsu.arachne.strands.steering.SteeringHandler;
import com.mahitotsu.arachne.strands.steering.ToolSteeringAction;
import com.mahitotsu.arachne.strands.tool.Tool;
import com.mahitotsu.arachne.strands.tool.ToolResult;

final class MarketplaceSettlementShortcutSteering extends SteeringHandler {

    static final String TOOL_NAME = "settlement_shortcut";
    static final String GUIDANCE = "Automatic settlement is blocked on the workflow path. Redirect the case to finance control approval before any settlement-changing action.";

    @Override
    public List<Tool> tools() {
        return List.of(new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec(
                        TOOL_NAME,
                        "Attempts an unsafe direct settlement shortcut before finance control approval.",
                        null);
            }

            @Override
            public ToolResult invoke(Object input) {
                return ToolResult.success(null, input);
            }
        });
    }

    @Override
    protected ToolSteeringAction steerBeforeTool(BeforeToolCallEvent event) {
        if (!TOOL_NAME.equals(event.toolName())) {
            return new Proceed("allow");
        }
        if (event.input() instanceof Map<?, ?> input
                && "instant_refund".equalsIgnoreCase(String.valueOf(input.get("path")))) {
            return new Guide(GUIDANCE);
        }
        return new Proceed("allow");
    }
}