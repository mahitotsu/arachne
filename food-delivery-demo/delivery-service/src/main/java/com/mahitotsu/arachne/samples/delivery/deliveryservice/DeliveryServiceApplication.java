package com.mahitotsu.arachne.samples.delivery.deliveryservice;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
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
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.ObjectMapper;
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

    @Bean
    ApplicationRunner registerDeliveryService(
            RestClient.Builder restClientBuilder,
            @Value("${DELIVERY_REGISTRY_BASE_URL:}") String registryBaseUrl,
            @Value("${DELIVERY_DELIVERY_ENDPOINT:http://delivery-service:8080}") String serviceEndpoint) {
        return args -> {
            if (registryBaseUrl.isBlank()) {
                return;
            }
            restClientBuilder.baseUrl(registryBaseUrl).build().post()
                    .uri("/registry/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "serviceName", "delivery-service",
                            "endpoint", serviceEndpoint,
                            "capability", "配送見積もり、自社スタッフの可用性確認、交通と天候を踏まえた ETA 評価を扱う。",
                            "agentName", "delivery-agent",
                            "systemPrompt", "配送レーンの可用性と ETA を比較して要約する。",
                            "skills", List.of(Map.of("name", "delivery-routing", "content", "配送レーン比較と ETA 評価")),
                            "requestMethod", "POST",
                            "requestPath", "/internal/delivery/quote",
                            "healthEndpoint", serviceEndpoint + "/actuator/health",
                            "status", "AVAILABLE"))
                    .retrieve()
                    .toBodilessEntity();
        };
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

    private static final String ETA_DISCOVERY_QUERY = "外部ETAを提供するサービスは？";

    private final AgentFactory agentFactory;
    private final CourierAvailabilityRepository courierRepo;
    private final TrafficWeatherRepository trafficRepo;
    private final EtaServiceDiscoveryClient etaDiscoveryClient;
    private final ExternalEtaServiceClient externalEtaServiceClient;
    private final Tool courierAvailabilityTool;
    private final Tool trafficWeatherTool;
    private final Tool discoverEtaServicesTool;
    private final Tool callEtaServiceTool;

    DeliveryApplicationService(
            AgentFactory agentFactory,
            CourierAvailabilityRepository courierRepo,
            TrafficWeatherRepository trafficRepo,
            Tool courierAvailabilityTool,
            Tool trafficWeatherTool,
            Tool discoverEtaServicesTool,
            Tool callEtaServiceTool,
            EtaServiceDiscoveryClient etaDiscoveryClient,
            ExternalEtaServiceClient externalEtaServiceClient) {
        this.agentFactory = agentFactory;
        this.courierRepo = courierRepo;
        this.trafficRepo = trafficRepo;
        this.courierAvailabilityTool = courierAvailabilityTool;
        this.trafficWeatherTool = trafficWeatherTool;
        this.discoverEtaServicesTool = discoverEtaServicesTool;
        this.callEtaServiceTool = callEtaServiceTool;
        this.etaDiscoveryClient = etaDiscoveryClient;
        this.externalEtaServiceClient = externalEtaServiceClient;
    }

    DeliveryQuoteResponse quote(DeliveryQuoteRequest request) {
        CourierStatus courierStatus = courierRepo.check(request.itemNames());
        TrafficWeatherStatus conditions = trafficRepo.current();
        List<EtaServiceTarget> etaServices = etaDiscoveryClient.discoverAvailableEtaServices(ETA_DISCOVERY_QUERY);
        List<ExternalEtaQuote> externalQuotes = etaServices.stream()
                .map(service -> externalEtaServiceClient.quote(service, request.itemNames(), request.message()))
                .flatMap(Optional::stream)
                .toList();
        List<DeliveryOption> candidateOptions = buildCandidateOptions(courierStatus, conditions, externalQuotes);
        DeliveryRanking ranking = DeliveryRankingPolicy.rank(candidateOptions, request.message());

        String prompt = """
                あなたは単一キッチンのクラウドキッチンアプリの delivery-agent です。
                必ず次の順でツールを使ってください:
                1. check_courier_availability
                2. get_traffic_weather
                3. discover_eta_services
                4. discover で返った各 AVAILABLE サービスに対して call_eta_service
                回答は日本語で、次のトレースを簡潔に書いてください:
                - 自社スタッフ確認
                - 交通・天候確認
                - registry discovery の結果
                - 各外部 ETA API 呼び出し結果
                - 推奨オプションと理由
                「急いで」「最速」は最短 ETA を優先し、「安く」「節約」は最安料金を優先してください。
                """;

        String summary = agentFactory.builder()
                .systemPrompt(prompt)
                .tools(courierAvailabilityTool, trafficWeatherTool, discoverEtaServicesTool, callEtaServiceTool)
                .build()
                .run("注文の配送状況を調査してください: " + request.message()
                        + "。アイテム: " + request.itemNames()
                        + "。推奨候補: " + ranking.recommendedTier()
                        + "。推奨理由: " + ranking.recommendationReason())
                .text();

        String headline = ranking.recommendedTier().isBlank()
                ? "delivery-agent が利用可能な配送候補を確認できませんでした"
                : "delivery-agent が " + labelFor(ranking.recommendedTier(), ranking.options()) + " を推奨しました";

        return new DeliveryQuoteResponse(
                "delivery-service",
                "delivery-agent",
                headline,
                summary,
                ranking.options(),
                ranking.recommendedTier(),
                ranking.recommendationReason());
    }

    private List<DeliveryOption> buildCandidateOptions(
            CourierStatus courierStatus,
            TrafficWeatherStatus conditions,
            List<ExternalEtaQuote> externalQuotes) {
        int trafficDelay = conditions.trafficDelayMinutes();
        int weatherDelay = conditions.weatherDelayMinutes();
        int expressBaseEta = 15 + trafficDelay / 2 + weatherDelay / 2;

        List<DeliveryOption> options = new ArrayList<>();
        if (courierStatus.expressAvailable()) {
            options.add(new DeliveryOption("express", "自社エクスプレス", expressBaseEta, new BigDecimal("300.00")));
        }
        for (ExternalEtaQuote quote : externalQuotes) {
            options.add(new DeliveryOption(
                    externalCode(quote.serviceName()),
                    externalLabel(quote.serviceName()),
                    quote.etaMinutes(),
                    quote.fee()));
        }
        return options;
    }

    private String externalCode(String serviceName) {
        return switch (serviceName) {
            case "hermes-adapter" -> "hermes";
            case "idaten-adapter" -> "idaten";
            default -> serviceName.replace("-adapter", "");
        };
    }

    private String externalLabel(String serviceName) {
        return switch (serviceName) {
            case "hermes-adapter" -> "Hermes スピード便";
            case "idaten-adapter" -> "Idaten エコノミー";
            default -> serviceName;
        };
    }

    private String labelFor(String code, List<DeliveryOption> options) {
        return options.stream()
                .filter(option -> option.code().equals(code))
                .map(DeliveryOption::label)
                .findFirst()
                .orElse(code);
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

@Component
class EtaServiceDiscoveryClient {

    private final RestClient restClient;

    EtaServiceDiscoveryClient(
            RestClient.Builder restClientBuilder,
            @Value("${DELIVERY_REGISTRY_BASE_URL:}") String registryBaseUrl) {
        this.restClient = registryBaseUrl.isBlank() ? null : restClientBuilder.baseUrl(registryBaseUrl).build();
    }

    List<EtaServiceTarget> discoverAvailableEtaServices(String query) {
        if (restClient == null) {
            return List.of();
        }
        RegistryDiscoverResponsePayload response = restClient.post()
                .uri("/registry/discover")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RegistryDiscoverRequestPayload(query, true))
                .retrieve()
                .body(RegistryDiscoverResponsePayload.class);
        if (response == null || response.matches() == null) {
            return List.of();
        }
        return response.matches().stream()
                .filter(Objects::nonNull)
                .filter(match -> "AVAILABLE".equalsIgnoreCase(match.status()))
                .map(match -> new EtaServiceTarget(
                        match.serviceName(),
                        joinUrl(match.endpoint(), match.requestPath())))
                .filter(target -> StringUtils.hasText(target.url()))
                .toList();
    }

    private String joinUrl(String endpoint, String requestPath) {
        if (!StringUtils.hasText(endpoint)) {
            return "";
        }
        if (!StringUtils.hasText(requestPath)) {
            return endpoint;
        }
        if (requestPath.startsWith("http://") || requestPath.startsWith("https://")) {
            return requestPath;
        }
        if (endpoint.endsWith("/") && requestPath.startsWith("/")) {
            return endpoint.substring(0, endpoint.length() - 1) + requestPath;
        }
        if (!endpoint.endsWith("/") && !requestPath.startsWith("/")) {
            return endpoint + "/" + requestPath;
        }
        return endpoint + requestPath;
    }
}

