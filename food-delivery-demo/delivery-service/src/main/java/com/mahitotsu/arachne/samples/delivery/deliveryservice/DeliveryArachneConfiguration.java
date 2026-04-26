package com.mahitotsu.arachne.samples.delivery.deliveryservice;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mahitotsu.arachne.strands.model.Model;
import com.mahitotsu.arachne.strands.model.ModelEvent;
import com.mahitotsu.arachne.strands.model.ToolSelection;
import com.mahitotsu.arachne.strands.model.ToolSpec;
import com.mahitotsu.arachne.strands.tool.Tool;
import com.mahitotsu.arachne.strands.tool.ToolInvocationContext;
import com.mahitotsu.arachne.strands.tool.ToolResult;
import com.mahitotsu.arachne.strands.types.ContentBlock;
import com.mahitotsu.arachne.strands.types.Message;

@Configuration
class DeliveryArachneConfiguration {

    @Bean
    Tool courierAvailabilityTool(CourierAvailabilityRepository repo) {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                ObjectNode root = JsonNodeFactory.instance.objectNode();
                root.put("type", "object");
                ObjectNode props = root.putObject("properties");
                ObjectNode itemsNode = props.putObject("itemNames");
                itemsNode.put("type", "array");
                itemsNode.putObject("items").put("type", "string");
                itemsNode.put("description", "注文に含まれるアイテムの名前");
                root.putArray("required").add("itemNames");
                root.put("additionalProperties", false);
                return new ToolSpec("check_courier_availability",
                        "現在クーリエの空きのある配送レーン（エクスプレス/スタンダード）と準備までの所要時間を確認する。", root);
            }

            @Override
            public ToolResult invoke(Object input) {
                return invoke(input, new ToolInvocationContext("check_courier_availability", null, input, null));
            }

