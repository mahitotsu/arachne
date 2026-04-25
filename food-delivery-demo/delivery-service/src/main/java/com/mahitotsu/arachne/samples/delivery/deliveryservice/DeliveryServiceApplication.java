package com.mahitotsu.arachne.samples.delivery.deliveryservice;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mahitotsu.arachne.strands.model.Model;
import com.mahitotsu.arachne.strands.model.ModelEvent;
import com.mahitotsu.arachne.strands.model.ToolSelection;
import com.mahitotsu.arachne.strands.model.ToolSpec;
import com.mahitotsu.arachne.strands.spring.AgentFactory;
import com.mahitotsu.arachne.strands.tool.Tool;
import com.mahitotsu.arachne.strands.tool.ToolInvocationContext;
import com.mahitotsu.arachne.strands.tool.ToolResult;
import com.mahitotsu.arachne.strands.types.ContentBlock;
import com.mahitotsu.arachne.strands.types.Message;

@SpringBootApplication
public class DeliveryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeliveryServiceApplication.class, args);
    }

    @Bean
    SecurityFilterChain deliverySecurity(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
                .build();
    }
}

@RestController
@RequestMapping(path = "/internal/delivery", produces = MediaType.APPLICATION_JSON_VALUE)
class DeliveryController {

    private final DeliveryApplicationService applicationService;

    DeliveryController(DeliveryApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/quote")
    DeliveryQuoteResponse quote(@RequestBody DeliveryQuoteRequest request) {
        return applicationService.quote(request);
    }
}

@Service
class DeliveryApplicationService {

    private final AgentFactory agentFactory;
    private final CourierAvailabilityRepository courierRepo;
    private final TrafficWeatherRepository trafficRepo;
    private final Tool courierAvailabilityTool;
    private final Tool trafficWeatherTool;

    DeliveryApplicationService(
            AgentFactory agentFactory,
            CourierAvailabilityRepository courierRepo,
            TrafficWeatherRepository trafficRepo,
            Tool courierAvailabilityTool,
            Tool trafficWeatherTool) {
        this.agentFactory = agentFactory;
        this.courierRepo = courierRepo;
        this.trafficRepo = trafficRepo;
        this.courierAvailabilityTool = courierAvailabilityTool;
        this.trafficWeatherTool = trafficWeatherTool;
    }

    DeliveryQuoteResponse quote(DeliveryQuoteRequest request) {
        AtomicReference<CourierStatus> capturedCourier = new AtomicReference<>();
        AtomicReference<TrafficWeatherStatus> capturedConditions = new AtomicReference<>();

        // リアルタイム情報を取得し、エージェントツールが返すデータを準備する
        CourierStatus courierStatus = courierRepo.check(request.itemNames());
        TrafficWeatherStatus conditions = trafficRepo.current();
        capturedCourier.set(courierStatus);
        capturedConditions.set(conditions);

        // 実際の状況に基づいて調整後 ETA を計算する
        int trafficDelay = conditions.trafficDelayMinutes();
        int weatherDelay = conditions.weatherDelayMinutes();
        int expressBaseEta = 15 + trafficDelay / 2 + weatherDelay / 2;
        int standardBaseEta = expressBaseEta + 12 + trafficDelay + weatherDelay;

        List<DeliveryOption> options = new ArrayList<>();
        if (courierStatus.expressAvailable()) {
            options.add(new DeliveryOption("express", "自社エクスプレス", expressBaseEta, new BigDecimal("300.00")));
        }
        options.add(new DeliveryOption("standard", "パートナースタンダード", standardBaseEta, new BigDecimal("180.00")));
        options.sort(Comparator.comparingInt(DeliveryOption::etaMinutes));

        String prompt = """
                あなたは単一キッチンのクラウドキッチンアプリの delivery-agent です。
                このアプリは2種類の配送レーンのみ提供します:
                - パートナースタンダード: 外部配送パートナーが履行
                - 自社エクスプレス: 利用可能な場合はキッチン専属スタッフが履行
                check_courier_availability と get_traffic_weather ツールを使ってリアルタイムの状況を調査し、以下を網羅する簡潔なサマリーを書いてください:
                1. 自社エクスプレスの利用可能性と現在の準備状況
                2. 交通状況・天気状況と ETA への影響
                3. 利用可能な各オプション（自社エクスプレスおよび/またはパートナースタンダード）の調整後 ETA
                回答は簡潔・客観的にしてください。質問はしないでください。
                """;

        String summary = agentFactory.builder()
                .systemPrompt(prompt)
                .tools(courierAvailabilityTool, trafficWeatherTool)
                .build()
                .run("注文の配送状況を調査してください: " + request.message()
                        + "。アイテム: " + request.itemNames())
                .text();

        String headline = courierStatus.expressAvailable()
            ? "delivery-agent が自社エクスプレスとパートナースタンダードの利用可能性を確認しました"
            : "delivery-agent: 自社エクスプレスは利用不可—パートナースタンダードを見積しました";

        return new DeliveryQuoteResponse("delivery-service", "delivery-agent", headline, summary, options);
    }
}

@Component
class CourierAvailabilityRepository {

