package com.mahitotsu.arachne.samples.delivery.menuservice;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
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
public class MenuServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MenuServiceApplication.class, args);
    }

    @Bean
    SecurityFilterChain menuSecurity(HttpSecurity http) throws Exception {
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
@RequestMapping(path = "/internal/menu", produces = MediaType.APPLICATION_JSON_VALUE)
class MenuController {

    private final MenuApplicationService applicationService;

    MenuController(MenuApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/suggest")
    MenuSuggestionResponse suggest(@RequestBody MenuSuggestionRequest request) {
        return applicationService.suggest(request);
    }

    @PostMapping("/substitutes")
    MenuSubstitutionResponse substitutes(@RequestBody MenuSubstitutionRequest request) {
        return applicationService.suggestSubstitutes(request);
    }
}

@Service
class MenuApplicationService {

    private final AgentFactory agentFactory;
    private final MenuRepository repository;
    private final Tool menuLookupTool;
    private final Tool menuSubstitutionLookupTool;

    MenuApplicationService(
            AgentFactory agentFactory,
            MenuRepository repository,
            @Qualifier("menuLookupTool") Tool menuLookupTool,
            @Qualifier("menuSubstitutionLookupTool") Tool menuSubstitutionLookupTool) {
        this.agentFactory = agentFactory;
        this.repository = repository;
        this.menuLookupTool = menuLookupTool;
        this.menuSubstitutionLookupTool = menuSubstitutionLookupTool;
    }

    MenuSuggestionResponse suggest(MenuSuggestionRequest request) {
        List<MenuItem> items = repository.search(request.message());
        String agentSummary = agentFactory.builder()
                .systemPrompt("""
                You are the menu-agent for a single-brand cloud kitchen app.

                The business has one kitchen only. Recommend items from the current menu only.
                Do not mention alternate branches, alternate kitchens, or pickup plans.

                        Use the available skills to tailor your response to the customer's situation:
                        - Activate family-order-guide when the customer mentions multiple people, children, or family.
                        - Activate proactive-recommendation when the customer is browsing without a specific item in mind.

                        After activating the relevant skill, call menu_catalog_lookup to find matching items, \
                        then explain the best matches following the skill's instructions.""")
                .tools(menuLookupTool)
                .build()
                .run("query=" + request.message())
                .text();
        String summary = renderSuggestionSummary(items, agentSummary);
        return new MenuSuggestionResponse("menu-service", "menu-agent", repository.headline(items), summary, items);
    }

    private String renderSuggestionSummary(List<MenuItem> items, String agentSummary) {
        String skillPrefix = extractSkillPrefix(agentSummary);
        String itemSummary = items.stream()
                .map(item -> item.name() + " x" + item.suggestedQuantity())
                .reduce((left, right) -> left + ", " + right)
                .orElse("today's top combos");
        String prefix = skillPrefix.isBlank() ? "" : skillPrefix + " ";
        return prefix + "menu-agent recommends " + itemSummary + ". It keeps the order aligned with the current menu.";
    }

    private String extractSkillPrefix(String agentSummary) {
        if (agentSummary == null) {
            return "";
        }
        int closingBracket = agentSummary.indexOf(']');
        if (agentSummary.startsWith("[") && closingBracket > 0) {
            return agentSummary.substring(0, closingBracket + 1);
        }
        return "";
    }

    MenuSubstitutionResponse suggestSubstitutes(MenuSubstitutionRequest request) {
        List<MenuItem> items = repository.findSubstitutes(request.unavailableItemId(), request.message());
        String summary = agentFactory.builder()
                .systemPrompt("""
                You are the menu-agent supporting kitchen-agent when an item is unavailable at the only cloud kitchen.

                        Call menu_substitution_lookup to prepare fallback items that stay close to the
                customer's apparent intent. Stay within the same brand menu and do not mention alternate kitchens.
                Keep the answer short and mention that these are
                        substitute suggestions for kitchen-agent to validate.
                        """)
                .tools(menuSubstitutionLookupTool)
                .build()
                .run("unavailableItemId=" + request.unavailableItemId() + "\ncustomerMessage=" + request.message())
                .text();
        return new MenuSubstitutionResponse(
                "menu-service",
                "menu-agent",
                repository.substitutionHeadline(items),
                summary,
                items);
    }
}

@Component
class MenuRepository {

    private static final List<MenuItem> ITEMS = List.of(
            new MenuItem("combo-crispy", "Crispy Chicken Box", "Crispy chicken, fries, and lemon soda.", new BigDecimal("980.00"), 1),
            new MenuItem("combo-smash", "Smash Burger Combo", "Double smash burger with fries and cola.", new BigDecimal("1050.00"), 1),
            new MenuItem("combo-kids", "Kids Cheeseburger Set", "Small cheeseburger, corn cup, and apple juice.", new BigDecimal("720.00"), 1),
            new MenuItem("side-fries", "Curly Fries", "Seasoned curly fries.", new BigDecimal("330.00"), 1),
            new MenuItem("side-nuggets", "Nugget Share Box", "Ten-piece nugget box with sauces.", new BigDecimal("640.00"), 1),
            new MenuItem("drink-lemon", "Lemon Soda", "Fresh lemon soda with low sweetness.", new BigDecimal("240.00"), 1),
            new MenuItem("drink-latte", "Iced Latte", "Iced latte with milk foam.", new BigDecimal("320.00"), 1),
            new MenuItem("wrap-garden", "Garden Wrap", "Fresh veggie wrap with yogurt sauce.", new BigDecimal("760.00"), 1));

    List<MenuItem> search(String query) {
        String normalized = normalize(query);
        List<MenuItem> matches = ITEMS.stream()
                .filter(item -> matches(normalized, item))
                .toList();
        if (!matches.isEmpty()) {
            return tuneQuantities(matches, query);
        }
        return tuneQuantities(List.of(ITEMS.get(0), ITEMS.get(1), ITEMS.get(3), ITEMS.get(5)), query);
    }

    String headline(List<MenuItem> items) {
        return "menu-agent matched " + items.size() + " menu options";
    }

    String describeSearch(String query) {
        List<MenuItem> matches = search(query);
        return matches.stream().map(MenuItem::name).limit(3).reduce((left, right) -> left + ", " + right).orElse("today's top combos");
    }

    List<MenuItem> findSubstitutes(String unavailableItemId, String customerMessage) {
        String normalized = normalize(customerMessage);
        List<MenuItem> sameCategory = ITEMS.stream()
                .filter(item -> !item.id().equals(unavailableItemId))
                .filter(item -> sameCategory(unavailableItemId, item.id()))
                .toList();
        List<MenuItem> intentAligned = sameCategory.stream()
                .filter(item -> matchesIntent(normalized, item))
                .toList();
        List<MenuItem> matches = intentAligned.isEmpty() ? sameCategory : intentAligned;
        if (matches.isEmpty()) {
            matches = ITEMS.stream()
                    .filter(item -> !item.id().equals(unavailableItemId))
                    .filter(item -> matchesIntent(normalized, item))
                    .toList();
        }
        if (matches.isEmpty()) {
            matches = ITEMS.stream()
                    .filter(item -> !item.id().equals(unavailableItemId))
                    .toList();
        }
        return tuneQuantities(matches.stream().limit(3).toList(), customerMessage);
    }

    String substitutionHeadline(List<MenuItem> items) {
        return "menu-agent prepared " + items.size() + " fallback options";
    }

    String describeSubstitutes(String unavailableItemId, String customerMessage) {
        return findSubstitutes(unavailableItemId, customerMessage).stream()
                .map(MenuItem::name)
                .reduce((left, right) -> left + ", " + right)
                .orElse("the closest available combo");
    }

    private List<MenuItem> tuneQuantities(List<MenuItem> items, String query) {
        int partySize = detectPartySize(query);
        if (partySize <= 1) {
            return items;
        }
        return items.stream()
                .map(item -> new MenuItem(item.id(), item.name(), item.description(), item.price(), Math.max(1, Math.min(4, partySize / 2 + item.suggestedQuantity()))))
                .toList();
    }

    private boolean matches(String normalized, MenuItem item) {
        if (normalized.isBlank()) {
            return false;
        }
        return containsAny(normalized, item.name().toLowerCase(Locale.ROOT), item.description().toLowerCase(Locale.ROOT))
                || (normalized.contains("チキン") && item.name().contains("Chicken"))
                || (normalized.contains("バーガー") && item.name().contains("Burger"))
                || (normalized.contains("子ども") && item.name().contains("Kids"))
            || (normalized.contains("飲み物") && (item.name().contains("Lemon") || item.name().contains("Latte")))
                || (normalized.contains("ポテト") && item.name().contains("Fries"));
    }

    private boolean containsAny(String normalized, String... fields) {
        for (String field : fields) {
            for (String token : normalized.split("\\s+")) {
                if (!token.isBlank() && field.contains(token)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean sameCategory(String leftId, String rightId) {
        return categoryOf(leftId).equals(categoryOf(rightId));
    }

    private String categoryOf(String itemId) {
        int separator = itemId.indexOf('-');
        return separator >= 0 ? itemId.substring(0, separator) : itemId;
    }

    private boolean matchesIntent(String normalized, MenuItem item) {
        if (normalized.isBlank()) {
            return true;
        }
        if (normalized.contains("子ども") || normalized.contains("kids")) {
            return item.name().contains("Kids") || item.description().contains("apple") || item.description().contains("corn");
        }
        if (normalized.contains("辛さ控えめ") || normalized.contains("mild")) {
            return item.name().contains("Garden") || item.name().contains("Lemon") || item.name().contains("Kids");
        }
        if (normalized.contains("飲み物") || normalized.contains("drink")) {
            return item.id().startsWith("drink-");
        }
        return true;
    }

    private int detectPartySize(String query) {
        if (query.contains("3人") || query.contains("三人")) {
            return 3;
        }
        if (query.contains("4人") || query.contains("四人")) {
            return 4;
        }
        if (query.contains("2人") || query.contains("二人") || query.contains("ふたり")) {
            return 2;
        }
        return 1;
    }

    private String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }
}

@Configuration
class MenuArachneConfiguration {

    @Bean
    Tool menuLookupTool(MenuRepository repository) {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec("menu_catalog_lookup", "Read the local menu catalog and summarize the best matches for the request.", schema());
            }

            @Override
            public ToolResult invoke(Object input) {
                return invoke(input, new ToolInvocationContext("menu_catalog_lookup", null, input, null));
            }

            @Override
            public ToolResult invoke(Object input, ToolInvocationContext context) {
                String query = String.valueOf(values(input).getOrDefault("query", ""));
                return ToolResult.success(context.toolUseId(), Map.of("matchSummary", repository.describeSearch(query)));
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
                        "Prepare menu alternatives for kitchen-agent when a requested item is unavailable.",
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> values(Object input) {
        if (input instanceof Map<?, ?> rawValues) {
            LinkedHashMap<String, Object> values = new LinkedHashMap<>();
            rawValues.forEach((key, value) -> values.put(String.valueOf(key), value));
            return values;
        }
        return Map.of();
    }

    private static final class MenuDeterministicModel implements Model {

        @Override
        public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools) {
            return converse(messages, tools, null, null);
        }

        @Override
        public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools, String systemPrompt) {
            return converse(messages, tools, systemPrompt, null);
        }

        @Override
        public Iterable<ModelEvent> converse(
                List<Message> messages,
                List<ToolSpec> tools,
                String systemPrompt,
                ToolSelection toolSelection) {
            Map<String, String> requestArgs = latestRequestArgs(messages);
            String userText = requestArgs.getOrDefault("query", latestUserText(messages));
            boolean substitutionQuery = requestArgs.containsKey("unavailableItemId");
            boolean isFamilyQuery = isFamilyQuery(userText);

            if (!substitutionQuery && isFamilyQuery && !hasSkillActivation(messages)) {
                return List.of(
                        new ModelEvent.ToolUse("skill-family", "activate_skill", Map.of("name", "family-order-guide")),
                        new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
            }

            String toolUseId = substitutionQuery ? "menu-substitution-lookup" : "menu-lookup";
            Map<String, Object> toolContent = latestToolContent(messages, toolUseId);
            if (toolContent == null) {
            if (substitutionQuery) {
                return List.of(
                    new ModelEvent.ToolUse(
                        "menu-substitution-lookup",
                        "menu_substitution_lookup",
                        Map.of(
                            "unavailableItemId", requestArgs.getOrDefault("unavailableItemId", ""),
                            "customerMessage", requestArgs.getOrDefault("customerMessage", ""))),
                    new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
            }
                return List.of(
                        new ModelEvent.ToolUse("menu-lookup", "menu_catalog_lookup", Map.of("query", userText)),
                        new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
            }

            if (substitutionQuery) {
            return List.of(
                new ModelEvent.TextDelta("menu-agent suggested "
                    + toolContent.getOrDefault("substitutionSummary", "the closest substitutes")
                    + " for kitchen-agent to validate."),
                new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
            }

            String prefix = isFamilyQuery ? "[family-order-guide] " : "";
            return List.of(
                    new ModelEvent.TextDelta(prefix + "menu-agent recommends " + toolContent.getOrDefault("matchSummary", "today's top combos")
                            + ". It keeps the order lightweight and easy to confirm in chat."),
                    new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
        }

        private boolean isFamilyQuery(String text) {
            if (text == null) {
                return false;
            }
            return text.contains("子ども") || text.contains("家族") || text.contains("kids")
                    || text.contains("2人") || text.contains("3人") || text.contains("4人");
        }

        private boolean hasSkillActivation(List<Message> messages) {
            for (Message message : messages) {
                for (ContentBlock block : message.content()) {
                    if (block instanceof ContentBlock.ToolResult toolResult
                            && toolResult.content() instanceof Map<?, ?> content
                            && "skill_activation".equals(content.get("type"))) {
                        return true;
                    }
                }
            }
            return false;
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
                        return text.text().replace("query=", "");
                    }
                }
            }
            return "";
        }

        private Map<String, String> latestRequestArgs(List<Message> messages) {
            String raw = latestUserText(messages);
            LinkedHashMap<String, String> values = new LinkedHashMap<>();
            for (String line : raw.split("\\R")) {
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

record MenuSuggestionRequest(String sessionId, String message) {}

record MenuSubstitutionRequest(String sessionId, String message, String unavailableItemId) {}

record MenuSuggestionResponse(String service, String agent, String headline, String summary, List<MenuItem> items) {}

record MenuSubstitutionResponse(String service, String agent, String headline, String summary, List<MenuItem> items) {}

record MenuItem(String id, String name, String description, BigDecimal price, int suggestedQuantity) {}