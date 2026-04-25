package com.mahitotsu.arachne.samples.delivery.kitchenservice;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

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
public class KitchenServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(KitchenServiceApplication.class, args);
    }

    @Bean
    SecurityFilterChain kitchenSecurity(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
                .build();
    }

    @Bean("menuRestClient")
    RestClient menuRestClient(@Value("${MENU_SERVICE_BASE_URL:http://localhost:8081}") String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).build();
    }
}

@RestController
@RequestMapping(path = "/internal/kitchen", produces = MediaType.APPLICATION_JSON_VALUE)
class KitchenController {

    private final KitchenApplicationService applicationService;

    KitchenController(KitchenApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/check")
    KitchenCheckResponse check(@RequestBody KitchenCheckRequest request) {
        return applicationService.check(request);
    }
}

@Service
class KitchenApplicationService {

    private final AgentFactory agentFactory;
    private final KitchenRepository repository;
    private final Tool kitchenLookupTool;
    private final Tool prepSchedulerTool;
    private final MenuSubstitutionGateway menuSubstitutionGateway;

    KitchenApplicationService(
            AgentFactory agentFactory,
            KitchenRepository repository,
            @Qualifier("kitchenLookupTool") Tool kitchenLookupTool,
            @Qualifier("prepSchedulerTool") Tool prepSchedulerTool,
            MenuSubstitutionGateway menuSubstitutionGateway) {
        this.agentFactory = agentFactory;
        this.repository = repository;
        this.kitchenLookupTool = kitchenLookupTool;
        this.prepSchedulerTool = prepSchedulerTool;
        this.menuSubstitutionGateway = menuSubstitutionGateway;
    }

    KitchenCheckResponse check(KitchenCheckRequest request) {
        String accessToken = SecurityAccessors.requiredAccessToken();
        List<KitchenItemStatus> statuses = repository.check(request.itemIds());
        AtomicReference<Map<String, KitchenItemStatus>> approvedSubstitutions = new AtomicReference<>(Map.of());
        AtomicReference<List<AgentCollaboration>> collaborations = new AtomicReference<>(List.of());
        String summary = agentFactory.builder()
                .systemPrompt("""
                あなたはこのアプリ唯一のクラウドキッチンの kitchen-agent です。

                代替の支店も代替のキッチンも存在しません。
                キッチンがアイテムを提供できない場合は、他のキッチンではなく menu-agent に同ブランドの代替品を尋ねてください。

                        まず kitchen_inventory_lookup を呼び出して在庫と調理時間を確認してください。
                        続けて prep_scheduler を呼び、ライン別のキュー遅延と提供見込み時間を確認してください。
                        リクエストされたアイテムが在庫切れの場合は、menu_substitution_lookup を呼び出して menu-agent に代替品の候補を提案させてください。
                        自分のキッチンラインで実際に対応できる代替品のみを承認してください。
                        grill-line の遅延が 15 分を超えるときは、assembly 系（例: サーモン丼）に切り替えると早く出せることを能動的に伝えてください。
                        最終的な判断を短い段落で説明してください。
                        """)
                    .tools(kitchenLookupTool, prepSchedulerTool,
                        buildMenuSubstitutionTool(request, accessToken, approvedSubstitutions, collaborations))
                .build()
                .run("items=" + String.join(",", request.itemIds()) + "\nmessage=" + request.message())
                .text();
        List<KitchenItemStatus> resolvedStatuses = applySubstitutions(statuses, approvedSubstitutions.get());
                PrepSchedule schedule = repository.schedule(resolvedStatuses.stream()
                    .map(status -> status.substituteItemId() != null ? status.substituteItemId() : status.itemId())
                    .toList());
        return new KitchenCheckResponse(
                "kitchen-service",
                "kitchen-agent",
                repository.headline(resolvedStatuses),
                summary,
                    schedule.readyInMinutes(),
                resolvedStatuses,
                collaborations.get());
    }

    private Tool buildMenuSubstitutionTool(
            KitchenCheckRequest request,
            String accessToken,
            AtomicReference<Map<String, KitchenItemStatus>> approvedSubstitutions,
            AtomicReference<List<AgentCollaboration>> collaborations) {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec(
                        "menu_substitution_lookup",
                        "利用不可なリクエストに対して menu-agent に代替品を尋ね、その後 kitchen-agent が承認する。",
                        substitutionSchema());
            }