    private final String modelMode;

    CourierAvailabilityRepository(@Value("${delivery.model.mode:live}") String modelMode) {
        this.modelMode = modelMode;
    }

    CourierStatus check(List<String> itemNames) {
        int itemCount = itemNames == null ? 0 : itemNames.size();
        if ("deterministic".equals(modelMode)) {
            return new CourierStatus(true, 1 + itemCount, 3 + itemCount);
        }
        // アイテム数と時刻に基づいてクーリエの空き状況をシミュレートする
        boolean expressAvailable = (System.currentTimeMillis() / 10000) % 3 != 0; // 約1/3の時間で利用不可
        int expressReadyInMinutes = expressAvailable ? (1 + itemCount) : -1;
        int standardReadyInMinutes = 3 + itemCount;
        return new CourierStatus(expressAvailable, expressReadyInMinutes, standardReadyInMinutes);
    }
}

@Component
class TrafficWeatherRepository {

    TrafficWeatherStatus current() {
        // 交通状況と天気状況を変動させてシミュレートする
        long slot = (System.currentTimeMillis() / 30000) % 4;
        int trafficDelay = (int) (slot * 3);       // 0、3、6、9分
        int weatherDelay = slot >= 3 ? 5 : 0;      // 最悪スロットは大雨
        String trafficLevel = switch ((int) slot) {
            case 0 -> "clear";
            case 1 -> "light";
            case 2 -> "moderate";
            default -> "heavy";
        };
        String weather = slot >= 3 ? "rainy" : "clear";
        return new TrafficWeatherStatus(trafficLevel, weather, trafficDelay, weatherDelay);
    }
}

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
            // ステップ1: check_courier_availability を呼び出す
            Map<String, Object> courierResult = latestToolContent(messages, "courier-check");
            if (courierResult == null) {
                return List.of(
                        new ModelEvent.ToolUse("courier-check", "check_courier_availability", Map.of("itemNames", List.of())),
                        new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
            }
            // ステップ2: get_traffic_weather を呼び出す
            Map<String, Object> trafficResult = latestToolContent(messages, "traffic-check");
            if (trafficResult == null) {
                return List.of(
                        new ModelEvent.ToolUse("traffic-check", "get_traffic_weather", Map.of()),
                        new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
            }
            // ステップ3: サマリーを合成する
            boolean expressAvailable = Boolean.parseBoolean(String.valueOf(courierResult.getOrDefault("expressAvailable", "true")));
            String trafficLevel = String.valueOf(trafficResult.getOrDefault("trafficLevel", "clear"));
            String weather = String.valueOf(trafficResult.getOrDefault("weather", "clear"));
            int trafficDelay = Integer.parseInt(String.valueOf(trafficResult.getOrDefault("trafficDelayMinutes", "0")));
            int weatherDelay = Integer.parseInt(String.valueOf(trafficResult.getOrDefault("weatherDelayMinutes", "0")));
            int expressEta = 15 + trafficDelay / 2 + weatherDelay / 2;
            int standardEta = expressEta + 12 + trafficDelay + weatherDelay;
            String expressLine = expressAvailable
                    ? "自社エクスプレスは利用可能です (準備まで約" + courierResult.getOrDefault("expressReadyInMinutes", 2) + "分)、配送予定時間" + expressEta + "分です。"
                    : "自社エクスプレスは現在利用できません。";
            String summary = expressLine + "交通状況: " + trafficLevel + "、天気: " + weather
                    + "（合計遅延+" + (trafficDelay + weatherDelay) + "分）。"
                    + "パートナースタンダードの配送予定時間: " + standardEta + "分。";
            return List.of(
                    new ModelEvent.TextDelta(summary),
                    new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
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

record DeliveryQuoteRequest(String sessionId, String message, List<String> itemNames) {}

record DeliveryQuoteResponse(String service, String agent, String headline, String summary, List<DeliveryOption> options) {}

record DeliveryOption(String code, String label, int etaMinutes, BigDecimal fee) {}

record CourierStatus(boolean expressAvailable, int expressReadyInMinutes, int standardReadyInMinutes) {}

record TrafficWeatherStatus(String trafficLevel, String weather, int trafficDelayMinutes, int weatherDelayMinutes) {}