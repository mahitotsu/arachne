package io.arachne.strands.tool.builtin;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.arachne.strands.model.ToolSpec;
import io.arachne.strands.tool.Tool;
import io.arachne.strands.tool.ToolResult;

public final class CalculatorTool implements Tool {

    public static final String TOOL_NAME = "calculator";
    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final CalculatorExpressionEvaluator evaluator = new CalculatorExpressionEvaluator();
    private final ToolSpec spec = new ToolSpec(
            TOOL_NAME,
            "Evaluates deterministic arithmetic expressions with BigDecimal-based numeric helpers.",
            inputSchema());

    @Override
    public ToolSpec spec() {
        return spec;
    }

    @Override
    public ToolResult invoke(Object input) {
        try {
            String expression = extractExpression(input);
            BigDecimal value = evaluator.evaluate(expression);
            LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
            payload.put("type", TOOL_NAME);
            payload.put("expression", expression);
            payload.put("result", evaluator.format(value));
            return ToolResult.success(null, Map.copyOf(payload));
        } catch (IllegalArgumentException e) {
            return ToolResult.error(null, e.getMessage());
        }
    }

    private String extractExpression(Object input) {
        if (!(input instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("calculator requires a non-blank 'expression' field.");
        }
        Object rawExpression = map.get("expression");
        if (!(rawExpression instanceof String expression) || expression.isBlank()) {
            throw new IllegalArgumentException("calculator requires a non-blank 'expression' field.");
        }
        return expression;
    }

    private static ObjectNode inputSchema() {
        ObjectNode root = JSON.objectNode();
        root.put("type", "object");
        ObjectNode properties = root.putObject("properties");
        ObjectNode expression = properties.putObject("expression");
        expression.put("type", "string");
        expression.put("description", "Arithmetic expression using +, -, *, /, %, parentheses, abs, round, min, and max.");
        root.putArray("required").add("expression");
        root.put("additionalProperties", false);
        return root;
    }
}