            @Override
            public ToolResult invoke(Object input) {
                return invoke(input, new ToolInvocationContext("menu_substitution_lookup", null, input, null));
            }

            @Override
            public ToolResult invoke(Object input, ToolInvocationContext context) {
                Map<String, Object> inputValues = inputValues(input);
                List<String> unavailableItemIds = stringList(inputValues.get("unavailableItemIds"));
                String customerMessage = String.valueOf(inputValues.getOrDefault("customerMessage", request.message()));

                LinkedHashMap<String, KitchenItemStatus> approved = new LinkedHashMap<>();
                ArrayList<AgentCollaboration> collaboratorEntries = new ArrayList<>();
                ArrayList<String> approvedNames = new ArrayList<>();

                for (String unavailableItemId : unavailableItemIds) {
                    MenuSubstitutionResponse response = menuSubstitutionGateway.suggestSubstitutes(
                        new MenuSubstitutionRequest(request.sessionId(), customerMessage, unavailableItemId), accessToken);
                    collaboratorEntries.add(new AgentCollaboration(
                            "menu-service/support",
                            response.agent(),
                            response.headline(),
                            response.summary()));
                    approveSubstitute(unavailableItemId, response.items()).ifPresent(status -> {
                        approved.put(unavailableItemId, status);
                        approvedNames.add(status.substituteName());
                    });
                }

                approvedSubstitutions.set(Map.copyOf(approved));
                collaborations.set(List.copyOf(collaboratorEntries));
                return ToolResult.success(context.toolUseId(), Map.of(
                        "substitutionSummary", approvedNames.isEmpty()
                                ? "承認された代替品はありません"
                                : String.join(", ", approvedNames)));
            }
        };
    }

    private static ObjectNode substitutionSchema() {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("type", "object");
        ObjectNode properties = root.putObject("properties");
        ObjectNode unavailableItemIds = properties.putObject("unavailableItemIds");
        unavailableItemIds.put("type", "array");
        unavailableItemIds.putObject("items").put("type", "string");
        properties.putObject("customerMessage").put("type", "string");
        root.putArray("required").add("unavailableItemIds").add("customerMessage");
        root.put("additionalProperties", false);
        return root;
    }

    private static Map<String, Object> inputValues(Object input) {
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

    private List<KitchenItemStatus> applySubstitutions(
            List<KitchenItemStatus> statuses,
            Map<String, KitchenItemStatus> approvedSubstitutions) {
        return statuses.stream()
                .map(status -> approvedSubstitutions.getOrDefault(status.itemId(), status))
                .toList();
    }

    private java.util.Optional<KitchenItemStatus> approveSubstitute(String unavailableItemId, List<MenuItem> candidates) {
        for (MenuItem candidate : candidates) {
            KitchenItemStatus availability = repository.check(List.of(candidate.id())).getFirst();
            if (availability.available()) {
                return java.util.Optional.of(new KitchenItemStatus(
                        unavailableItemId,
                        false,
                        availability.prepMinutes(),
                        candidate.id(),
                        candidate.name(),
                        candidate.price()));
            }
        }
        return java.util.Optional.empty();
    }
}

@Component
class KitchenRepository {

    private static final Map<String, KitchenStockState> STOCK = Map.ofEntries(
            Map.entry("combo-crispy", stock(true, 14, "fry")),
            Map.entry("combo-smash", stock(true, 16, "grill")),
            Map.entry("combo-kids", stock(true, 9, "assembly")),
            Map.entry("combo-teriyaki", stock(true, 14, "grill")),
            Map.entry("combo-spicy-tuna", stock(true, 12, "assembly")),
            Map.entry("side-fries", stock(false, 7, "fry")),
            Map.entry("side-nuggets", stock(true, 8, "fry")),
            Map.entry("side-onion-rings", stock(true, 7, "fry")),
            Map.entry("drink-lemon", stock(true, 3, "drink")),
            Map.entry("drink-latte", stock(true, 4, "drink")),
            Map.entry("drink-matcha-latte", stock(true, 5, "drink")),
            Map.entry("wrap-garden", stock(true, 11, "assembly")),
            Map.entry("bowl-salmon", stock(true, 10, "assembly")),
            Map.entry("bowl-veggie", stock(true, 8, "assembly")),
            Map.entry("dessert-choco", stock(true, 6, "assembly")),
            Map.entry("dessert-matcha", stock(true, 4, "assembly")));

