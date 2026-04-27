package com.mahitotsu.arachne.samples.delivery.supportservice;

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
class SupportArachneConfiguration {

    @Bean
    Tool faqLookupTool(FaqRepository repository) {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                ObjectNode root = JsonNodeFactory.instance.objectNode();
                root.put("type", "object");
                ObjectNode properties = root.putObject("properties");
                properties.putObject("query")
                        .put("type", "string")
                        .put("description", "FAQ を検索する問い合わせ文");
                properties.putObject("limit")
                        .put("type", "integer")
                        .put("description", "返す件数の上限");
                root.putArray("required").add("query");
                root.put("additionalProperties", false);
                return new ToolSpec("faq_lookup", "FAQナレッジを検索して回答候補を返す。", root);
            }

            @Override
            public ToolResult invoke(Object input) {
                return invoke(input, new ToolInvocationContext("faq_lookup", null, input, null));
            }

            @Override
            public ToolResult invoke(Object input, ToolInvocationContext context) {
                Map<String, Object> values = values(input);
                String query = String.valueOf(values.getOrDefault("query", ""));
                int limit = Integer.parseInt(String.valueOf(values.getOrDefault("limit", 3)));
                List<Map<String, Object>> matches = repository.lookup(query, limit).stream()
                        .<Map<String, Object>>map(entry -> {
                            Map<String, Object> mapped = new LinkedHashMap<>();
                            mapped.put("id", entry.id());
                            mapped.put("question", entry.question());
                            mapped.put("answer", entry.answer());
                            mapped.put("tags", entry.tags());
                            return mapped;
                        })
                        .toList();
                return ToolResult.success(context.toolUseId(), Map.of("matches", matches));
            }
        };
    }

    @Bean
    Tool campaignLookupTool(CampaignRepository repository) {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                ObjectNode root = JsonNodeFactory.instance.objectNode();
                root.put("type", "object");
                root.putObject("properties");
                root.put("additionalProperties", false);
                return new ToolSpec("campaign_lookup", "現在有効なキャンペーンを返す。", root);
            }

            @Override
            public ToolResult invoke(Object input) {
                return invoke(input, new ToolInvocationContext("campaign_lookup", null, input, null));
            }

            @Override
            public ToolResult invoke(Object input, ToolInvocationContext context) {
                List<Map<String, Object>> campaigns = repository.activeCampaigns().stream()
                        .<Map<String, Object>>map(campaign -> {
                            Map<String, Object> mapped = new LinkedHashMap<>();
                            mapped.put("campaignId", campaign.campaignId());
                            mapped.put("title", campaign.title());
                            mapped.put("description", campaign.description());
                            mapped.put("badge", campaign.badge());
                            mapped.put("validUntil", campaign.validUntil());
                            return mapped;
                        })
                        .toList();
                return ToolResult.success(context.toolUseId(), Map.of("campaigns", campaigns));
            }
        };
    }

    @Bean
    Tool serviceStatusLookupTool(SupportStatusGateway gateway) {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                ObjectNode root = JsonNodeFactory.instance.objectNode();
                root.put("type", "object");
                root.putObject("properties");
                root.put("additionalProperties", false);
                return new ToolSpec("service_status_lookup", "registry-service 経由で現在の稼働状況を取得する。", root);
            }

            @Override
            public ToolResult invoke(Object input) {
                return invoke(input, new ToolInvocationContext("service_status_lookup", null, input, null));
            }

            @Override
            public ToolResult invoke(Object input, ToolInvocationContext context) {
                List<Map<String, Object>> services = gateway.currentStatuses().stream()
                        .<Map<String, Object>>map(service -> {
                            Map<String, Object> mapped = new LinkedHashMap<>();
                            mapped.put("serviceName", service.serviceName());
                            mapped.put("status", service.status());
                            mapped.put("healthEndpoint", service.healthEndpoint());
                            return mapped;
                        })
                        .toList();
                return ToolResult.success(context.toolUseId(), Map.of("services", services));
            }
        };
    }

    @Bean
    Tool feedbackLookupTool(FeedbackRepository repository) {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                ObjectNode root = JsonNodeFactory.instance.objectNode();
                root.put("type", "object");
                ObjectNode properties = root.putObject("properties");
                properties.putObject("query")
                        .put("type", "string")
                        .put("description", "過去問い合わせを検索する条件");
                properties.putObject("limit")
                        .put("type", "integer")
                        .put("description", "返す件数の上限");
                root.putArray("required").add("query");
                root.put("additionalProperties", false);
                return new ToolSpec("feedback_lookup", "過去の問い合わせ・フィードバックを検索する。", root);
            }

            @Override
            public ToolResult invoke(Object input) {
                return invoke(input, new ToolInvocationContext("feedback_lookup", null, input, null));
            }

            @Override
            public ToolResult invoke(Object input, ToolInvocationContext context) {
                Map<String, Object> values = values(input);
                String query = String.valueOf(values.getOrDefault("query", ""));
                int limit = Integer.parseInt(String.valueOf(values.getOrDefault("limit", 3)));
                List<Map<String, Object>> entries = repository.lookup(query, limit).stream()
                        .<Map<String, Object>>map(entry -> {
                            Map<String, Object> mapped = new LinkedHashMap<>();
                            mapped.put("feedbackId", entry.feedbackId());
                            mapped.put("orderId", entry.orderId());
                            mapped.put("category", entry.category());
                            mapped.put("summary", entry.summary());
                            mapped.put("escalationRequired", entry.escalationRequired());
                            mapped.put("createdAt", entry.createdAt());
                            return mapped;
                        })
                        .toList();
                return ToolResult.success(context.toolUseId(), Map.of("entries", entries));
            }
        };
    }

    @Bean
    Tool orderHistoryLookupTool(OrderHistorySnapshotStore snapshotStore) {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                ObjectNode root = JsonNodeFactory.instance.objectNode();
                root.put("type", "object");
                ObjectNode properties = root.putObject("properties");
                properties.putObject("customerId")
                        .put("type", "string")
                        .put("description", "注文履歴を参照する顧客ID");
                root.putArray("required").add("customerId");
                root.put("additionalProperties", false);
                return new ToolSpec("order_history_lookup", "認証済みカスタマーの直近注文履歴を取得する。", root);
            }

            @Override
            public ToolResult invoke(Object input) {
                return invoke(input, new ToolInvocationContext("order_history_lookup", null, input, null));
            }

            @Override
            public ToolResult invoke(Object input, ToolInvocationContext context) {
                Map<String, Object> values = values(input);
                String customerId = String.valueOf(values.getOrDefault("customerId", ""));
                List<Map<String, Object>> orders = snapshotStore.get(customerId).stream()
                        .<Map<String, Object>>map(order -> {
                            Map<String, Object> mapped = new LinkedHashMap<>();
                            mapped.put("orderId", order.orderId());
                            mapped.put("itemSummary", order.itemSummary());
                            mapped.put("total", order.total());
                            mapped.put("etaLabel", order.etaLabel());
                            mapped.put("paymentStatus", order.paymentStatus());
                            mapped.put("createdAt", order.createdAt());
                            return mapped;
                        })
                        .toList();
                return ToolResult.success(context.toolUseId(), Map.of("orders", orders));
            }
        };
    }

    @Bean
    @ConditionalOnProperty(name = "delivery.model.mode", havingValue = "deterministic", matchIfMissing = false)
    Model supportDeterministicModel() {
        return new SupportDeterministicModel();
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