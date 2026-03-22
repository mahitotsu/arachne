package io.arachne.samples.approvalworkflow;

import java.util.Map;

import org.springframework.stereotype.Component;

import io.arachne.strands.hooks.HookProvider;
import io.arachne.strands.hooks.HookRegistrar;
import io.arachne.strands.spring.ArachneHook;

@Component
@ArachneHook
public class ApprovalGateHook implements HookProvider {

    @Override
    public void registerHooks(HookRegistrar registrar) {
        registrar.beforeToolCall(event -> {
            event.state().put("approvalRequested", Boolean.TRUE);
            event.interrupt(
                    "operatorApproval",
                    Map.of(
                            "message", "Operator approval required before approvalTool can run.",
                            "toolName", event.toolName(),
                            "input", event.input()));
        });
    }
}