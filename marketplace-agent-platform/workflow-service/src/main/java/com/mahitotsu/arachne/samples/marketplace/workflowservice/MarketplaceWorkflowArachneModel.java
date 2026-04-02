package com.mahitotsu.arachne.samples.marketplace.workflowservice;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mahitotsu.arachne.samples.marketplace.workflowservice.WorkflowContracts.Recommendation;
import com.mahitotsu.arachne.strands.model.Model;
import com.mahitotsu.arachne.strands.model.ModelEvent;
import com.mahitotsu.arachne.strands.model.ToolSelection;
import com.mahitotsu.arachne.strands.model.ToolSpec;
import com.mahitotsu.arachne.strands.types.ContentBlock;
import com.mahitotsu.arachne.strands.types.Message;

final class MarketplaceWorkflowArachneModel implements Model {

    @Override
    public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools) {
        return converse(messages, tools, null, null);
    }

    @Override
    public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools, String systemPrompt) {
        return converse(messages, tools, systemPrompt, null);
    }

    @Override
    public Iterable<ModelEvent> converse(
            List<Message> messages,
            List<ToolSpec> tools,
            String systemPrompt,
            ToolSelection toolSelection) {
        String agentName = agentName(systemPrompt, latestUserText(messages));
        return switch (agentName) {
            case "shipment-agent" -> shipmentResponse(messages);
            case "escrow-agent" -> escrowResponse(messages);
            case "risk-agent" -> riskResponse(messages);
            default -> workflowResponse(messages, tools);
        };
    }

    private Iterable<ModelEvent> shipmentResponse(List<Message> messages) {
        if (!hasSkillActivation(messages, "shipment-evidence-review")) {
            return toolUse("shipment-skill", "activate_skill", Map.of("name", "shipment-evidence-review"));
        }
        Map<String, String> prompt = promptValues(messages);
        if ("operator-follow-up".equalsIgnoreCase(prompt.get("mode"))) {
            return structuredOutput(
                "shipment-follow-up",
                Map.of(
                    "summary",
                    "shipment-agent reviewed the operator instruction \""
                        + blankSafe(prompt.get("instruction"))
                        + "\" and focused on shipment evidence: "
                        + blankSafe(prompt.get("existingEvidence"))));
        }
        return structuredOutput(
                "shipment-summary",
                Map.of(
                        "summary",
                        prompt.get("milestoneSummary") + " Tracking number: " + prompt.get("trackingNumber") + ". "
                                + prompt.get("shippingExceptionSummary")));
    }

    private Iterable<ModelEvent> escrowResponse(List<Message> messages) {
        if (!hasSkillActivation(messages, "settlement-eligibility-summary")) {
            return toolUse("escrow-skill", "activate_skill", Map.of("name", "settlement-eligibility-summary"));
        }
        Map<String, String> prompt = promptValues(messages);
        if ("operator-follow-up".equalsIgnoreCase(prompt.get("mode"))) {
            return structuredOutput(
                "escrow-follow-up",
                Map.of(
                    "summary",
                    "escrow-agent reviewed the operator instruction \""
                        + blankSafe(prompt.get("instruction"))
                        + "\" and focused on settlement posture: "
                        + blankSafe(prompt.get("existingEvidence"))));
        }
        return structuredOutput(
                "escrow-summary",
                Map.of(
                        "summary",
                        prompt.get("summary") + " Hold state: " + prompt.get("holdState") + ". Eligibility: "
                                + prompt.get("settlementEligibility") + "."));
    }

    private Iterable<ModelEvent> riskResponse(List<Message> messages) {
        if (!hasSkillActivation(messages, "risk-review-summary")) {
            return toolUse("risk-skill", "activate_skill", Map.of("name", "risk-review-summary"));
        }
        Map<String, String> prompt = promptValues(messages);
        if ("operator-follow-up".equalsIgnoreCase(prompt.get("mode"))) {
            return structuredOutput(
                "risk-follow-up",
                Map.of(
                    "summary",
                    "risk-agent reviewed the operator instruction \""
                        + blankSafe(prompt.get("instruction"))
                        + "\" and focused on risk controls: "
                        + blankSafe(prompt.get("existingEvidence"))));
        }
        return structuredOutput(
                "risk-summary",
                Map.of(
                        "summary",
                        prompt.get("summary") + " Indicators: " + prompt.get("indicatorSummary") + ". Flags: "
                                + prompt.get("policyFlags") + "."));
    }

    private Iterable<ModelEvent> workflowResponse(List<Message> messages, List<ToolSpec> tools) {
        Map<String, Object> approvalResponse = latestApprovalResponse(messages);
        if (approvalResponse != null) {
            return approvalCompleted(approvalResponse);
        }

        Map<String, String> prompt = promptValues(messages);
        ToolOutcome shortcutOutcome = latestToolOutcome(messages, "workflow-shortcut");
        if ("approval-start".equalsIgnoreCase(prompt.get("mode"))) {
            if (!hasSkillActivation(messages, "approval-escalation-and-resume")) {
                return toolUse("workflow-skill-3", "activate_skill", Map.of("name", "approval-escalation-and-resume"));
            }
            return approvalRequested(prompt);
        }

        List<String> toolNames = tools.stream().map(ToolSpec::name).toList();
        if (!hasSkillActivation(messages, "marketplace-dispute-intake")) {
            return toolUse("workflow-skill-1", "activate_skill", Map.of("name", "marketplace-dispute-intake"));
        }
        if (!hasSkillActivation(messages, "item-not-received-investigation")) {
            return toolUse("workflow-skill-2", "activate_skill", Map.of("name", "item-not-received-investigation"));
        }
        if (!hasSkillActivation(messages, "approval-escalation-and-resume")) {
            return toolUse("workflow-skill-3", "activate_skill", Map.of("name", "approval-escalation-and-resume"));
        }
        if (toolNames.contains("resource_list") && latestToolResult(messages, "resource_list") == null) {
            return toolUse(
                    "workflow-resources",
                    "resource_list",
                    Map.of("location", "classpath:/marketplace-workflow/", "pattern", "**/*.md"));
        }
        if (toolNames.contains("resource_reader")
                && resourceContent(messages, "classpath:/marketplace-workflow/runbooks/item-not-received.md") == null) {
            return toolUse(
                    "workflow-runbook",
                    "resource_reader",
                    Map.of("location", "classpath:/marketplace-workflow/runbooks/item-not-received.md"));
        }
        if (toolNames.contains("resource_reader")
                && resourceContent(messages, "classpath:/marketplace-workflow/policies/settlement-policy-summary.md") == null) {
            return toolUse(
                    "workflow-policy",
                    "resource_reader",
                    Map.of("location", "classpath:/marketplace-workflow/policies/settlement-policy-summary.md"));
        }
        if (toolNames.contains("resource_reader")
                && resourceContent(messages, "classpath:/marketplace-workflow/policies/finance-control-thresholds.md") == null) {
            return toolUse(
                    "workflow-threshold",
                    "resource_reader",
                    Map.of("location", "classpath:/marketplace-workflow/policies/finance-control-thresholds.md"));
        }
        ToolOutcome firstContextProbe = latestToolOutcome(messages, "workflow-context-probe-1");
        ToolOutcome secondContextProbe = latestToolOutcome(messages, "workflow-context-probe-2");
        if (toolNames.contains(MarketplaceOperatorContextPlugin.TOOL_NAME)
            && (firstContextProbe == null || secondContextProbe == null)) {
            return toolUses(List.of(
                new ModelEvent.ToolUse(
                    "workflow-context-probe-1",
                    MarketplaceOperatorContextPlugin.TOOL_NAME,
                    Map.of("probe", "shipment-delegation")),
                new ModelEvent.ToolUse(
                    "workflow-context-probe-2",
                    MarketplaceOperatorContextPlugin.TOOL_NAME,
                    Map.of("probe", "risk-delegation"))));
        }

        Recommendation recommendation = recommendation(prompt.get("caseType"), prompt.get("amount"));
        if (recommendation == Recommendation.REFUND && shortcutOutcome == null) {
            return toolUse(
                "workflow-shortcut",
                MarketplaceSettlementShortcutSteering.TOOL_NAME,
                Map.of(
                    "caseId", prompt.get("caseId"),
                    "path", "instant_refund",
                    "recommendation", recommendation.name(),
                    "amount", prompt.get("amount")));
        }
        boolean shortcutRedirected = shortcutOutcome != null && "error".equalsIgnoreCase(shortcutOutcome.status());
        String recommendationMessage = recommendation == Recommendation.REFUND
            ? shortcutRedirected
                ? "case-workflow-agent kept the refund recommendation but redirected the case away from the automatic settlement shortcut to finance control approval."
                : "case-workflow-agent recommends a refund after reviewing packaged guidance, downstream evidence, and the low-value dispute path."
                : "case-workflow-agent recommends keeping the hold until finance control confirms the next step under the packaged settlement policy.";
        return structuredOutput(
                "workflow-decision",
                Map.of(
                        "recommendation", recommendation.name(),
                        "recommendationMessage", recommendationMessage,
                "approvalMessage", shortcutRedirected
                    ? "Finance control approval is required because workflow steering blocked the automatic settlement shortcut and redirected the case to the approval path."
                    : "Finance control approval is required for settlement progression after the workflow reviewed the packaged threshold reference.",
                        "policyReference", WorkflowRuntimeAdapter.POLICY_REFERENCE));
    }

    private Iterable<ModelEvent> approvalRequested(Map<String, String> prompt) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("caseId", prompt.get("caseId"));
        payload.put("caseType", prompt.get("caseType"));
        payload.put("orderId", prompt.get("orderId"));
        payload.put("recommendation", prompt.get("recommendation"));
        payload.put("requestedRole", "FINANCE_CONTROL");
        payload.put("message", prompt.get("approvalMessage"));
        return toolUse("workflow-approval", MarketplaceFinanceControlApprovalPlugin.TOOL_NAME, payload);
    }

    private Iterable<ModelEvent> approvalCompleted(Map<String, Object> approvalResponse) {
        boolean approved = "APPROVE".equalsIgnoreCase(stringValue(approvalResponse.get("decision")));
        String actorId = stringValue(approvalResponse.get("actorId"));
        String actorText = actorId == null || actorId.isBlank() ? "" : " by " + actorId;
        String message = approved
                ? "Finance control approved the workflow recommendation" + actorText + "."
                : "Finance control rejected the workflow recommendation" + actorText + ".";
        return List.of(
                new ModelEvent.TextDelta(message),
                new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
    }

    private Recommendation recommendation(String caseType, String amountText) {
        if (!"ITEM_NOT_RECEIVED".equalsIgnoreCase(caseType)) {
            return Recommendation.CONTINUED_HOLD;
        }
        if (amountText == null || amountText.isBlank()) {
            return Recommendation.CONTINUED_HOLD;
        }
        BigDecimal amount = new BigDecimal(amountText);
        return amount.compareTo(WorkflowRuntimeAdapter.AUTOMATED_REFUND_THRESHOLD) <= 0
                ? Recommendation.REFUND
                : Recommendation.CONTINUED_HOLD;
    }

    private Iterable<ModelEvent> toolUse(String toolUseId, String toolName, Map<String, Object> input) {
        return List.of(
                new ModelEvent.ToolUse(toolUseId, toolName, input),
                new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
    }

    private Iterable<ModelEvent> toolUses(List<ModelEvent.ToolUse> toolUses) {
        java.util.ArrayList<ModelEvent> events = new java.util.ArrayList<>(toolUses);
        events.add(new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(toolUses.size(), toolUses.size())));
        return List.copyOf(events);
    }

    private Iterable<ModelEvent> structuredOutput(String toolUseId, Map<String, Object> payload) {
        return List.of(
                new ModelEvent.ToolUse(toolUseId, "structured_output", payload),
                new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
    }

    private String agentName(String systemPrompt, String latestUserText) {
        String combined = (systemPrompt == null ? "" : systemPrompt) + "\n" + (latestUserText == null ? "" : latestUserText);
        if (combined.contains("shipment-agent")) {
            return "shipment-agent";
        }
        if (combined.contains("escrow-agent")) {
            return "escrow-agent";
        }
        if (combined.contains("risk-agent")) {
            return "risk-agent";
        }
        return "case-workflow-agent";
    }

    private boolean hasSkillActivation(List<Message> messages, String skillName) {
        for (Message message : messages) {
            for (ContentBlock block : message.content()) {
                if (block instanceof ContentBlock.ToolResult toolResult
                        && toolResult.content() instanceof Map<?, ?> content
                        && "skill_activation".equals(content.get("type"))
                        && skillName.equals(content.get("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private Map<String, ?> latestToolResult(List<Message> messages, String type) {
        for (int index = messages.size() - 1; index >= 0; index--) {
            Message message = messages.get(index);
            for (ContentBlock block : message.content()) {
                if (block instanceof ContentBlock.ToolResult toolResult
                        && toolResult.content() instanceof Map<?, ?> content
                        && type.equals(content.get("type"))) {
                    LinkedHashMap<String, Object> values = new LinkedHashMap<>();
                    content.forEach((key, value) -> values.put(String.valueOf(key), value));
                    return values;
                }
            }
        }
        return null;
    }

    private ToolOutcome latestToolOutcome(List<Message> messages, String toolUseId) {
        for (int index = messages.size() - 1; index >= 0; index--) {
            Message message = messages.get(index);
            for (ContentBlock block : message.content()) {
                if (block instanceof ContentBlock.ToolResult toolResult && toolUseId.equals(toolResult.toolUseId())) {
                    return new ToolOutcome(toolResult.status(), toolResult.content());
                }
            }
        }
        return null;
    }

    private String resourceContent(List<Message> messages, String location) {
        for (int index = messages.size() - 1; index >= 0; index--) {
            Message message = messages.get(index);
            for (ContentBlock block : message.content()) {
                if (block instanceof ContentBlock.ToolResult toolResult
                        && toolResult.content() instanceof Map<?, ?> content
                        && "resource".equals(content.get("type"))
                        && location.equals(content.get("location"))
                        && content.get("content") instanceof String body) {
                    return body;
                }
            }
        }
        return null;
    }

    private Map<String, Object> latestApprovalResponse(List<Message> messages) {
        for (int index = messages.size() - 1; index >= 0; index--) {
            Message message = messages.get(index);
            for (ContentBlock block : message.content()) {
                if (block instanceof ContentBlock.ToolResult toolResult
                        && "workflow-approval".equals(toolResult.toolUseId())
                        && toolResult.content() instanceof Map<?, ?> content) {
                    LinkedHashMap<String, Object> values = new LinkedHashMap<>();
                    content.forEach((key, value) -> values.put(String.valueOf(key), value));
                    return values;
                }
            }
        }
        return null;
    }

    private Map<String, String> promptValues(List<Message> messages) {
        Map<String, String> values = new LinkedHashMap<>();
        String text = latestUserText(messages);
        if (text == null) {
            return values;
        }
        for (String line : text.split("\\R")) {
            int separator = line.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            values.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
        }
        return values;
    }

    private String latestUserText(List<Message> messages) {
        for (int index = messages.size() - 1; index >= 0; index--) {
            Message message = messages.get(index);
            if (message.role() != Message.Role.USER) {
                continue;
            }
            for (ContentBlock block : message.content()) {
                if (block instanceof ContentBlock.Text text) {
                    return text.text();
                }
            }
        }
        return null;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String blankSafe(String value) {
        return value == null ? "" : value;
    }

    private record ToolOutcome(String status, Object content) {
    }
}