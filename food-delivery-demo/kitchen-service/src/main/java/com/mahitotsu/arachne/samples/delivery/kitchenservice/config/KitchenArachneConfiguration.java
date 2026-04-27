package com.mahitotsu.arachne.samples.delivery.kitchenservice.config;

import static com.mahitotsu.arachne.samples.delivery.kitchenservice.domain.KitchenTypes.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mahitotsu.arachne.samples.delivery.kitchenservice.infrastructure.KitchenRepository;
import com.mahitotsu.arachne.strands.model.Model;
import com.mahitotsu.arachne.strands.model.ToolSpec;
import com.mahitotsu.arachne.strands.tool.Tool;
import com.mahitotsu.arachne.strands.tool.ToolInvocationContext;
import com.mahitotsu.arachne.strands.tool.ToolResult;

@Configuration
class KitchenArachneConfiguration {

    @Bean
    Tool kitchenLookupTool(KitchenRepository repository) {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec("kitchen_inventory_lookup", "選択したアイテムの在庫プレッシャーと調理時間を確認する。", schema());
            }

            @Override
            public ToolResult invoke(Object input) {
                return invoke(input, new ToolInvocationContext("kitchen_inventory_lookup", null, input, null));
            }

            @Override
            public ToolResult invoke(Object input, ToolInvocationContext context) {
                String rawItems = String.valueOf(values(input).getOrDefault("items", ""));
                List<String> itemIds = rawItems.isBlank() ? List.of() : List.of(rawItems.split(","));
                List<KitchenItemStatus> statuses = repository.check(itemIds);
                return ToolResult.success(context.toolUseId(), Map.of(
                        "inventorySummary", repository.describeStatuses(statuses),
                        "unavailableItemIds", statuses.stream().filter(status -> !status.available()).map(KitchenItemStatus::itemId).toList()));
            }
        };
    }

    @Bean
    Tool prepSchedulerTool(KitchenRepository repository) {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec("prep_scheduler", "調理ラインごとのキュー遅延と提供見込み時間を計算する。", schema());
            }

            @Override
            public ToolResult invoke(Object input) {
                return invoke(input, new ToolInvocationContext("prep_scheduler", null, input, null));
            }

            @Override
            public ToolResult invoke(Object input, ToolInvocationContext context) {
                String rawItems = String.valueOf(values(input).getOrDefault("items", ""));
                List<String> itemIds = rawItems.isBlank() ? List.of() : List.of(rawItems.split(","));
                PrepSchedule schedule = repository.schedule(itemIds);
                return ToolResult.success(context.toolUseId(), Map.of(
                        "readyInMinutes", schedule.readyInMinutes(),
                        "scheduleSummary", schedule.summary(),
                        "alternativeSuggestion", schedule.alternativeSuggestion() == null
                                ? ""
                                : schedule.alternativeSuggestion()));
            }
        };
    }

    @Bean
    @ConditionalOnProperty(name = "delivery.model.mode", havingValue = "deterministic", matchIfMissing = false)
    Model kitchenDeterministicModel() {
        return new KitchenDeterministicModel();
    }

    private static ObjectNode schema() {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("type", "object");
        ObjectNode properties = root.putObject("properties");
        properties.putObject("items").put("type", "string");
        root.putArray("required").add("items");
        root.put("additionalProperties", false);
        return root;
    }

    static Map<String, Object> values(Object input) {
        if (input instanceof Map<?, ?> rawValues) {
            LinkedHashMap<String, Object> values = new LinkedHashMap<>();
            rawValues.forEach((key, value) -> values.put(String.valueOf(key), value));
            return values;
        }
        return Map.of();
    }

    static List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}