package com.mahitotsu.arachne.samples.marketplace.workflowservice;

import java.util.function.Consumer;

import com.mahitotsu.arachne.strands.hooks.BeforeModelCallEvent;
import com.mahitotsu.arachne.strands.hooks.HookProvider;
import com.mahitotsu.arachne.strands.model.ToolSelection;
import com.mahitotsu.arachne.strands.model.ToolSpec;
import com.mahitotsu.arachne.strands.types.ContentBlock;
import com.mahitotsu.arachne.strands.types.Message;

final class MarketplaceApprovalInterruptHookProvider implements HookProvider {

    private final Consumer<String> forcedToolRecorder;

    MarketplaceApprovalInterruptHookProvider() {
        this(toolName -> {
        });
    }

    MarketplaceApprovalInterruptHookProvider(Consumer<String> forcedToolRecorder) {
        this.forcedToolRecorder = forcedToolRecorder;
    }

    @Override
    public void registerHooks(com.mahitotsu.arachne.strands.hooks.HookRegistrar registrar) {
        registrar.beforeModelCall(this::apply);
    }

    void apply(BeforeModelCallEvent event) {
        if (approvalDecisionPresent(event.messages())) {
            return;
        }
        if (!hasFinanceApprovalTool(event.toolSpecs())) {
            return;
        }
        forcedToolRecorder.accept(MarketplaceFinanceControlApprovalPlugin.TOOL_NAME);
        event.setToolSelection(ToolSelection.force(MarketplaceFinanceControlApprovalPlugin.TOOL_NAME));
    }

    private boolean approvalDecisionPresent(java.util.List<Message> messages) {
        for (Message message : messages) {
            for (ContentBlock block : message.content()) {
                if (block instanceof ContentBlock.ToolResult toolResult
                        && toolResult.content() instanceof java.util.Map<?, ?> content
                        && content.containsKey("decision")) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasFinanceApprovalTool(java.util.List<ToolSpec> toolSpecs) {
        return toolSpecs.stream().anyMatch(spec -> MarketplaceFinanceControlApprovalPlugin.TOOL_NAME.equals(spec.name()));
    }
}