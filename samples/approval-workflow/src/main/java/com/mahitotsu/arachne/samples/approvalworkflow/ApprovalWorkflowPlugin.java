package com.mahitotsu.arachne.samples.approvalworkflow;

import java.util.List;

import com.mahitotsu.arachne.strands.hooks.HookRegistrar;
import com.mahitotsu.arachne.strands.hooks.Plugin;
import com.mahitotsu.arachne.strands.model.ToolSpec;
import com.mahitotsu.arachne.strands.tool.Tool;
import com.mahitotsu.arachne.strands.tool.ToolResult;

public class ApprovalWorkflowPlugin implements Plugin {

    @Override
    public void registerHooks(HookRegistrar registrar) {
        registrar.beforeInvocation(event -> event.state().put("workflow", "approval-workflow-demo"));
    }

    @Override
    public List<Tool> tools() {
        return List.of(new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec("approvalTool", "Accepts an approval payload for a travel request", null);
            }

            @Override
            public ToolResult invoke(Object input) {
                return ToolResult.success("approval-tool-1", input);
            }
        });
    }
}