package com.mahitotsu.arachne.samples.delivery.kitchenservice;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
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
    private final MenuSubstitutionGateway menuSubstitutionGateway;

    KitchenApplicationService(
            AgentFactory agentFactory,
            KitchenRepository repository,
            @Qualifier("kitchenLookupTool") Tool kitchenLookupTool,
            MenuSubstitutionGateway menuSubstitutionGateway) {
        this.agentFactory = agentFactory;
        this.repository = repository;
        this.kitchenLookupTool = kitchenLookupTool;
        this.menuSubstitutionGateway = menuSubstitutionGateway;
    }

    KitchenCheckResponse check(KitchenCheckRequest request) {
        List<KitchenItemStatus> statuses = repository.check(request.itemIds());
        AtomicReference<Map<String, KitchenItemStatus>> approvedSubstitutions = new AtomicReference<>(Map.of());
        AtomicReference<List<AgentCollaboration>> collaborations = new AtomicReference<>(List.of());
        String summary = agentFactory.builder()
                .systemPrompt("""
                You are the kitchen-agent for the app's only cloud kitchen.

                There is no alternate branch and no alternate kitchen to hand work to.
                If the kitchen cannot serve an item, ask menu-agent for same-brand substitutes instead.

                        First call kitchen_inventory_lookup to inspect stock and prep timing.
                        If any requested item is unavailable, call menu_substitution_lookup so menu-agent can
                        propose fallback items. Approve only substitutes that your own kitchen line can actually serve.
                        Explain the final decision in one short paragraph.
                        """)
                .tools(kitchenLookupTool, buildMenuSubstitutionTool(request, approvedSubstitutions, collaborations))
                .build()
                .run("items=" + String.join(",", request.itemIds()) + "\nmessage=" + request.message())
                .text();
        List<KitchenItemStatus> resolvedStatuses = applySubstitutions(statuses, approvedSubstitutions.get());
        int readyInMinutes = resolvedStatuses.stream().mapToInt(KitchenItemStatus::prepMinutes).max().orElse(12);
        return new KitchenCheckResponse(
                "kitchen-service",
                "kitchen-agent",
                repository.headline(resolvedStatuses),
                summary,
                readyInMinutes,
                resolvedStatuses,
                collaborations.get());
    }

    private Tool buildMenuSubstitutionTool(
            KitchenCheckRequest request,
            AtomicReference<Map<String, KitchenItemStatus>> approvedSubstitutions,
            AtomicReference<List<AgentCollaboration>> collaborations) {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec(
                        "menu_substitution_lookup",
                        "Ask menu-agent for fallback items for the unavailable requests, then let kitchen-agent approve them.",
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
                            new MenuSubstitutionRequest(request.sessionId(), customerMessage, unavailableItemId));
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
                                ? "no approved substitutes"
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

    private static final Map<String, KitchenItemStatus> STOCK = Map.of(
            "combo-crispy", new KitchenItemStatus("combo-crispy", true, 14, null, null, null),
            "combo-smash", new KitchenItemStatus("combo-smash", true, 16, null, null, null),
            "combo-kids", new KitchenItemStatus("combo-kids", true, 9, null, null, null),
            "side-fries", new KitchenItemStatus("side-fries", false, 7, null, null, null),
            "side-nuggets", new KitchenItemStatus("side-nuggets", true, 8, null, null, null),
            "drink-lemon", new KitchenItemStatus("drink-lemon", true, 3, null, null, null),
            "drink-latte", new KitchenItemStatus("drink-latte", true, 4, null, null, null),
            "wrap-garden", new KitchenItemStatus("wrap-garden", true, 11, null, null, null));

    List<KitchenItemStatus> check(List<String> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return List.of(new KitchenItemStatus("combo-crispy", true, 14, null, null, null));
        }
        return itemIds.stream()
                .map(itemId -> STOCK.getOrDefault(itemId, new KitchenItemStatus(itemId, true, 12, null, null, null)))
                .toList();
    }

    String headline(List<KitchenItemStatus> statuses) {
        long unavailable = statuses.stream().filter(status -> !status.available()).count();
        if (unavailable == 0) {
            return "kitchen-agent cleared the whole draft";
        }
        return "kitchen-agent found " + unavailable + " substitution point";
    }

    String describe(List<String> itemIds) {
        List<KitchenItemStatus> statuses = check(itemIds);
        return describeStatuses(statuses);
    }

    String describeStatuses(List<KitchenItemStatus> statuses) {
        return statuses.stream()
                .map(status -> status.available()
                        ? status.itemId() + " ready in " + status.prepMinutes() + " min"
                        : status.itemId() + " is unavailable and needs a substitute")
                .reduce((left, right) -> left + "; " + right)
                .orElse("kitchen is ready");
    }
}

@Component
class MenuSubstitutionGateway {

    private final RestClient restClient;

    MenuSubstitutionGateway(@Qualifier("menuRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    MenuSubstitutionResponse suggestSubstitutes(MenuSubstitutionRequest request) {
        return Objects.requireNonNull(restClient.post()
                .uri("/internal/menu/substitutes")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(MenuSubstitutionResponse.class));
    }
}

@Configuration
class KitchenArachneConfiguration {

    @Bean
    Tool kitchenLookupTool(KitchenRepository repository) {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec("kitchen_inventory_lookup", "Inspect stock pressure and prep timing for the selected items.", schema());
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
            if (substitutionContent != null) {
            return List.of(
                new ModelEvent.TextDelta("kitchen-agent checked the line and found: "
                    + inventorySummary
                    + ". It consulted menu-agent and approved "
                    + substitutionContent.getOrDefault("substitutionSummary", "the closest substitute")
                    + "."),
                new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
            }
            return List.of(
                new ModelEvent.TextDelta("kitchen-agent checked the line and found: " + inventorySummary + "."),
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

record AgentCollaboration(String service, String agent, String headline, String summary) {}

record MenuSubstitutionRequest(String sessionId, String message, String unavailableItemId) {}

record MenuSubstitutionResponse(String service, String agent, String headline, String summary, List<MenuItem> items) {}

record MenuItem(String id, String name, String description, BigDecimal price, int suggestedQuantity) {}