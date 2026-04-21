package com.mahitotsu.arachne.samples.delivery.deliveryservice;

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
public class DeliveryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DeliveryServiceApplication.class, args);
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
    private final DeliveryRoutingRepository repository;
    private final Tool deliveryRoutingTool;

    DeliveryApplicationService(
            AgentFactory agentFactory,
            DeliveryRoutingRepository repository,
            @Qualifier("deliveryRoutingTool") Tool deliveryRoutingTool) {
        this.agentFactory = agentFactory;
        this.repository = repository;
        this.deliveryRoutingTool = deliveryRoutingTool;
    }

    DeliveryQuoteResponse quote(DeliveryQuoteRequest request) {
        List<DeliveryOption> options = repository.quote(request.message(), request.itemNames());
        String summary = agentFactory.builder()
                .systemPrompt("You are the delivery-agent. Explain the best delivery option and ETA in one paragraph.")
                .tools(deliveryRoutingTool)
                .build()
                .run("request=" + request.message())
                .text();
        return new DeliveryQuoteResponse("delivery-service", "delivery-agent", repository.headline(request.message()), summary, options);
    }
}

@Component
class DeliveryRoutingRepository {

    List<DeliveryOption> quote(String message, List<String> itemNames) {
        boolean fastest = message != null && (message.contains("最速") || message.toLowerCase().contains("fast"));
        int itemCount = itemNames == null ? 0 : itemNames.size();
        int baseEta = 18 + Math.max(0, itemCount - 2) * 2;
        DeliveryOption express = new DeliveryOption("express", "Express rider", baseEta, new BigDecimal("300.00"));
        DeliveryOption standard = new DeliveryOption("standard", "Standard route", baseEta + 9, new BigDecimal("180.00"));
        return fastest ? List.of(express, standard) : List.of(standard, express);
    }

    String headline(String message) {
        if (message != null && message.contains("最速")) {
            return "delivery-agent prioritised the express lane";
        }
        return "delivery-agent priced both standard and express options";
    }

    String describe(String message) {
        List<DeliveryOption> options = quote(message, List.of());
        return options.stream()
                .map(option -> option.label() + " in " + option.etaMinutes() + " min")
                .reduce((left, right) -> left + ", then " + right)
                .orElse("standard route in 27 min");
    }
}

@Configuration
class DeliveryArachneConfiguration {

    @Bean
    Tool deliveryRoutingTool(DeliveryRoutingRepository repository) {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec("delivery_route_lookup", "Estimate the best delivery route and ETA options for the order.", schema());
            }

            @Override
            public ToolResult invoke(Object input) {
                return invoke(input, new ToolInvocationContext("delivery_route_lookup", null, input, null));
            }

            @Override
            public ToolResult invoke(Object input, ToolInvocationContext context) {
                String request = String.valueOf(values(input).getOrDefault("request", ""));
                return ToolResult.success(context.toolUseId(), Map.of("routeSummary", repository.describe(request)));
            }
        };
    }

    @Bean
    @ConditionalOnProperty(name = "delivery.model.mode", havingValue = "deterministic", matchIfMissing = false)
    Model deliveryDeterministicModel() {
        return new DeliveryDeterministicModel();
    }

    private static ObjectNode schema() {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("type", "object");
        ObjectNode properties = root.putObject("properties");
        properties.putObject("request").put("type", "string");
        root.putArray("required").add("request");
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
            Map<String, Object> toolContent = latestToolContent(messages, "delivery-lookup");
            if (toolContent == null) {
                return List.of(
                        new ModelEvent.ToolUse("delivery-lookup", "delivery_route_lookup", Map.of("request", latestUserText(messages))),
                        new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
            }
            return List.of(
                    new ModelEvent.TextDelta("delivery-agent prepared the route plan: " + toolContent.getOrDefault("routeSummary", "standard delivery in 27 min") + "."),
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
                        return text.text().replace("request=", "");
                    }
                }
            }
            return "";
        }
    }
}

record DeliveryQuoteRequest(String sessionId, String message, List<String> itemNames) {}

record DeliveryQuoteResponse(String service, String agent, String headline, String summary, List<DeliveryOption> options) {}

record DeliveryOption(String code, String label, int etaMinutes, BigDecimal fee) {}