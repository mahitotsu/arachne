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
}

@Service
class MenuApplicationService {

    private final AgentFactory agentFactory;
    private final MenuRepository repository;
    private final Tool menuLookupTool;

    MenuApplicationService(
            AgentFactory agentFactory,
            MenuRepository repository,
            @Qualifier("menuLookupTool") Tool menuLookupTool) {
        this.agentFactory = agentFactory;
        this.repository = repository;
        this.menuLookupTool = menuLookupTool;
    }

    MenuSuggestionResponse suggest(MenuSuggestionRequest request) {
        List<MenuItem> items = repository.search(request.message());
        String summary = agentFactory.builder()
                .systemPrompt("You are the menu-agent for a fast-food delivery app. Briefly explain the best menu matches.")
                .tools(menuLookupTool)
                .build()
                .run("query=" + request.message())
                .text();
        return new MenuSuggestionResponse("menu-service", "menu-agent", repository.headline(items), summary, items);
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
                || (normalized.contains("飲み物") && item.name().contains("Lemon") || item.name().contains("Latte"))
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
    @ConditionalOnProperty(name = "delivery.model.mode", havingValue = "deterministic", matchIfMissing = true)
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
        public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools, String systemPrompt, ToolSelection toolSelection) {
            Map<String, Object> toolContent = latestToolContent(messages, "menu-lookup");
            if (toolContent == null) {
                return List.of(
                        new ModelEvent.ToolUse("menu-lookup", "menu_catalog_lookup", Map.of("query", latestUserText(messages))),
                        new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
            }
            return List.of(
                    new ModelEvent.TextDelta("menu-agent recommends " + toolContent.getOrDefault("matchSummary", "today's top combos")
                            + ". It keeps the order lightweight and easy to confirm in chat."),
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
                        return text.text().replace("query=", "");
                    }
                }
            }
            return "";
        }
    }
}

record MenuSuggestionRequest(String sessionId, String message) {}

record MenuSuggestionResponse(String service, String agent, String headline, String summary, List<MenuItem> items) {}

record MenuItem(String id, String name, String description, BigDecimal price, int suggestedQuantity) {}