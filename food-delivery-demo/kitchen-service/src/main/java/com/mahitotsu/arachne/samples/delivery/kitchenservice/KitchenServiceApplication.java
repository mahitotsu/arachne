package com.mahitotsu.arachne.samples.delivery.kitchenservice;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
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
public class KitchenServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(KitchenServiceApplication.class, args);
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

    KitchenApplicationService(
            AgentFactory agentFactory,
            KitchenRepository repository,
            @Qualifier("kitchenLookupTool") Tool kitchenLookupTool) {
        this.agentFactory = agentFactory;
        this.repository = repository;
        this.kitchenLookupTool = kitchenLookupTool;
    }

    KitchenCheckResponse check(KitchenCheckRequest request) {
        List<KitchenItemStatus> statuses = repository.check(request.itemIds());
        int readyInMinutes = statuses.stream().mapToInt(KitchenItemStatus::prepMinutes).max().orElse(12);
        String summary = agentFactory.builder()
                .systemPrompt("You are the kitchen-agent. Explain stock pressure and substitutions in one short paragraph.")
                .tools(kitchenLookupTool)
                .build()
                .run("items=" + String.join(",", request.itemIds()))
                .text();
        return new KitchenCheckResponse("kitchen-service", "kitchen-agent", repository.headline(statuses), summary, readyInMinutes, statuses);
    }
}

@Component
class KitchenRepository {

    private static final Map<String, KitchenItemStatus> STOCK = Map.of(
            "combo-crispy", new KitchenItemStatus("combo-crispy", true, 14, null, null, null),
            "combo-smash", new KitchenItemStatus("combo-smash", true, 16, null, null, null),
            "combo-kids", new KitchenItemStatus("combo-kids", true, 9, null, null, null),
            "side-fries", new KitchenItemStatus("side-fries", false, 7, "side-nuggets", "Nugget Share Box", new BigDecimal("640.00")),
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
        return statuses.stream()
                .map(status -> status.available()
                        ? status.itemId() + " ready in " + status.prepMinutes() + " min"
                        : status.itemId() + " swaps to " + status.substituteName())
                .reduce((left, right) -> left + "; " + right)
                .orElse("kitchen is ready");
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
                return ToolResult.success(context.toolUseId(), Map.of("inventorySummary", repository.describe(itemIds)));
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

    private static Map<String, Object> values(Object input) {
        if (input instanceof Map<?, ?> rawValues) {
            LinkedHashMap<String, Object> values = new LinkedHashMap<>();
            rawValues.forEach((key, value) -> values.put(String.valueOf(key), value));
            return values;
        }
        return Map.of();
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
            Map<String, Object> toolContent = latestToolContent(messages, "kitchen-lookup");
            if (toolContent == null) {
                return List.of(
                        new ModelEvent.ToolUse("kitchen-lookup", "kitchen_inventory_lookup", Map.of("items", latestUserText(messages))),
                        new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
            }
            return List.of(
                    new ModelEvent.TextDelta("kitchen-agent checked the line and found: " + toolContent.getOrDefault("inventorySummary", "kitchen is ready") + "."),
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
                        return text.text().replace("items=", "");
                    }
                }
            }
            return "";
        }
    }
}

record KitchenCheckRequest(String sessionId, String message, List<String> itemIds) {}

record KitchenCheckResponse(String service, String agent, String headline, String summary, int readyInMinutes, List<KitchenItemStatus> items) {}

record KitchenItemStatus(String itemId, boolean available, int prepMinutes, String substituteItemId, String substituteName, BigDecimal substitutePrice) {}