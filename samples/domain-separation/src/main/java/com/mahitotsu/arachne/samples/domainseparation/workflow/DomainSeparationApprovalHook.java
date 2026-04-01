package com.mahitotsu.arachne.samples.domainseparation.workflow;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.mahitotsu.arachne.strands.hooks.AfterToolCallEvent;
import com.mahitotsu.arachne.strands.hooks.BeforeInvocationEvent;
import com.mahitotsu.arachne.strands.hooks.BeforeModelCallEvent;
import com.mahitotsu.arachne.strands.hooks.BeforeToolCallEvent;
import com.mahitotsu.arachne.strands.hooks.HookProvider;
import com.mahitotsu.arachne.strands.hooks.HookRegistrar;
import com.mahitotsu.arachne.strands.spring.ArachneHook;
import com.mahitotsu.arachne.strands.types.ContentBlock;
import com.mahitotsu.arachne.strands.types.Message;

@Component
@ArachneHook
public class DomainSeparationApprovalHook implements HookProvider {

    private static final Logger log = LoggerFactory.getLogger(DomainSeparationApprovalHook.class);

    private final ObjectMapper objectMapper;

    public DomainSeparationApprovalHook(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void registerHooks(HookRegistrar registrar) {
        registrar.beforeInvocation(this::captureWorkflowTurn)
                .beforeModelCall(this::captureResumeApproval)
                .beforeToolCall(this::pauseForApproval)
                .afterToolCall(this::rememberToolOutputs);
    }

    private void captureWorkflowTurn(BeforeInvocationEvent event) {
        Map<String, String> prompt = DomainSeparationWorkflowState.parsePrompt(event.prompt());
        if (prompt.isEmpty()) {
            return;
        }

        if (isStart(prompt)) {
            event.state().put(DomainSeparationWorkflowState.REQUEST, Map.of(
                    "operationType", prompt.get("operationType"),
                    "accountId", prompt.get("accountId"),
                    "requestedBy", prompt.get("requestedBy"),
                    "reason", prompt.getOrDefault("reason", "")));
            event.state().put(DomainSeparationWorkflowState.APPROVAL, approvalState("NOT_REQUESTED", null, null, null));
            event.state().put(DomainSeparationWorkflowState.STATUS, "RUNNING");
            return;
        }

        if (isResume(prompt)) {
            applyApprovalDecision(
                    event.state(),
                    Boolean.parseBoolean(prompt.getOrDefault("approvalApproved", "false")),
                    prompt.getOrDefault("approverId", ""),
                    prompt.getOrDefault("approvalComment", ""));
        }
    }

    private void captureResumeApproval(BeforeModelCallEvent event) {
        Message latestMessage = event.messages().isEmpty() ? null : event.messages().getLast();
        if (latestMessage == null || latestMessage.role() != Message.Role.USER) {
            return;
        }

        for (ContentBlock block : latestMessage.content()) {
            if (!(block instanceof ContentBlock.ToolResult toolResult)) {
                continue;
            }
            Map<String, Object> response = mapValue(toolResult.content());
            if (response == null || !response.containsKey("approved")) {
                continue;
            }
            applyApprovalDecision(
                    event.state(),
                    Boolean.parseBoolean(String.valueOf(response.get("approved"))),
                    String.valueOf(response.getOrDefault("approverId", "")),
                    String.valueOf(response.getOrDefault("comment", "")));
            return;
        }
    }

    private void pauseForApproval(BeforeToolCallEvent event) {
        if (!"execute_account_operation".equals(event.toolName())) {
            return;
        }

        Map<String, Object> approval = mapValue(event.state().get(DomainSeparationWorkflowState.APPROVAL));
        if (approval != null && "REJECTED".equals(String.valueOf(approval.get("status")))) {
            event.state().put(DomainSeparationWorkflowState.STATUS, "REJECTED");
            event.guide("Approval rejected. Do not execute the requested account operation.");
            return;
        }
        if (approval != null && Boolean.TRUE.equals(approval.get("approved"))) {
            event.state().put(DomainSeparationWorkflowState.STATUS, "RUNNING");
            return;
        }

        Map<String, Object> request = mapValue(event.state().get(DomainSeparationWorkflowState.REQUEST));
        Map<String, Object> preparation = mapValue(event.state().get(DomainSeparationWorkflowState.PREPARATION));
        event.state().put(DomainSeparationWorkflowState.STATUS, "PENDING_APPROVAL");
        event.state().put(DomainSeparationWorkflowState.APPROVAL, approvalState("PENDING", null, null, null));
        log.info("demo.trace> approval required before execute_account_operation can run; workflow interrupted");
        event.interrupt(
                "operatorApproval",
                Map.of(
                        "message", "Operator approval required before execute_account_operation can run.",
                        "request", request == null ? Map.of() : request,
                        "preparation", preparation == null ? Map.of() : preparation));
    }

    private void rememberToolOutputs(AfterToolCallEvent event) {
        Map<String, Object> content = mapValue(event.result().content());
        if (content == null) {
            return;
        }

        if ("prepare_account_operation".equals(event.toolName())) {
            event.state().put(DomainSeparationWorkflowState.PREPARATION, content);
            String preparedStatus = String.valueOf(content.get("preparedStatus"));
            if (isUnlockReady(preparedStatus)) {
                event.state().put(DomainSeparationWorkflowState.STATUS, "PENDING_APPROVAL");
            } else {
                event.state().put(DomainSeparationWorkflowState.STATUS, "FAILED");
                event.state().put(
                        DomainSeparationWorkflowState.APPROVAL,
                        approvalState(
                                "SKIPPED",
                                null,
                                null,
                                "Execution skipped because preparation did not return a lockable account."));
            }
            return;
        }

        if ("execute_account_operation".equals(event.toolName())) {
            event.state().put(DomainSeparationWorkflowState.EXECUTION, content);
            String outcome = String.valueOf(content.get("outcome"));
            event.state().put(DomainSeparationWorkflowState.STATUS,
                    "UNLOCKED".equals(outcome) ? "COMPLETED" : "FAILED");
        }
    }

    private boolean isUnlockReady(String preparedStatus) {
        return "LOCKED".equals(preparedStatus) || "READY".equals(preparedStatus);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean) {
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, nestedValue) -> copy.put(String.valueOf(key), nestedValue));
            return copy;
        }
        return objectMapper.convertValue(value, Map.class);
    }

    private Map<String, Object> approvalState(String status, Boolean approved, String approverId, String comment) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("required", Boolean.TRUE);
        values.put("status", status);
        values.put("approved", approved);
        values.put("approverId", approverId);
        values.put("comment", comment);
        return values;
    }

    private boolean isStart(Map<String, String> prompt) {
        String mode = prompt.get("mode");
        return "start".equalsIgnoreCase(mode) || "prepare-and-execute".equalsIgnoreCase(mode);
    }

    private boolean isResume(Map<String, String> prompt) {
        return "resume".equalsIgnoreCase(prompt.get("mode"));
    }

    private void applyApprovalDecision(
            com.mahitotsu.arachne.strands.agent.AgentState state,
            boolean approved,
            String approverId,
            String comment) {
        log.info(
                "demo.trace> workflow resumed with external approval: approved={} approverId={}",
                approved,
                approverId);
        state.put(
                DomainSeparationWorkflowState.APPROVAL,
                approvalState(approved ? "APPROVED" : "REJECTED", approved, approverId, comment));
        state.put(DomainSeparationWorkflowState.STATUS, approved ? "RUNNING" : "REJECTED");
    }
}