    private final ConcurrentMap<String, Integer> queueDepthByLine = new ConcurrentHashMap<>();

    List<KitchenItemStatus> check(List<String> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return List.of(toStatus("combo-crispy", STOCK.get("combo-crispy")));
        }
        return itemIds.stream()
                .map(itemId -> toStatus(itemId, STOCK.getOrDefault(itemId, stock(true, 12, "assembly"))))
                .toList();
    }

    PrepSchedule schedule(List<String> itemIds) {
        Map<String, Integer> maxPrepByLine = new LinkedHashMap<>();
        for (String itemId : itemIds) {
            KitchenStockState state = STOCK.getOrDefault(itemId, stock(true, 12, "assembly"));
            maxPrepByLine.merge(state.lineType(), state.prepMinutes(), Math::max);
        }
        if (maxPrepByLine.isEmpty()) {
            return new PrepSchedule(12, "assembly", "現在の負荷は軽く、通常どおり準備できます。", null);
        }

        int readyInMinutes = 0;
        String slowestLine = "assembly";
        for (Map.Entry<String, Integer> entry : maxPrepByLine.entrySet()) {
            int delay = queueDelayMinutes(entry.getKey());
            int candidateEta = entry.getValue() + delay;
            if (candidateEta >= readyInMinutes) {
                readyInMinutes = candidateEta;
                slowestLine = entry.getKey();
            }
        }

        String summary = slowestLine + "-line の見込みは約" + readyInMinutes + "分です。";
        String alternative = null;
        if ("grill".equals(slowestLine) && queueDelayMinutes("grill") > 15) {
            int assemblyEta = assemblyEtaMinutes();
            alternative = "現在 grill-line が混雑中です。assembly 系（サーモン丼など）であれば今すぐ約"
                    + assemblyEta + "分で提供できます。";
            summary += " " + alternative;
        }
        return new PrepSchedule(readyInMinutes, slowestLine, summary, alternative);
    }

    void setQueueDepth(String lineType, int depth) {
        if (depth <= 0) {
            queueDepthByLine.remove(lineType);
            return;
        }
        queueDepthByLine.put(lineType, depth);
    }

    void clearQueueDepths() {
        queueDepthByLine.clear();
    }

    String headline(List<KitchenItemStatus> statuses) {
        long unavailable = statuses.stream().filter(status -> !status.available()).count();
        if (unavailable == 0) {
            return "kitchen-agent が全アイテムの在庫を確認しました";
        }
        return "kitchen-agent が " + unavailable + " 件の代替点を検出しました";
    }

    String describe(List<String> itemIds) {
        List<KitchenItemStatus> statuses = check(itemIds);
        return describeStatuses(statuses);
    }

    String describeSchedule(List<String> itemIds) {
        return schedule(itemIds).summary();
    }

    String describeStatuses(List<KitchenItemStatus> statuses) {
        return statuses.stream()
                .map(status -> status.available()
                        ? status.itemId() + " は約" + status.prepMinutes() + "分で準備できます"
                        : status.itemId() + " は在庫切れのため代替品が必要です")
                .reduce((left, right) -> left + "; " + right)
                .orElse("キッチンは準備完了です");
    }

    private static KitchenStockState stock(boolean available, int prepMinutes, String lineType) {
        return new KitchenStockState(available, prepMinutes, lineType);
    }

    private static KitchenItemStatus toStatus(String itemId, KitchenStockState state) {
        return new KitchenItemStatus(itemId, state.available(), state.prepMinutes(), null, null, null);
    }

    private int queueDelayMinutes(String lineType) {
        return queueDepthByLine.getOrDefault(lineType, 0) * 4;
    }

    private int assemblyEtaMinutes() {
        int maxPrep = STOCK.values().stream()
                .filter(state -> "assembly".equals(state.lineType()))
                .mapToInt(KitchenStockState::prepMinutes)
                .max()
                .orElse(10);
        return maxPrep + queueDelayMinutes("assembly");
    }
}

@Component
class MenuSubstitutionGateway {

    private final RestClient restClient;

