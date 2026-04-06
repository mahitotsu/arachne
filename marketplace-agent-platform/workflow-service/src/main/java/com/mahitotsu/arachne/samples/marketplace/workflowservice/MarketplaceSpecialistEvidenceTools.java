package com.mahitotsu.arachne.samples.marketplace.workflowservice;

import java.math.BigDecimal;
import java.util.Map;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mahitotsu.arachne.strands.model.ToolSpec;
import com.mahitotsu.arachne.strands.tool.Tool;
import com.mahitotsu.arachne.strands.tool.ToolInvocationContext;
import com.mahitotsu.arachne.strands.tool.ToolResult;

final class ShipmentEvidenceLookupTool implements Tool {

    static final String TOOL_NAME = "shipment_evidence_lookup";

    private final DownstreamGateway downstreamGateway;

    ShipmentEvidenceLookupTool(DownstreamGateway downstreamGateway) {
        this.downstreamGateway = downstreamGateway;
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                TOOL_NAME,
                "Query the live shipment-service evidence summary for the current marketplace case before summarizing shipment findings.",
                SpecialistToolSchemas.shipmentEvidenceLookupSchema());
    }

    @Override
    public ToolResult invoke(Object input) {
        return invoke(input, new ToolInvocationContext(TOOL_NAME, null, input, null));
    }

    @Override
    public ToolResult invoke(Object input, ToolInvocationContext context) {
        Map<String, Object> values = SpecialistToolSchemas.values(input);
        var response = downstreamGateway.shipmentEvidence(new DownstreamContracts.ShipmentEvidenceRequest(
                SpecialistToolSchemas.string(values, "caseId"),
                SpecialistToolSchemas.string(values, "caseType"),
                SpecialistToolSchemas.string(values, "disputeSummary"),
                SpecialistToolSchemas.string(values, "orderId")));
        return ToolResult.success(context.toolUseId(), Map.of(
                "type", TOOL_NAME,
                "trackingNumber", response.trackingNumber(),
                "milestoneSummary", response.milestoneSummary(),
                "deliveryConfidence", response.deliveryConfidence(),
                "shippingExceptionSummary", response.shippingExceptionSummary()));
    }
}

final class EscrowEvidenceLookupTool implements Tool {

    static final String TOOL_NAME = "escrow_evidence_lookup";

    private final DownstreamGateway downstreamGateway;

    EscrowEvidenceLookupTool(DownstreamGateway downstreamGateway) {
        this.downstreamGateway = downstreamGateway;
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                TOOL_NAME,
                "Query the live escrow-service evidence summary for the current marketplace case before summarizing settlement posture.",
                SpecialistToolSchemas.escrowEvidenceLookupSchema());
    }

    @Override
    public ToolResult invoke(Object input) {
        return invoke(input, new ToolInvocationContext(TOOL_NAME, null, input, null));
    }

    @Override
    public ToolResult invoke(Object input, ToolInvocationContext context) {
        Map<String, Object> values = SpecialistToolSchemas.values(input);
        var response = downstreamGateway.escrowEvidence(new DownstreamContracts.EscrowEvidenceRequest(
                SpecialistToolSchemas.string(values, "caseId"),
                SpecialistToolSchemas.string(values, "caseType"),
                SpecialistToolSchemas.string(values, "orderId"),
                SpecialistToolSchemas.string(values, "disputeSummary"),
                SpecialistToolSchemas.decimal(values, "amount"),
                SpecialistToolSchemas.string(values, "currency"),
                SpecialistToolSchemas.string(values, "operatorId"),
                SpecialistToolSchemas.string(values, "operatorRole")));
        return ToolResult.success(context.toolUseId(), Map.of(
                "type", TOOL_NAME,
                "holdState", response.holdState(),
                "settlementEligibility", response.settlementEligibility(),
                "amount", response.amount(),
                "currency", response.currency(),
                "priorSettlementStatus", response.priorSettlementStatus(),
                "summary", response.summary()));
    }
}

final class RiskReviewLookupTool implements Tool {

    static final String TOOL_NAME = "risk_review_lookup";

    private final DownstreamGateway downstreamGateway;

