package com.mahitotsu.arachne.samples.marketplace.workflowservice;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mahitotsu.arachne.strands.hooks.HookRegistrar;
import com.mahitotsu.arachne.strands.hooks.Plugin;
import com.mahitotsu.arachne.strands.model.ToolSpec;
import com.mahitotsu.arachne.strands.tool.Tool;
import com.mahitotsu.arachne.strands.tool.ToolResult;

final class MarketplaceFinanceControlApprovalPlugin implements Plugin {

    static final String TOOL_NAME = "finance_control_approval";
    static final String INTERRUPT_NAME = "financeControlApproval";

    @Override
    public void registerHooks(HookRegistrar registrar) {
        registrar.beforeToolCall(event -> {
            if (!TOOL_NAME.equals(event.toolName())) {
                return;
            }
            event.state().put("financeControlApprovalPending", Boolean.TRUE);
            event.interrupt(INTERRUPT_NAME, interruptReason(event.input()));
        });
    }

    @Override
    public List<Tool> tools() {
        return List.of(new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec(TOOL_NAME, "Accepts a finance-control approval decision for settlement progression", null);
            }

            @Override
            public ToolResult invoke(Object input) {
                return ToolResult.success(null, input);
            }
        });
    }

    private Map<String, Object> interruptReason(Object input) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("message", "Finance control approval is required before settlement progression can continue.");
        values.put("requestedRole", "FINANCE_CONTROL");
        if (input instanceof Map<?, ?> map) {
            map.forEach((key, value) -> values.put(String.valueOf(key), value));
        }
        return values;
    }
}