package com.mahitotsu.arachne.samples.marketplace.workflowservice;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mahitotsu.arachne.strands.hooks.Plugin;
import com.mahitotsu.arachne.strands.model.ToolSpec;
import com.mahitotsu.arachne.strands.tool.Tool;
import com.mahitotsu.arachne.strands.tool.ToolInvocationContext;
import com.mahitotsu.arachne.strands.tool.ToolResult;

final class MarketplaceOperatorContextPlugin implements Plugin {

    static final String TOOL_NAME = "operator_authorization_probe";

    private final OperatorAuthorizationContextHolder operatorAuthorizationContextHolder;

    MarketplaceOperatorContextPlugin(OperatorAuthorizationContextHolder operatorAuthorizationContextHolder) {
        this.operatorAuthorizationContextHolder = operatorAuthorizationContextHolder;
    }

    @Override
    public List<Tool> tools() {
        return List.of(new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec(
                        TOOL_NAME,
                        "Reads the operator authorization context visible inside delegated workflow tool execution.",
                        MarketplaceToolSchemas.permissiveObjectSchema());
            }

            @Override
            public ToolResult invoke(Object input) {
                return invoke(input, new ToolInvocationContext(TOOL_NAME, null, input, null));
            }

            @Override
            public ToolResult invoke(Object input, ToolInvocationContext context) {
                OperatorAuthorizationContext current = operatorAuthorizationContextHolder.current();
                if (current == null || blank(current.operatorId()) || blank(current.operatorRole())) {
                    return ToolResult.error(context.toolUseId(),
                            "operator authorization context is not available in delegated tool execution");
                }
                LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
                payload.put("type", "operator_context_probe");
                payload.put("probe", probeName(input));
                payload.put("operatorId", current.operatorId());
                payload.put("operatorRole", current.operatorRole());
                return ToolResult.success(context.toolUseId(), payload);
            }

            private String probeName(Object input) {
                if (input instanceof Map<?, ?> values) {
                    Object probe = values.get("probe");
                    if (probe != null) {
                        return String.valueOf(probe);
                    }
                }
                return "unknown-probe";
            }

            private boolean blank(String value) {
                return value == null || value.isBlank();
            }
        });
    }
}