    RiskReviewLookupTool(DownstreamGateway downstreamGateway) {
        this.downstreamGateway = downstreamGateway;
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                TOOL_NAME,
                "Query the live risk-service case review summary for the current marketplace case before summarizing risk posture.",
                SpecialistToolSchemas.riskReviewLookupSchema());
    }

    @Override
    public ToolResult invoke(Object input) {
        return invoke(input, new ToolInvocationContext(TOOL_NAME, null, input, null));
    }

    @Override
    public ToolResult invoke(Object input, ToolInvocationContext context) {
        Map<String, Object> values = SpecialistToolSchemas.values(input);
        var response = downstreamGateway.riskReview(new DownstreamContracts.RiskCaseReviewRequest(
                SpecialistToolSchemas.string(values, "caseId"),
                SpecialistToolSchemas.string(values, "caseType"),
                SpecialistToolSchemas.string(values, "orderId"),
                SpecialistToolSchemas.string(values, "disputeSummary"),
                SpecialistToolSchemas.string(values, "operatorRole")));
        return ToolResult.success(context.toolUseId(), Map.of(
                "type", TOOL_NAME,
                "indicatorSummary", response.indicatorSummary(),
                "manualReviewRequired", response.manualReviewRequired(),
                "policyFlags", response.policyFlags(),
                "summary", response.summary()));
    }
}

final class SpecialistToolSchemas {

    private SpecialistToolSchemas() {
    }

    static ObjectNode shipmentEvidenceLookupSchema() {
        ObjectNode root = baseObjectSchema();
        ObjectNode properties = root.putObject("properties");
        stringProperty(properties, "caseId", "Case identifier.");
        stringProperty(properties, "caseType", "Case type such as ITEM_NOT_RECEIVED.");
        stringProperty(properties, "orderId", "Order identifier for the dispute.");
        stringProperty(properties, "disputeSummary", "Current dispute summary or operator complaint.");
        required(root, "caseId", "caseType", "orderId", "disputeSummary");
        return root;
    }

    static ObjectNode escrowEvidenceLookupSchema() {
        ObjectNode root = baseObjectSchema();
        ObjectNode properties = root.putObject("properties");
        stringProperty(properties, "caseId", "Case identifier.");
        stringProperty(properties, "caseType", "Case type such as ITEM_NOT_RECEIVED.");
        stringProperty(properties, "orderId", "Order identifier for the dispute.");
        stringProperty(properties, "disputeSummary", "Current dispute summary or operator complaint.");
        numberProperty(properties, "amount", "Disputed amount for the case.");
        stringProperty(properties, "currency", "Currency code for the case amount.");
        stringProperty(properties, "operatorId", "Operator identifier visible to the workflow runtime.");
        stringProperty(properties, "operatorRole", "Operator role visible to the workflow runtime.");
        required(root, "caseId", "caseType", "orderId", "disputeSummary", "amount", "currency", "operatorId", "operatorRole");
        return root;
    }

    static ObjectNode riskReviewLookupSchema() {
        ObjectNode root = baseObjectSchema();
        ObjectNode properties = root.putObject("properties");
        stringProperty(properties, "caseId", "Case identifier.");
        stringProperty(properties, "caseType", "Case type such as ITEM_NOT_RECEIVED.");
        stringProperty(properties, "orderId", "Order identifier for the dispute.");
        stringProperty(properties, "disputeSummary", "Current dispute summary or operator complaint.");
        stringProperty(properties, "operatorRole", "Operator role visible to the workflow runtime.");
        required(root, "caseId", "caseType", "orderId", "disputeSummary", "operatorRole");
        return root;
    }

    static Map<String, Object> values(Object input) {
        if (input instanceof Map<?, ?> rawValues) {
            java.util.LinkedHashMap<String, Object> values = new java.util.LinkedHashMap<>();
            rawValues.forEach((key, value) -> values.put(String.valueOf(key), value));
            return values;
        }
        return Map.of();
    }

    static String string(Map<String, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    static BigDecimal decimal(Map<String, Object> values, String key) {
        Object value = values.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private static ObjectNode baseObjectSchema() {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("type", "object");
        root.put("additionalProperties", false);
        return root;
    }

    private static void stringProperty(ObjectNode properties, String name, String description) {
        ObjectNode property = properties.putObject(name);
        property.put("type", "string");
        property.put("description", description);
    }

    private static void numberProperty(ObjectNode properties, String name, String description) {
        ObjectNode property = properties.putObject(name);
        property.put("type", "number");
        property.put("description", description);
    }

    private static void required(ObjectNode root, String... names) {
        var required = root.putArray("required");
        for (String name : names) {
            required.add(name);
        }
    }
}