            @Override
            public ToolResult invoke(Object input, ToolInvocationContext context) {
                List<String> itemNames = stringList(values(input).get("itemNames"));
                CourierStatus status = repo.check(itemNames);
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("expressAvailable", status.expressAvailable());
                result.put("expressReadyInMinutes", status.expressReadyInMinutes());
                result.put("standardReadyInMinutes", status.standardReadyInMinutes());
                if (!status.expressAvailable()) {
                    result.put("expressUnavailableReason", "自社エクスプレスのスタッフは現在対応可能な人数がいません");
                }
                return ToolResult.success(context.toolUseId(), result);
            }
        };
    }

    @Bean
    Tool trafficWeatherTool(TrafficWeatherRepository repo) {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                ObjectNode root = JsonNodeFactory.instance.objectNode();
                root.put("type", "object");
                root.put("properties", root.objectNode());
                root.put("additionalProperties", false);
                return new ToolSpec("get_traffic_weather",
                        "配送 ETA に影響する現在のリアルタイム交通湋滞レベルと天気状況を取得する。", root);
            }

            @Override
            public ToolResult invoke(Object input) {
                return invoke(input, new ToolInvocationContext("get_traffic_weather", null, input, null));
            }

            @Override
            public ToolResult invoke(Object input, ToolInvocationContext context) {
                TrafficWeatherStatus status = repo.current();
                return ToolResult.success(context.toolUseId(), Map.of(
                        "trafficLevel", status.trafficLevel(),
                        "weather", status.weather(),
                        "trafficDelayMinutes", status.trafficDelayMinutes(),
                        "weatherDelayMinutes", status.weatherDelayMinutes()));
            }
        };
    }

    @Bean
    Tool discoverEtaServicesTool(EtaServiceDiscoveryGateway gateway) {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                ObjectNode root = JsonNodeFactory.instance.objectNode();
                root.put("type", "object");
                ObjectNode props = root.putObject("properties");
                props.putObject("query").put("type", "string").put("description", "discover に使う自然言語クエリ");
                props.putObject("availableOnly").put("type", "boolean").put("description", "AVAILABLE の候補だけに絞るか");
                root.putArray("required").add("query");
                root.put("additionalProperties", false);
                return new ToolSpec("discover_eta_services",
                        "registry-service に外部 ETA 提供サービスを問い合わせ、AVAILABLE な候補だけを返す。", root);
            }

            @Override
            public ToolResult invoke(Object input) {
                return invoke(input, new ToolInvocationContext("discover_eta_services", null, input, null));
            }

            @Override
            public ToolResult invoke(Object input, ToolInvocationContext context) {
                Map<String, Object> inputValues = values(input);
                String query = String.valueOf(inputValues.getOrDefault("query", "外部ETAを提供するサービスは？"));
                List<Map<String, Object>> services = gateway.discoverAvailableEtaServices(query).stream()
                        .map(service -> {
                            Map<String, Object> result = new LinkedHashMap<>();
                            result.put("serviceName", service.serviceName());
                            result.put("url", service.url());
                            result.put("status", "AVAILABLE");
                            return result;
                        })
                        .toList();
                return ToolResult.success(context.toolUseId(), Map.of("services", services));
            }
        };
    }

    @Bean
    Tool callEtaServiceTool(ExternalEtaGateway gateway) {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                ObjectNode root = JsonNodeFactory.instance.objectNode();
                root.put("type", "object");
                ObjectNode props = root.putObject("properties");
                props.putObject("url").put("type", "string").put("description", "外部 ETA API の完全 URL");
                props.putObject("serviceName").put("type", "string").put("description", "外部サービス名");
                ObjectNode itemsNode = props.putObject("itemNames");
                itemsNode.put("type", "array");
                itemsNode.putObject("items").put("type", "string");
                props.putObject("context").put("type", "string").put("description", "配送判断の文脈");
                root.putArray("required").add("url").add("itemNames");
                root.put("additionalProperties", false);
                return new ToolSpec("call_eta_service",
                        "discover で見つけた外部 ETA サービスの URL を実際に呼び出し、ETA・混雑度・料金を取得する。", root);
            }

            @Override
            public ToolResult invoke(Object input) {
                return invoke(input, new ToolInvocationContext("call_eta_service", null, input, null));
            }

            @Override
            public ToolResult invoke(Object input, ToolInvocationContext context) {
                Map<String, Object> inputValues = values(input);
                EtaServiceTarget target = new EtaServiceTarget(
                        String.valueOf(inputValues.getOrDefault("serviceName", "external-service")),
                        String.valueOf(inputValues.getOrDefault("url", "")));
                Optional<ExternalEtaQuote> quote = gateway.quote(
                        target,
                        stringList(inputValues.get("itemNames")),
                        String.valueOf(inputValues.getOrDefault("context", "")));
                if (quote.isEmpty()) {
                    return ToolResult.success(context.toolUseId(), Map.of(
                            "service", target.serviceName(),
                            "status", "NOT_AVAILABLE",
                            "note", "外部 ETA サービスは現在利用できません"));
                }
                ExternalEtaQuote result = quote.get();
                return ToolResult.success(context.toolUseId(), Map.of(
                        "service", result.serviceName(),
                        "status", "AVAILABLE",
                        "etaMinutes", result.etaMinutes(),
                        "congestion", result.congestion(),
                        "fee", result.fee(),
                        "note", result.note()));
            }
        };
    }

    @Bean
    @ConditionalOnProperty(name = "delivery.model.mode", havingValue = "deterministic", matchIfMissing = false)
    Model deliveryDeterministicModel() {
        return new DeliveryDeterministicModel();
    }

    private static List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private static Map<String, Object> values(Object input) {
        if (input instanceof Map<?, ?> rawValues) {
            LinkedHashMap<String, Object> values = new LinkedHashMap<>();
            rawValues.forEach((key, value) -> values.put(String.valueOf(key), value));
            return values;
        }
        return Map.of();
    }

    private static final class DeliveryDeterministicModel implements Model {

        @Override
        public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools) {
            return converse(messages, tools, null, null);
        }

        @Override
        public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools, String systemPrompt) {
            return converse(messages, tools, systemPrompt, null);
        }

        @Override
        public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools, String systemPrompt, ToolSelection toolSelection) {
            Map<String, Object> courierResult = latestToolContent(messages, "courier-check");
            if (courierResult == null) {
                return List.of(
                        new ModelEvent.ToolUse("courier-check", "check_courier_availability", Map.of("itemNames", List.of())),
                        new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
            }
            Map<String, Object> trafficResult = latestToolContent(messages, "traffic-check");
            if (trafficResult == null) {
                return List.of(
                        new ModelEvent.ToolUse("traffic-check", "get_traffic_weather", Map.of()),
                        new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
            }
            Map<String, Object> discoveryResult = latestToolContent(messages, "eta-discovery");
            if (discoveryResult == null) {
                return List.of(
                        new ModelEvent.ToolUse("eta-discovery", "discover_eta_services", Map.of(
                                "query", "外部ETAを提供するサービスは？",
                                "availableOnly", true)),
                        new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
            }
            List<Map<String, Object>> discoveredServices = mapList(discoveryResult.get("services"));
            for (int index = 0; index < discoveredServices.size(); index++) {
                String toolUseId = "eta-call-" + index;
                if (latestToolContent(messages, toolUseId) == null) {
                    Map<String, Object> service = discoveredServices.get(index);
                    return List.of(
                            new ModelEvent.ToolUse(toolUseId, "call_eta_service", Map.of(
                                    "serviceName", String.valueOf(service.getOrDefault("serviceName", "external-service")),
                                    "url", String.valueOf(service.getOrDefault("url", "")),
                                    "itemNames", List.of(),
                                    "context", latestUserText(messages))),
                            new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
                }
            }
            boolean expressAvailable = Boolean.parseBoolean(String.valueOf(courierResult.getOrDefault("expressAvailable", "true")));
            String trafficLevel = String.valueOf(trafficResult.getOrDefault("trafficLevel", "clear"));
            String weather = String.valueOf(trafficResult.getOrDefault("weather", "clear"));
            int trafficDelay = Integer.parseInt(String.valueOf(trafficResult.getOrDefault("trafficDelayMinutes", "0")));
            int weatherDelay = Integer.parseInt(String.valueOf(trafficResult.getOrDefault("weatherDelayMinutes", "0")));
            int expressEta = 15 + trafficDelay / 2 + weatherDelay / 2;
            List<DeliveryOption> options = new ArrayList<>();
            if (expressAvailable) {
                options.add(new DeliveryOption("express", "自社エクスプレス", expressEta, new BigDecimal("300.00")));
            }
            List<String> externalLines = new ArrayList<>();
            for (int index = 0; index < discoveredServices.size(); index++) {
                Map<String, Object> service = discoveredServices.get(index);
                Map<String, Object> etaResult = latestToolContent(messages, "eta-call-" + index);
                if (etaResult == null || !"AVAILABLE".equalsIgnoreCase(String.valueOf(etaResult.getOrDefault("status", "NOT_AVAILABLE")))) {
                    continue;
                }
                String serviceName = String.valueOf(service.getOrDefault("serviceName", "external-service"));
                DeliveryOption option = switch (serviceName) {
                    case "hermes-adapter" -> new DeliveryOption(
                            "hermes",
                            "Hermes スピード便",
                            Integer.parseInt(String.valueOf(etaResult.getOrDefault("etaMinutes", "22"))),
                            new BigDecimal(String.valueOf(etaResult.getOrDefault("fee", "350.00"))));
                    case "idaten-adapter" -> new DeliveryOption(
                            "idaten",
                            "Idaten エコノミー",
                            Integer.parseInt(String.valueOf(etaResult.getOrDefault("etaMinutes", "34"))),
                            new BigDecimal(String.valueOf(etaResult.getOrDefault("fee", "180.00"))));
                    default -> new DeliveryOption(
                            serviceName,
                            serviceName,
                            Integer.parseInt(String.valueOf(etaResult.getOrDefault("etaMinutes", "30"))),
                            new BigDecimal(String.valueOf(etaResult.getOrDefault("fee", "250.00"))));
                };
                options.add(option);
                externalLines.add("✓ " + option.label() + " API 呼び出し -> ETA " + option.etaMinutes()
                        + "分、料金 ¥" + option.fee().stripTrailingZeros().toPlainString()
                        + "、混雑:" + etaResult.getOrDefault("congestion", "unknown"));
            }
            DeliveryRanking ranking = DeliveryRankingPolicy.rank(options, latestUserText(messages));
            String discoverLine = discoveredServices.isEmpty()
                    ? "✓ registry discovery -> 利用可能な外部 ETA サービスは見つかりませんでした"
                    : "✓ registry discovery -> " + discoveredServices.stream()
                            .map(service -> String.valueOf(service.getOrDefault("serviceName", "external-service")))
                            .reduce((left, right) -> left + ", " + right)
                            .orElse("")
                    + " を発見";
            String courierLine = expressAvailable
                    ? "✓ 自社スタッフ確認 -> 自社エクスプレス利用可能、ETA " + expressEta + "分"
                    : "✓ 自社スタッフ確認 -> 自社エクスプレスは現在利用不可";
            String trafficLine = "✓ 交通・天候確認 -> " + trafficLevel + " / " + weather
                    + "、遅延+" + (trafficDelay + weatherDelay) + "分";
            String recommendationLine = ranking.recommendedTier().isBlank()
                    ? "→ 推奨候補を提示できませんでした"
                    : "→ 推奨: " + ranking.options().get(0).label() + "。理由: " + ranking.recommendationReason();
            List<String> lines = new ArrayList<>();
            lines.add(courierLine);
            lines.add(trafficLine);
            lines.add(discoverLine);
            lines.addAll(externalLines);
            lines.add(recommendationLine);
            String summary = String.join("\n", lines);
            return List.of(
                    new ModelEvent.TextDelta(summary),
                    new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
        }

        private List<Map<String, Object>> mapList(Object value) {
            if (value instanceof List<?> list) {
                List<Map<String, Object>> results = new ArrayList<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> map) {
                        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
                        map.forEach((key, element) -> values.put(String.valueOf(key), element));
                        results.add(values);
                    }
                }
                return results;
            }
            return List.of();
        }

        private String latestUserText(List<Message> messages) {
            for (int index = messages.size() - 1; index >= 0; index--) {
                Message message = messages.get(index);
                for (ContentBlock block : message.content()) {
                    if (block instanceof ContentBlock.Text text) {
                        return text.text();
                    }
                }
            }
            return "";
        }

        private Map<String, Object> latestToolContent(List<Message> messages, String toolUseId) {
            for (int index = messages.size() - 1; index >= 0; index--) {
                Message message = messages.get(index);
                for (ContentBlock block : message.content()) {
                    if (block instanceof ContentBlock.ToolResult result
                            && toolUseId.equals(result.toolUseId())
                            && result.content() instanceof Map<?, ?> content) {
                        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
                        content.forEach((key, value) -> values.put(String.valueOf(key), value));
                        return values;
                    }
                }
            }
            return null;
        }
    }
}