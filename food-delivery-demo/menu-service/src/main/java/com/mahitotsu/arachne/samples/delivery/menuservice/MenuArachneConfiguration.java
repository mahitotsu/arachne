package com.mahitotsu.arachne.samples.delivery.menuservice;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mahitotsu.arachne.strands.model.Model;
import com.mahitotsu.arachne.strands.model.ToolSpec;
import com.mahitotsu.arachne.strands.tool.Tool;
import com.mahitotsu.arachne.strands.tool.ToolInvocationContext;
import com.mahitotsu.arachne.strands.tool.ToolResult;

@Configuration
class MenuArachneConfiguration {

    @Bean
    Tool catalogLookupTool(MenuRepository repository) {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec("catalog_lookup_tool", "ローカルメニューカタログを参照し、候補一覧を返す。", schema());
            }

            @Override
            public ToolResult invoke(Object input) {
                return invoke(input, new ToolInvocationContext("catalog_lookup_tool", null, input, null));
            }

            @Override
            public ToolResult invoke(Object input, ToolInvocationContext context) {
                String query = String.valueOf(values(input).getOrDefault("query", ""));
                List<MenuItem> matches = repository.search(query);
                return ToolResult.success(context.toolUseId(), Map.of(
                        "matchSummary", repository.describeSearch(query),
                        "itemIds", matches.stream().map(MenuItem::id).toList()));
            }
        };
    }

    @Bean
    Tool calculateTotalTool(MenuRepository repository) {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec("calculate_total_tool", "候補セットの合計金額を計算する。", totalSchema());
            }

            @Override
            public ToolResult invoke(Object input) {
                return invoke(input, new ToolInvocationContext("calculate_total_tool", null, input, null));
            }

            @Override
            public ToolResult invoke(Object input, ToolInvocationContext context) {
                List<String> itemIds = stringList(values(input).get("itemIds"));
                BigDecimal total = repository.calculateTotal(itemIds.stream()
                        .map(id -> repository.findById(id).orElse(null))
                        .filter(java.util.Objects::nonNull)
                        .toList());
                return ToolResult.success(context.toolUseId(), Map.of("total", total));
            }
        };
    }

    @Bean
    Tool menuSubstitutionLookupTool(MenuRepository repository) {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec(
                        "menu_substitution_lookup",
                        "リクエストされたアイテムが在庫切れのときに kitchen-agent 向けのメニュー代替品を準備する。",
                        substitutionSchema());
            }

            @Override
            public ToolResult invoke(Object input) {
                return invoke(input, new ToolInvocationContext("menu_substitution_lookup", null, input, null));
            }

            @Override
            public ToolResult invoke(Object input, ToolInvocationContext context) {
                Map<String, Object> values = values(input);
                String unavailableItemId = String.valueOf(values.getOrDefault("unavailableItemId", ""));
                String customerMessage = String.valueOf(values.getOrDefault("customerMessage", ""));
                return ToolResult.success(context.toolUseId(), Map.of(
                        "substitutionSummary", repository.describeSubstitutes(unavailableItemId, customerMessage)));
            }
        };
    }

    @Bean
    @ConditionalOnProperty(name = "delivery.model.mode", havingValue = "deterministic", matchIfMissing = false)
    Model menuDeterministicModel() {
        return new MenuDeterministicModel();
    }

    private static ObjectNode schema() {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("type", "object");
        ObjectNode properties = root.putObject("properties");
        properties.putObject("query").put("type", "string");
        root.putArray("required").add("query");
        root.put("additionalProperties", false);
        return root;
    }

    private static ObjectNode substitutionSchema() {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("type", "object");
        ObjectNode properties = root.putObject("properties");
        properties.putObject("unavailableItemId").put("type", "string");
        properties.putObject("customerMessage").put("type", "string");
        root.putArray("required").add("unavailableItemId").add("customerMessage");
        root.put("additionalProperties", false);
        return root;
    }

    private static ObjectNode totalSchema() {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("type", "object");
        ObjectNode properties = root.putObject("properties");
        ObjectNode itemIds = properties.putObject("itemIds");
        itemIds.put("type", "array");
        itemIds.putObject("items").put("type", "string");
        root.putArray("required").add("itemIds");
        root.put("additionalProperties", false);
        return root;
    }

    private static Map<String, Object> values(Object input) {
        if (input instanceof Map<?, ?> rawValues) {
            LinkedHashMap<String, Object> values = new LinkedHashMap<>();
            rawValues.forEach((key, value) -> values.put(String.valueOf(key), value));
            return values;
        }
        return Map.of();
    }

    private static List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}