@Component
class ExternalEtaServiceClient {

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    ExternalEtaServiceClient(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.restClientBuilder = restClientBuilder;
        this.objectMapper = objectMapper;
    }

    Optional<ExternalEtaQuote> quote(EtaServiceTarget service, List<String> itemNames, String context) {
        try {
            AdapterEtaResponsePayload response = restClientBuilder.build().post()
                    .uri(service.url())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new AdapterEtaRequestPayload(itemNames, context))
                    .retrieve()
                    .body(AdapterEtaResponsePayload.class);
            return toQuote(service, response);
        } catch (RestClientResponseException ex) {
            return toQuote(service, parse(ex.getResponseBodyAsString()));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private Optional<ExternalEtaQuote> toQuote(EtaServiceTarget service, AdapterEtaResponsePayload response) {
        if (response == null || !"AVAILABLE".equalsIgnoreCase(response.status())) {
            return Optional.empty();
        }
        return Optional.of(new ExternalEtaQuote(
                service.serviceName(),
                response.etaMinutes(),
                response.congestion(),
                response.fee(),
                response.note()));
    }

    private AdapterEtaResponsePayload parse(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(body, AdapterEtaResponsePayload.class);
        } catch (Exception ignored) {
            return null;
        }
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
    Tool discoverEtaServicesTool(EtaServiceDiscoveryClient client) {
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
                Map<String, Object> values = values(input);
                String query = String.valueOf(values.getOrDefault("query", "外部ETAを提供するサービスは？"));
                List<Map<String, Object>> services = client.discoverAvailableEtaServices(query).stream()
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
    Tool callEtaServiceTool(ExternalEtaServiceClient client) {
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
                Map<String, Object> values = values(input);
                EtaServiceTarget target = new EtaServiceTarget(
                        String.valueOf(values.getOrDefault("serviceName", "external-service")),
                        String.valueOf(values.getOrDefault("url", "")));
                Optional<ExternalEtaQuote> quote = client.quote(
                        target,
                        stringList(values.get("itemNames")),
                        String.valueOf(values.getOrDefault("context", "")));
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

record DeliveryQuoteRequest(String sessionId, String message, List<String> itemNames) {}

record DeliveryQuoteResponse(
        String service,
        String agent,
        String headline,
        String summary,
        List<DeliveryOption> options,
        String recommendedTier,
        String recommendationReason) {}

record DeliveryOption(String code, String label, int etaMinutes, BigDecimal fee) {}

record CourierStatus(boolean expressAvailable, int expressReadyInMinutes, int standardReadyInMinutes) {}

record TrafficWeatherStatus(String trafficLevel, String weather, int trafficDelayMinutes, int weatherDelayMinutes) {}

record RegistryDiscoverRequestPayload(String query, Boolean availableOnly) {}

record RegistryDiscoverResponsePayload(String service, String agent, String summary, List<RegistryServiceMatchPayload> matches) {}

record RegistryServiceMatchPayload(
        String serviceName,
        String endpoint,
        String capability,
        String agentName,
        String systemPrompt,
        List<Map<String, Object>> skills,
        String requestMethod,
        String requestPath,
        String status) {}

record EtaServiceTarget(String serviceName, String url) {}

record AdapterEtaRequestPayload(List<String> itemNames, String context) {}

record AdapterEtaResponsePayload(String service, String status, int etaMinutes, String congestion, BigDecimal fee, String note) {}

record ExternalEtaQuote(String serviceName, int etaMinutes, String congestion, BigDecimal fee, String note) {}

record DeliveryRanking(List<DeliveryOption> options, String recommendedTier, String recommendationReason) {}

final class DeliveryRankingPolicy {

    private DeliveryRankingPolicy() {
    }

    static DeliveryRanking rank(List<DeliveryOption> options, String message) {
        List<DeliveryOption> safeOptions = options == null ? List.of() : List.copyOf(options);
        if (safeOptions.isEmpty()) {
            return new DeliveryRanking(List.of(), "", "現在利用可能な配送候補がありません。");
        }
        DeliveryPreference preference = preferenceFor(message);
        Comparator<DeliveryOption> comparator = switch (preference) {
            case CHEAP -> Comparator.comparing(DeliveryOption::fee, BigDecimal::compareTo)
                    .thenComparingInt(DeliveryOption::etaMinutes)
                    .thenComparing(DeliveryOption::code);
            case URGENT, BALANCED -> Comparator.comparingInt(DeliveryOption::etaMinutes)
                    .thenComparing(DeliveryOption::fee, BigDecimal::compareTo)
                    .thenComparing(DeliveryOption::code);
        };
        List<DeliveryOption> ranked = safeOptions.stream().sorted(comparator).toList();
        DeliveryOption best = ranked.get(0);
        String reason = switch (preference) {
            case CHEAP -> "「安く」の文脈なので最安の " + best.label() + " を優先しました。";
            case URGENT -> "「急いで」の文脈なので最短 ETA の " + best.label() + " を優先しました。";
            case BALANCED -> "現在の可用性では ETA と料金のバランスが良い " + best.label() + " を優先しました。";
        };
        return new DeliveryRanking(ranked, best.code(), reason);
    }

    private static DeliveryPreference preferenceFor(String message) {
        String normalized = Objects.requireNonNullElse(message, "").toLowerCase(Locale.ROOT);
        if (containsAny(normalized, "安く", "節約", "料金", "cheap", "budget")) {
            return DeliveryPreference.CHEAP;
        }
        if (containsAny(normalized, "急いで", "最速", "早く", "すぐ", "fast", "quick")) {
            return DeliveryPreference.URGENT;
        }
        return DeliveryPreference.BALANCED;
    }

    private static boolean containsAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}

enum DeliveryPreference {
    URGENT,
    CHEAP,
    BALANCED
}