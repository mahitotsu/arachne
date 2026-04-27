package com.mahitotsu.arachne.samples.delivery.registryservice;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
class RegistryArachneConfiguration {

    @Bean
    Tool capabilityMatchTool(RegistryRepository repository) {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                ObjectNode root = JsonNodeFactory.instance.objectNode();
                root.put("type", "object");
                ObjectNode properties = root.putObject("properties");
                properties.putObject("query")
                        .put("type", "string")
                        .put("description", "自然言語のサービス発見クエリ");
                properties.putObject("availableOnly")
                        .put("type", "boolean")
                        .put("description", "AVAILABLE の候補だけに絞るかどうか");
                root.putArray("required").add("query");
                root.put("additionalProperties", false);
                return new ToolSpec("capability_match",
                        "登録済みサービスのケイパビリティ説明を自然言語クエリと照合し、利用可能な候補を返す。", root);
            }

            @Override
            public ToolResult invoke(Object input) {
                return invoke(input, new ToolInvocationContext("capability_match", null, input, null));
            }

            @Override
            public ToolResult invoke(Object input, ToolInvocationContext context) {
                Map<String, Object> values = values(input);
                String query = String.valueOf(values.getOrDefault("query", ""));
                boolean availableOnly = Boolean.parseBoolean(String.valueOf(values.getOrDefault("availableOnly", true)));
                List<Map<String, Object>> matches = repository.discover(query, availableOnly).stream()
                        .<Map<String, Object>>map(match -> {
                            Map<String, Object> mapped = new LinkedHashMap<>();
                            mapped.put("serviceName", match.serviceName());
                            mapped.put("endpoint", match.endpoint());
                            mapped.put("capability", match.capability());
                            mapped.put("agentName", match.agentName());
                            mapped.put("requestMethod", match.requestMethod());
                            mapped.put("requestPath", match.requestPath());
                            mapped.put("status", match.status().name());
                            return mapped;
                        })
                        .toList();
                return ToolResult.success(context.toolUseId(), Map.of("matches", matches));
            }
        };
    }

    @Bean
    Model capabilityRegistryDeterministicModel() {
        return new CapabilityRegistryDeterministicModel();
    }

    private static Map<String, Object> values(Object input) {
        if (input instanceof Map<?, ?> rawValues) {
            LinkedHashMap<String, Object> values = new LinkedHashMap<>();
            rawValues.forEach((key, value) -> values.put(String.valueOf(key), value));
            return values;
        }
        return Map.of();
    }
}