    MenuSubstitutionGateway(@Qualifier("menuRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    MenuSubstitutionResponse suggestSubstitutes(MenuSubstitutionRequest request, String accessToken) {
        return Objects.requireNonNull(restClient.post()
                .uri("/internal/menu/substitutes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(MenuSubstitutionResponse.class));
    }
}

final class SecurityAccessors {

    private SecurityAccessors() {
    }

    static String requiredAccessToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            return jwtAuthenticationToken.getToken().getTokenValue();
        }
        throw new IllegalStateException("No access token is available in the security context");
    }
}

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

    private static ObjectNode substitutionSchema() {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("type", "object");
        ObjectNode properties = root.putObject("properties");
        properties.putObject("unavailableItemIds").put("type", "array").putObject("items").put("type", "string");
        properties.putObject("customerMessage").put("type", "string");
        root.putArray("required").add("unavailableItemIds").add("customerMessage");
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

    private static final class KitchenDeterministicModel implements Model {

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
            Map<String, String> requestArgs = latestRequestArgs(messages);
            Map<String, Object> toolContent = latestToolContent(messages, "kitchen-lookup");
            if (toolContent == null) {
                return List.of(
                new ModelEvent.ToolUse(
                    "kitchen-lookup",
                    "kitchen_inventory_lookup",
                    Map.of("items", requestArgs.getOrDefault("items", ""))),
                        new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
            }

                    Map<String, Object> scheduleContent = latestToolContent(messages, "prep-scheduler");
                    if (scheduleContent == null) {
                    return List.of(
                        new ModelEvent.ToolUse(
                            "prep-scheduler",
                            "prep_scheduler",
                            Map.of("items", requestArgs.getOrDefault("items", ""))),
                        new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
                    }

            List<String> unavailableItemIds = stringList(toolContent.get("unavailableItemIds"));
            Map<String, Object> substitutionContent = latestToolContent(messages, "menu-substitution-lookup");
            if (!unavailableItemIds.isEmpty() && substitutionContent == null) {
            return List.of(
                new ModelEvent.ToolUse(
                    "menu-substitution-lookup",
                    "menu_substitution_lookup",
                    Map.of(
                        "unavailableItemIds", unavailableItemIds,
                        "customerMessage", requestArgs.getOrDefault("message", ""))),
                new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
            }

            String inventorySummary = String.valueOf(toolContent.getOrDefault("inventorySummary", "kitchen is ready"));
            String scheduleSummary = String.valueOf(scheduleContent.getOrDefault("scheduleSummary", ""));
            if (substitutionContent != null) {
            return List.of(
                new ModelEvent.TextDelta("kitchen-agent がラインを確認しました: "
                    + inventorySummary
                    + " " + scheduleSummary
                    + ". menu-agent に相談して "
                    + substitutionContent.getOrDefault("substitutionSummary", "最適な代替品")
                    + " を承認しました。"),
                new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
            }
            return List.of(
                new ModelEvent.TextDelta("kitchen-agent がラインを確認しました: " + inventorySummary + " " + scheduleSummary),
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
            return "";
        }

        private Map<String, String> latestRequestArgs(List<Message> messages) {
            LinkedHashMap<String, String> values = new LinkedHashMap<>();
            for (String line : latestUserText(messages).split("\\R")) {
                int separator = line.indexOf('=');
                if (separator <= 0) {
                    continue;
                }
                values.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
            }
            return values;
        }
    }
}

record KitchenCheckRequest(String sessionId, String message, List<String> itemIds) {}

record KitchenCheckResponse(
        String service,
        String agent,
        String headline,
        String summary,
        int readyInMinutes,
        List<KitchenItemStatus> items,
        List<AgentCollaboration> collaborations) {}

record KitchenItemStatus(String itemId, boolean available, int prepMinutes, String substituteItemId, String substituteName, BigDecimal substitutePrice) {}

record KitchenStockState(boolean available, int prepMinutes, String lineType) {}

record PrepSchedule(int readyInMinutes, String bottleneckLine, String summary, String alternativeSuggestion) {}

record AgentCollaboration(String service, String agent, String headline, String summary) {}

record MenuSubstitutionRequest(String sessionId, String message, String unavailableItemId) {}

record MenuSubstitutionResponse(String service, String agent, String headline, String summary, List<MenuItem> items) {}

record MenuItem(String id, String name, String description, BigDecimal price, int suggestedQuantity) {}