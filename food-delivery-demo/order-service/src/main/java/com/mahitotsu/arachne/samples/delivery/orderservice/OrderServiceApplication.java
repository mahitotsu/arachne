package com.mahitotsu.arachne.samples.delivery.orderservice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.core.JsonProcessingException;
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
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }

    @Bean("menuRestClient")
    RestClient menuRestClient(@Value("${MENU_SERVICE_BASE_URL:http://localhost:8081}") String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).build();
    }

    @Bean("kitchenRestClient")
    RestClient kitchenRestClient(@Value("${KITCHEN_SERVICE_BASE_URL:http://localhost:8082}") String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).build();
    }

    @Bean("deliveryRestClient")
    RestClient deliveryRestClient(@Value("${DELIVERY_SERVICE_BASE_URL:http://localhost:8083}") String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).build();
    }

    @Bean("paymentRestClient")
    RestClient paymentRestClient(@Value("${PAYMENT_SERVICE_BASE_URL:http://localhost:8084}") String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).build();
    }
}

@RestController
@RequestMapping(path = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
class OrderController {

    private final OrderApplicationService applicationService;

    OrderController(OrderApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping(path = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE)
    ChatResponse chat(@RequestBody ChatRequest request) {
        return applicationService.chat(request);
    }

    @GetMapping("/session/{sessionId}")
    ChatResponse session(@PathVariable String sessionId) {
        return applicationService.session(sessionId);
    }
}

@Service
class OrderApplicationService {

    private static final String DEMO_CUSTOMER_ID = "demo-user";

    private final String defaultLanguage;
    private final OrderSessionStore sessionStore;
    private final OrderRepository orderRepository;
    private final MenuGateway menuGateway;
    private final KitchenGateway kitchenGateway;
    private final DeliveryGateway deliveryGateway;
    private final PaymentGateway paymentGateway;
    private final AgentFactory agentFactory;
    private final Tool recentOrderLookupTool;

    OrderApplicationService(
            OrderSessionStore sessionStore,
            OrderRepository orderRepository,
            MenuGateway menuGateway,
            KitchenGateway kitchenGateway,
            DeliveryGateway deliveryGateway,
            PaymentGateway paymentGateway,
            AgentFactory agentFactory,
            @Qualifier("recentOrderLookupTool") Tool recentOrderLookupTool) {
        this.defaultLanguage = Locale.getDefault().getDisplayLanguage(Locale.ENGLISH);
        this.sessionStore = sessionStore;
        this.orderRepository = orderRepository;
        this.menuGateway = menuGateway;
        this.kitchenGateway = kitchenGateway;
        this.deliveryGateway = deliveryGateway;
        this.paymentGateway = paymentGateway;
        this.agentFactory = agentFactory;
        this.recentOrderLookupTool = recentOrderLookupTool;
    }

    ChatResponse chat(ChatRequest request) {
        String sessionId = request.sessionId() == null || request.sessionId().isBlank()
                ? "session-" + UUID.randomUUID().toString().substring(0, 8)
                : request.sessionId();
        OrderChatSession existingSession = sessionStore.load(sessionId).orElse(emptySession(sessionId));
        RoutingDecision routingDecision = routeRequest(request.message(), existingSession);
        ArrayList<ConversationMessage> conversation = new ArrayList<>(existingSession.conversation());
        conversation.add(new ConversationMessage("user", request.message()));

        List<ServiceTrace> serviceTraces = Collections.synchronizedList(new ArrayList<>());
        AtomicReference<MenuSuggestionResponse> capturedMenuResponse = new AtomicReference<>(null);
        AtomicReference<KitchenCheckResponse> capturedKitchenResponse = new AtomicReference<>(null);
        AtomicReference<DeliveryQuoteResponse> capturedDeliveryResponse = new AtomicReference<>(null);
        AtomicReference<List<MenuItemView>> capturedMenuItems = new AtomicReference<>(List.of());
        AtomicReference<List<OrderLineItem>> capturedLineItems = new AtomicReference<>(List.of());
        AtomicReference<List<DeliveryOptionView>> capturedDeliveryOptions = new AtomicReference<>(List.of());
        AtomicReference<DeliveryOptionView> capturedSelectedDelivery = new AtomicReference<>(null);
        AtomicReference<PaymentPrepareResponse> capturedPaymentResponse = new AtomicReference<>(null);

        Tool menuTool = buildMenuTool(sessionId, capturedMenuResponse, capturedMenuItems, serviceTraces);
        Tool kitchenTool = buildKitchenTool(sessionId, capturedMenuItems, capturedLineItems, capturedKitchenResponse,
            serviceTraces);
        Tool deliveryTool = buildDeliveryTool(sessionId, capturedDeliveryResponse, capturedDeliveryOptions, serviceTraces);
        Tool paymentTool = buildPaymentTool(sessionId, capturedLineItems, capturedDeliveryOptions,
                capturedSelectedDelivery, capturedPaymentResponse, serviceTraces);

        String draftContext = existingSession.draft().items().isEmpty() ? ""
                : "\n\nCurrent draft order: " + existingSession.draft().items().stream()
                        .map(i -> i.quantity() + "x " + i.name() + " (¥" + i.unitPrice() + " each)")
                        .reduce((a, b) -> a + ", " + b).orElse("")
                        + " | status: " + existingSession.draft().status()
                        + " | total: ¥" + existingSession.draft().total();

        String assistantMessage = agentFactory.builder()
                .systemPrompt("""
                You are the order coordinator for a single-brand cloud kitchen app.
                        Your job is to help the customer build and confirm their order by calling the right tools in sequence.

                BUSINESS SETTING:
                - The app serves food from one cloud kitchen only.
                - If an item is unavailable, offer same-brand substitute items instead of alternate kitchens.
                - Delivery is delivery-only, with two lanes: Partner Standard (external courier) and In-house Express (the kitchen's own staff).

                        ## Tools — when and how to call them

                        suggest_menu
                        - Call when the customer asks for food options, recommendations, or anything to eat.
                        - Call when the customer wants to MODIFY the current order (e.g. "double it", "倍にして", "量を増やして", "add one more", "もう一つ追加して").
                          In that case, pass a message that describes the modification in terms of the existing items,
                          e.g. "Return the same items as the current draft but double all quantities: 1x Crispy Chicken Box → 2x".
                        - Call when the customer references a past order to re-order it.
                        - ALWAYS call check_kitchen immediately after suggest_menu, using the itemIds returned.

                        check_kitchen
                        - Call immediately after suggest_menu with the itemIds from that result.
                        - After check_kitchen returns, present the proposed items AND the kitchen status (highlight any substitutions or unavailable items) to the customer.
                        - Ask for explicit confirmation. End your reply with: [CHOICES: "はい、この内容で注文します", "変更したい"]
                          Do NOT call quote_delivery in the same turn. Stop here.

                        quote_delivery
                        - Call ONLY after the customer has explicitly confirmed the proposed items and kitchen status.
                        - Use the itemNames from the previous check_kitchen result.
                        - After quote_delivery returns, present the delivery-agent's assessment (courier availability, traffic, weather, adjusted ETAs) AND the available options with fees.
                        - Ask the customer to choose a delivery tier. End your reply with [CHOICES] listing the available options, e.g.:
                                                    [CHOICES: "自社エクスプレス (XX分・¥300)", "パートナースタンダード (XX分・¥180)"]
                          If express is unavailable, offer only standard.
                          Do NOT call prepare_payment in the same turn. Stop here.

                        prepare_payment
                        - Call ONLY after the customer has explicitly chosen a delivery option.
                        - Set confirmRequested=true ONLY when the customer explicitly says to place, confirm, or submit the order (e.g. "注文確定して", "confirm", "place the order").
                        - Set fastestDelivery=true when the customer chose the express option.

                        recent_order_lookup
                        - Call when the customer refers to a previous order (e.g. "前回と同じ", "same as last time").

                        ## Rules

                        TOOL CHAIN — THREE-TURN CONFIRMATION FLOW:
                        - Proposal turn: call suggest_menu → check_kitchen → present items + kitchen status → ask [CHOICES: "はい、この内容で注文します", "変更したい"]. Stop here.
                        - Delivery turn (after item confirmation): call quote_delivery → present delivery-agent assessment + options → ask [CHOICES: "自社エクスプレス ...", "パートナースタンダード ..."]. Stop here.
                        - Payment turn (after delivery choice): call prepare_payment → present total and ask for final confirmation.
                        Items are NEVER added to the draft without the customer's explicit confirmation.
                        Never call quote_delivery before the customer has confirmed items.
                        Never call prepare_payment before the customer has chosen a delivery option.

                        ANSWER THE ACTUAL REQUEST: Read what the customer wrote carefully. If they said "倍にして" (double it), double the quantities. If they said "子ども向け" (for kids), pick child-friendly items. Always address the customer's specific request, not a generic response.

                        LANGUAGE: Reply in %s by default. If the customer writes in a different language, mirror their language.

                        CLARIFICATION: When the customer's intent is genuinely ambiguous, ask a short question and append choices at the very end of your reply using this exact format (no text after it):
                        [CHOICES: "Option A", "Option B", "Option C"]

                        DIRECT ANSWERS: For questions unrelated to ordering, answer directly without calling any tool.
                        """.formatted(defaultLanguage))
                .tools(menuTool, kitchenTool, deliveryTool, paymentTool, recentOrderLookupTool)
                .build()
                .run(request.message() + draftContext)
                .text();

        // Only promote kitchen-resolved items to the draft when quote_delivery has also
        // run in this same turn. That means the customer already saw and confirmed the
        // kitchen status (proposal turn stops before quote_delivery).
        List<OrderLineItem> lineItems = (capturedLineItems.get().isEmpty() || capturedDeliveryOptions.get().isEmpty())
                ? existingSession.draft().items()
                : capturedLineItems.get();
        PaymentPrepareResponse paymentResponse = capturedPaymentResponse.get();
        DeliveryOptionView selectedDelivery = capturedSelectedDelivery.get() != null
                ? capturedSelectedDelivery.get()
                : (capturedDeliveryOptions.get().isEmpty()
                ? new DeliveryOptionView("standard", "Partner Standard", 27, new BigDecimal("180.00"))
                        : capturedDeliveryOptions.get().get(0));
        BigDecimal subtotal = subtotal(lineItems);
        BigDecimal total = paymentResponse != null
                ? paymentResponse.total()
                : subtotal.add(selectedDelivery.fee()).setScale(2, RoundingMode.HALF_UP);

        String orderId = existingSession.draft().orderId();
        String status = lineItems.isEmpty() ? existingSession.draft().status() : "DRAFT_READY";
        if (paymentResponse != null && paymentResponse.charged()) {
            orderId = orderRepository.saveConfirmedOrder(
                    DEMO_CUSTOMER_ID,
                    lineItems,
                    subtotal,
                    total,
                    selectedDelivery.etaMinutes() + " min via " + selectedDelivery.label(),
                    paymentResponse.paymentStatus());
            status = "CONFIRMED";
        }

        OrderDraft draft = new OrderDraft(
                status,
                lineItems,
                subtotal,
                total,
                selectedDelivery.etaMinutes() + " min via " + selectedDelivery.label(),
                paymentResponse != null ? paymentResponse.paymentStatus() : existingSession.draft().paymentStatus(),
                paymentResponse != null ? paymentResponse.selectedMethod() : existingSession.draft().paymentMethod(),
                orderId);

        // Parse [CHOICES: "A", "B", "C"] block from the assistant message
        List<String> choices = List.of();
        String displayMessage = assistantMessage;
        Pattern choicesPattern = Pattern.compile("\\[CHOICES:\\s*((?:\"[^\"]+\"(?:,\\s*)?)+)\\]");
        Matcher choicesMatcher = choicesPattern.matcher(assistantMessage);
        if (choicesMatcher.find()) {
            String raw = choicesMatcher.group(1);
            choices = java.util.Arrays.stream(raw.split(",\\s*"))
                    .map(s -> s.trim().replaceAll("^\"|\"$", ""))
                    .filter(s -> !s.isEmpty())
                    .toList();
            displayMessage = assistantMessage.substring(0, choicesMatcher.start()).trim();
        }
        if ("proposal-skill".equals(routingDecision.selectedSkill())) {
            displayMessage = mergeProposalResponse(
                displayMessage,
                capturedMenuResponse.get(),
                capturedKitchenResponse.get(),
                capturedMenuItems.get());
        } else if (paymentResponse != null) {
            displayMessage = mergePaymentResponse(
                    displayMessage,
                    capturedDeliveryResponse.get(),
                    selectedDelivery,
                    paymentResponse,
                    total,
                    request.message());
        } else if (capturedDeliveryResponse.get() != null) {
            displayMessage = mergeDeliveryResponse(
                    displayMessage,
                    capturedDeliveryResponse.get(),
                    request.message());
        }

        conversation.add(new ConversationMessage("assistant", displayMessage));
        OrderChatSession updated = new OrderChatSession(sessionId, List.copyOf(conversation), draft);
        sessionStore.save(updated);

        serviceTraces.add(new ServiceTrace(
            "order-service",
            "order-agent",
            routingHeadline(status, routingDecision),
            displayMessage,
            routingDecision));

        return new ChatResponse(
            sessionId,
            updated.conversation(),
            displayMessage,
            draft,
            List.copyOf(serviceTraces),
            routingDecision,
            contextualSuggestions(updated),
            choices);
    }

    ChatResponse session(String sessionId) {
        OrderChatSession session = sessionStore.load(sessionId).orElse(emptySession(sessionId));
        return new ChatResponse(session.sessionId(), session.conversation(), "", session.draft(), List.of(), null,
            contextualSuggestions(session), List.of());
    }

    private List<OrderLineItem> buildLineItems(List<MenuItemView> menuItems, List<KitchenItemStatusView> statuses) {
        Map<String, KitchenItemStatusView> statusByItemId = statuses.stream()
                .collect(LinkedHashMap::new, (map, status) -> map.put(status.itemId(), status), Map::putAll);
        ArrayList<OrderLineItem> items = new ArrayList<>();
        for (MenuItemView menuItem : menuItems) {
            KitchenItemStatusView status = statusByItemId.get(menuItem.id());
            if (status == null || status.available()) {
                items.add(new OrderLineItem(menuItem.name(), menuItem.suggestedQuantity(), menuItem.price(), "kitchen ready"));
                continue;
            }
            if (status.substituteName() != null && status.substitutePrice() != null) {
                items.add(new OrderLineItem(
                        status.substituteName(),
                        menuItem.suggestedQuantity(),
                        status.substitutePrice(),
                        "swapped from " + menuItem.name()));
            }
        }
        if (!items.isEmpty()) {
            return List.copyOf(items);
        }
        return List.of(new OrderLineItem("Crispy Chicken Box", 1, new BigDecimal("980.00"), "fallback draft"));
    }

    private DeliveryOptionView selectDeliveryOption(List<DeliveryOptionView> options, boolean fastestDelivery) {
        if (options == null || options.isEmpty()) {
            return new DeliveryOptionView("standard", "Partner Standard", 27, new BigDecimal("180.00"));
        }
        if (!fastestDelivery) {
            return options.get(0);
        }
        return options.stream()
                .min((left, right) -> Integer.compare(left.etaMinutes(), right.etaMinutes()))
                .orElse(options.get(0));
    }

    private BigDecimal subtotal(List<OrderLineItem> items) {
        return items.stream()
                .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

        private Tool buildMenuTool(String sessionId,
            AtomicReference<MenuSuggestionResponse> capturedMenuResponse,
            AtomicReference<List<MenuItemView>> capturedMenuItems,
            List<ServiceTrace> serviceTraces) {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                ObjectNode root = JsonNodeFactory.instance.objectNode();
                root.put("type", "object");
                ObjectNode props = root.putObject("properties");
                props.putObject("message").put("type", "string")
                        .put("description", "The customer's request for menu suggestions");
                root.putArray("required").add("message");
                root.put("additionalProperties", false);
                return new ToolSpec("suggest_menu",
                        "Ask the menu team for food recommendations matching the customer's request.", root);
            }

            @Override
            public ToolResult invoke(Object input) {
                return invoke(input, new ToolInvocationContext("suggest_menu", null, input, null));
            }

            @Override
            public ToolResult invoke(Object input, ToolInvocationContext context) {
                String message = String.valueOf(inputMap(input).getOrDefault("message", ""));
                MenuSuggestionResponse response = menuGateway.suggest(new MenuSuggestionRequest(sessionId, message));
                capturedMenuResponse.set(response);
                capturedMenuItems.set(response.items());
                serviceTraces.add(new ServiceTrace(response.service(), response.agent(), response.headline(), response.summary(), null));
                return ToolResult.success(context.toolUseId(), Map.of(
                        "summary", response.summary(),
                        "itemIds", response.items().stream().map(MenuItemView::id).toList(),
                        "itemNames", response.items().stream().map(MenuItemView::name).toList()));
            }
        };
    }

    private Tool buildKitchenTool(String sessionId,
            AtomicReference<List<MenuItemView>> capturedMenuItems,
            AtomicReference<List<OrderLineItem>> capturedLineItems,
            AtomicReference<KitchenCheckResponse> capturedKitchenResponse,
            List<ServiceTrace> serviceTraces) {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                ObjectNode root = JsonNodeFactory.instance.objectNode();
                root.put("type", "object");
                ObjectNode props = root.putObject("properties");
                ObjectNode itemIdsNode = props.putObject("itemIds");
                itemIdsNode.put("type", "array");
                itemIdsNode.putObject("items").put("type", "string");
                itemIdsNode.put("description", "Item IDs from suggest_menu to check availability");
                props.putObject("message").put("type", "string");
                root.putArray("required").add("itemIds").add("message");
                root.put("additionalProperties", false);
                return new ToolSpec("check_kitchen",
                        "Check with the kitchen for item availability, prep time, and substitutions.", root);
            }

            @Override
            public ToolResult invoke(Object input) {
                return invoke(input, new ToolInvocationContext("check_kitchen", null, input, null));
            }

            @Override
            public ToolResult invoke(Object input, ToolInvocationContext context) {
                Map<String, Object> args = inputMap(input);
                List<String> itemIds = stringList(args.get("itemIds"));
                String message = String.valueOf(args.getOrDefault("message", ""));
                KitchenCheckResponse response = kitchenGateway.check(new KitchenCheckRequest(sessionId, message, itemIds));
                capturedKitchenResponse.set(response);
                List<OrderLineItem> lineItems = buildLineItems(capturedMenuItems.get(), response.items());
                capturedLineItems.set(lineItems);
                serviceTraces.add(new ServiceTrace(response.service(), response.agent(), response.headline(), response.summary(), null));
                if (response.collaborations() != null) {
                    response.collaborations().forEach(collaboration -> serviceTraces.add(
                            new ServiceTrace(
                                    collaboration.service(),
                                    collaboration.agent(),
                                    collaboration.headline(),
                                    collaboration.summary(),
                                    null)));
                }
                return ToolResult.success(context.toolUseId(), Map.of(
                        "summary", response.summary(),
                        "itemNames", lineItems.stream().map(OrderLineItem::name).toList(),
                        "readyInMinutes", response.readyInMinutes()));
            }
        };
    }

        private Tool buildDeliveryTool(String sessionId,
            AtomicReference<DeliveryQuoteResponse> capturedDeliveryResponse,
            AtomicReference<List<DeliveryOptionView>> capturedDeliveryOptions,
            List<ServiceTrace> serviceTraces) {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                ObjectNode root = JsonNodeFactory.instance.objectNode();
                root.put("type", "object");
                ObjectNode props = root.putObject("properties");
                ObjectNode itemNamesNode = props.putObject("itemNames");
                itemNamesNode.put("type", "array");
                itemNamesNode.putObject("items").put("type", "string");
                itemNamesNode.put("description", "Item names from check_kitchen to route for delivery");
                props.putObject("message").put("type", "string");
                root.putArray("required").add("itemNames").add("message");
                root.put("additionalProperties", false);
                return new ToolSpec("quote_delivery",
                        "Get delivery options and ETA estimates from the logistics team.", root);
            }

            @Override
            public ToolResult invoke(Object input) {
                return invoke(input, new ToolInvocationContext("quote_delivery", null, input, null));
            }

            @Override
            public ToolResult invoke(Object input, ToolInvocationContext context) {
                Map<String, Object> args = inputMap(input);
                List<String> itemNames = stringList(args.get("itemNames"));
                String message = String.valueOf(args.getOrDefault("message", ""));
                DeliveryQuoteResponse response = deliveryGateway.quote(new DeliveryQuoteRequest(sessionId, message, itemNames));
                capturedDeliveryResponse.set(response);
                capturedDeliveryOptions.set(response.options());
                serviceTraces.add(new ServiceTrace(response.service(), response.agent(), response.headline(), response.summary(), null));
                List<Map<String, Object>> options = response.options().stream()
                        .<Map<String, Object>>map(o -> Map.of(
                                "code", o.code(), "label", o.label(),
                                "etaMinutes", o.etaMinutes(), "fee", o.fee()))
                        .toList();
                return ToolResult.success(context.toolUseId(), Map.of(
                        "summary", response.summary(),
                        "options", options));
            }
        };
    }

    private Tool buildPaymentTool(String sessionId,
            AtomicReference<List<OrderLineItem>> capturedLineItems,
            AtomicReference<List<DeliveryOptionView>> capturedDeliveryOptions,
            AtomicReference<DeliveryOptionView> capturedSelectedDelivery,
            AtomicReference<PaymentPrepareResponse> capturedPaymentResponse,
            List<ServiceTrace> serviceTraces) {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                ObjectNode root = JsonNodeFactory.instance.objectNode();
                root.put("type", "object");
                ObjectNode props = root.putObject("properties");
                props.putObject("confirmRequested").put("type", "boolean")
                        .put("description", "True only when the customer explicitly asks to place or confirm the order");
                props.putObject("fastestDelivery").put("type", "boolean")
                        .put("description", "True when the customer wants the fastest delivery option");
                root.putArray("required").add("confirmRequested").add("fastestDelivery");
                root.put("additionalProperties", false);
                return new ToolSpec("prepare_payment",
                        "Check payment readiness and optionally process the charge.", root);
            }

            @Override
            public ToolResult invoke(Object input) {
                return invoke(input, new ToolInvocationContext("prepare_payment", null, input, null));
            }

            @Override
            public ToolResult invoke(Object input, ToolInvocationContext context) {
                Map<String, Object> args = inputMap(input);
                boolean confirmRequested = Boolean.parseBoolean(String.valueOf(args.getOrDefault("confirmRequested", "false")));
                boolean fastestDelivery = Boolean.parseBoolean(String.valueOf(args.getOrDefault("fastestDelivery", "false")));
                DeliveryOptionView selected = selectDeliveryOption(capturedDeliveryOptions.get(), fastestDelivery);
                capturedSelectedDelivery.set(selected);
                BigDecimal itemsSubtotal = subtotal(capturedLineItems.get());
                BigDecimal total = itemsSubtotal.add(selected.fee()).setScale(2, RoundingMode.HALF_UP);
                PaymentPrepareResponse response = paymentGateway.prepare(
                        new PaymentPrepareRequest(sessionId, "", total, confirmRequested));
                capturedPaymentResponse.set(response);
                serviceTraces.add(new ServiceTrace(response.service(), response.agent(), response.headline(), response.summary(), null));
                return ToolResult.success(context.toolUseId(), Map.of(
                        "summary", response.summary(),
                        "charged", response.charged(),
                        "paymentStatus", response.paymentStatus(),
                        "selectedMethod", response.selectedMethod()));
            }
        };
    }

    private static Map<String, Object> inputMap(Object input) {
        if (input instanceof Map<?, ?> raw) {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            raw.forEach((k, v) -> map.put(String.valueOf(k), v));
            return map;
        }
        return Map.of();
    }

    private static List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private RoutingDecision routeRequest(String message, OrderChatSession session) {
        String normalized = normalize(message);
        boolean hasDraft = !session.draft().items().isEmpty();

        if (asksGeneralQuestion(normalized) && !hasDraft) {
            return new RoutingDecision(
                    "general-question",
                    "direct-answer",
                    "respond-directly",
                    "The request asks for service information rather than a concrete order workflow.");
        }
        if (asksForRecommendations(normalized)) {
            return new RoutingDecision(
                    "menu-discovery",
                    "proposal-skill",
                    "menu-suggestion",
                    "The request asks for recommendations or preference-based curation, so the flow starts by proposing menu options.");
        }
        if (hasDraft && choosesDeliveryTier(normalized)) {
            return new RoutingDecision(
                    "delivery-selection",
                    "order-skill",
                    "payment-prepare",
                    "There is already a live draft and the customer chose a delivery tier, so the flow can continue at payment preparation.");
        }
        if (hasDraft && confirmsCurrentDraft(normalized)) {
            return new RoutingDecision(
                    "draft-confirmation",
                    "order-skill",
                    "delivery-quote",
                    "There is already a live draft and the customer is confirming it, so the next step is delivery quoting.");
        }
        return new RoutingDecision(
                hasDraft ? "draft-adjustment" : "direct-order",
                "order-skill",
                "kitchen-validation",
                hasDraft
                        ? "The request changes or extends the active draft, so the order flow returns to kitchen validation."
                        : "The request contains a concrete order, so the order flow starts at kitchen validation.");
    }

    private String routingHeadline(String status, RoutingDecision routingDecision) {
        if ("CONFIRMED".equals(status)) {
            return "order-agent completed the order workflow";
        }
        if ("direct-answer".equals(routingDecision.selectedSkill())) {
            return "order-agent answered without a workflow skill";
        }
        if ("proposal-skill".equals(routingDecision.selectedSkill())) {
            return "order-agent selected the proposal workflow";
        }
        return "order-agent selected the order workflow";
    }

    private static String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }

    private static boolean asksForRecommendations(String normalized) {
        return containsAny(normalized,
                "おすすめ", "オススメ", "提案", "recommend", "suggest", "何がいい", "なにがいい", "what should i eat",
                "what do you recommend", "kids", "for kids", "子ども向け", "辛さ控えめ", "mild", "人気");
    }

    private static boolean asksGeneralQuestion(String normalized) {
        return containsAny(normalized,
                "サービス", "状況", "混雑", "営業時間", "how busy", "status", "service", "hours", "open now");
    }

    private static boolean choosesDeliveryTier(String normalized) {
        return containsAny(normalized,
                "最速", "express", "エクスプレス", "standard", "スタンダード");
    }

    private static boolean confirmsCurrentDraft(String normalized) {
        return containsAny(normalized,
                "この内容", "これで", "注文確定", "確定", "confirm", "place the order", "注文して");
    }

    private static boolean containsAny(String normalized, String... needles) {
        for (String needle : needles) {
            if (normalized.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private OrderChatSession emptySession(String sessionId) {
        return new OrderChatSession(sessionId, List.of(), emptyDraft());
    }

    private OrderDraft emptyDraft() {
        return new OrderDraft("EMPTY", List.of(), BigDecimal.ZERO, BigDecimal.ZERO, "", "PENDING", "", "");
    }

    private List<String> contextualSuggestions(OrderChatSession session) {
        String status = session.draft().status();
        if ("CONFIRMED".equals(status)) {
            return List.of(
                    "配送状況を教えて",
                    "新しい注文を始めたい",
                    "別のメニューを見せて");
        }
        if ("DRAFT_READY".equals(status)) {
            return List.of(
                    "この内容で注文確定して",
                    "最速配送にして",
                    "量を倍にして",
                    "ドリンクも追加して");
        }
        // Extract last assistant message to derive hints
        List<ConversationMessage> conv = session.conversation();
        if (!conv.isEmpty()) {
            return List.of(
                    "2人分でスパイシー少なめのおすすめを見せて",
                    "子ども向けセットを教えて",
                    "前回と同じ内容で頼みたい",
                    "今の時間帯のおすすめは？");
        }
        return List.of(
                "2人分でスパイシー少なめのおすすめを見せて",
                "子ども向けセットを足して",
                "前回と同じ量で最速配送にして",
                "この内容で注文確定して");
    }

    private String mergeProposalResponse(
            String currentMessage,
            MenuSuggestionResponse menuResponse,
            KitchenCheckResponse kitchenResponse,
            List<MenuItemView> menuItems) {
        if (menuResponse == null || kitchenResponse == null) {
            return currentMessage;
        }
        String menuNarrative = firstNonBlank(
                userFacingSummary(menuResponse.summary()),
                describeSuggestedItems(menuItems));
        String kitchenNarrative = buildKitchenNarrative(kitchenResponse, menuItems, menuNarrative + "\n" + currentMessage);
        if (menuNarrative.isBlank() || kitchenNarrative.isBlank()) {
            return currentMessage;
        }
        boolean japanese = looksJapanese(menuNarrative + "\n" + currentMessage);
        String closing = japanese
                ? "よければこの内容で注文を進めます。変更したい場合はそのまま調整できます。"
                : "If this looks good, we can proceed with this order, or adjust it if you'd like.";
        return String.join("\n\n", menuNarrative, kitchenNarrative, closing);
    }

        private String mergeDeliveryResponse(
            String currentMessage,
            DeliveryQuoteResponse deliveryResponse,
            String languageHint) {
        if (deliveryResponse == null) {
            return currentMessage;
        }
        boolean japanese = looksJapanese(languageHint + "\n" + currentMessage);
        String assessment = japanese
            ? "配送レーンの稼働状況と所要時間を確認しました。"
            : "I checked the delivery lanes and current timing.";
        String optionsLine = describeDeliveryOptions(deliveryResponse.options(), japanese);
        String closing = japanese
            ? "希望する配送レーンを選んでください。"
            : "Please choose the delivery lane you prefer.";
        return String.join("\n\n", assessment, optionsLine, closing);
        }

        private String mergePaymentResponse(
            String currentMessage,
            DeliveryQuoteResponse deliveryResponse,
            DeliveryOptionView selectedDelivery,
            PaymentPrepareResponse paymentResponse,
            BigDecimal total,
            String languageHint) {
        if (paymentResponse == null || selectedDelivery == null) {
            return currentMessage;
        }
        boolean japanese = looksJapanese(languageHint + "\n" + currentMessage);
        String laneLine = japanese
            ? selectedDelivery.label() + " でお届けし、到着目安は約" + selectedDelivery.etaMinutes() + "分です。"
            : "Delivery will be arranged via " + selectedDelivery.label() + " with an ETA of about "
                + selectedDelivery.etaMinutes() + " minutes.";
        String paymentLine = japanese
            ? "お支払いは " + paymentResponse.selectedMethod() + " を使用し、合計は " + formatYen(total) + " です。"
            : "Payment will use " + paymentResponse.selectedMethod() + ", and the total is " + formatYen(total) + ".";
        if (paymentResponse.charged()) {
            String confirmation = japanese
                ? "決済が完了し、注文を確定しました。"
                : "Payment is complete and the order is now confirmed.";
            return String.join("\n\n", laneLine, paymentLine, confirmation);
        }
        String choicesLine = deliveryResponse != null && deliveryResponse.options() != null && !deliveryResponse.options().isEmpty()
            ? describeDeliveryOptions(deliveryResponse.options(), japanese)
            : "";
        String confirmationPrompt = japanese
            ? "この内容でよければ、注文確定と伝えてください。"
            : "If this looks right, tell me to confirm the order.";
        return firstNonBlank(
            String.join("\n\n",
                laneLine,
                paymentLine,
                choicesLine,
                confirmationPrompt).trim(),
            currentMessage);
        }

    private String buildKitchenNarrative(
            KitchenCheckResponse kitchenResponse,
            List<MenuItemView> menuItems,
            String languageHint) {
        if (kitchenResponse == null) {
            return "";
        }
        boolean japanese = looksJapanese(languageHint);
        Map<String, String> itemNameById = menuItems.stream()
                .collect(LinkedHashMap::new, (map, item) -> map.put(item.id(), item.name()), Map::putAll);
        List<String> substitutions = new ArrayList<>();
        List<String> unavailable = new ArrayList<>();
        for (KitchenItemStatusView item : kitchenResponse.items()) {
            String originalName = itemNameById.getOrDefault(item.itemId(), item.itemId());
            if (item.available()) {
                continue;
            }
            if (item.substituteName() != null && !item.substituteName().isBlank()) {
                substitutions.add(japanese
                        ? originalName + " の代わりに " + item.substituteName()
                        : item.substituteName() + " instead of " + originalName);
                continue;
            }
            unavailable.add(originalName);
        }
        if (!substitutions.isEmpty()) {
            String replacementLine = japanese
                    ? "キッチンでは在庫状況を確認し、" + String.join("、", substitutions)
                            + " を用意できます。出来上がり目安は約" + kitchenResponse.readyInMinutes() + "分です。"
                    : "The kitchen checked live availability and can prepare " + String.join(", ", substitutions)
                            + ". Estimated prep time is about " + kitchenResponse.readyInMinutes() + " minutes.";
            if (unavailable.isEmpty()) {
                return replacementLine;
            }
            return replacementLine + " " + (japanese
                    ? "なお、" + String.join("、", unavailable) + " は現時点でご用意できません。"
                    : String.join(", ", unavailable) + " cannot be prepared right now.");
        }
        if (!unavailable.isEmpty()) {
            return japanese
                    ? "キッチンで在庫を確認したところ、" + String.join("、", unavailable)
                            + " は現在ご用意できません。出来上がり目安は約" + kitchenResponse.readyInMinutes() + "分です。"
                    : "The kitchen checked availability and cannot prepare " + String.join(", ", unavailable)
                            + " right now. Estimated prep time for the available items is about " + kitchenResponse.readyInMinutes() + " minutes.";
        }
        return japanese
                ? "キッチンではこの内容をそのまま用意でき、出来上がり目安は約" + kitchenResponse.readyInMinutes() + "分です。"
                : "The kitchen can prepare this lineup as-is, with an estimated prep time of about "
                        + kitchenResponse.readyInMinutes() + " minutes.";
    }

    private String describeSuggestedItems(List<MenuItemView> menuItems) {
        if (menuItems == null || menuItems.isEmpty()) {
            return "";
        }
        boolean japanese = menuItems.stream().map(MenuItemView::name).anyMatch(this::looksJapanese);
        String names = menuItems.stream().map(MenuItemView::name).reduce((left, right) -> left + ", " + right).orElse("");
        return japanese
                ? "今回のおすすめは " + names + " です。"
                : "Recommended items: " + names + ".";
    }

    private String describeDeliveryOptions(List<DeliveryOptionView> options, boolean japanese) {
        if (options == null || options.isEmpty()) {
            return "";
        }
        List<String> descriptions = options.stream()
                .map(option -> japanese
                        ? option.label() + " は約" + option.etaMinutes() + "分・配送料" + formatYen(option.fee())
                        : option.label() + " is about " + option.etaMinutes() + " min with a delivery fee of "
                                + formatYen(option.fee()))
                .toList();
        return japanese
                ? String.join("、", descriptions) + " です。"
                : String.join(", ", descriptions) + ".";
    }

    private String formatYen(BigDecimal amount) {
        if (amount == null) {
            return "¥0";
        }
        return "¥" + amount.stripTrailingZeros().toPlainString();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String userFacingSummary(String summary) {
        if (summary == null) {
            return "";
        }
        return summary
                .replaceFirst("^menu-agent\\s+", "")
                .replaceFirst("^kitchen-agent\\s+", "")
                .trim();
    }

    private boolean looksJapanese(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return text.chars().anyMatch(ch -> {
            Character.UnicodeScript script = Character.UnicodeScript.of(ch);
            return script == Character.UnicodeScript.HIRAGANA
                    || script == Character.UnicodeScript.KATAKANA
                    || script == Character.UnicodeScript.HAN;
        });
    }
}

@Component
class MenuGateway {

    private final RestClient restClient;

    MenuGateway(@Qualifier("menuRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    MenuSuggestionResponse suggest(MenuSuggestionRequest request) {
        return Objects.requireNonNull(restClient.post()
                .uri("/internal/menu/suggest")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(MenuSuggestionResponse.class));
    }
}

@Component
class KitchenGateway {

    private final RestClient restClient;

    KitchenGateway(@Qualifier("kitchenRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    KitchenCheckResponse check(KitchenCheckRequest request) {
        return Objects.requireNonNull(restClient.post()
                .uri("/internal/kitchen/check")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(KitchenCheckResponse.class));
    }
}

@Component
class DeliveryGateway {

    private final RestClient restClient;

    DeliveryGateway(@Qualifier("deliveryRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    DeliveryQuoteResponse quote(DeliveryQuoteRequest request) {
        return Objects.requireNonNull(restClient.post()
                .uri("/internal/delivery/quote")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(DeliveryQuoteResponse.class));
    }
}

@Component
class PaymentGateway {

    private final RestClient restClient;

    PaymentGateway(@Qualifier("paymentRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    PaymentPrepareResponse prepare(PaymentPrepareRequest request) {
        return Objects.requireNonNull(restClient.post()
                .uri("/internal/payment/prepare")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(PaymentPrepareResponse.class));
    }
}

@Component
class OrderRepository {

    private final JdbcClient jdbcClient;

    OrderRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    String saveConfirmedOrder(
            String customerId,
            List<OrderLineItem> items,
            BigDecimal subtotal,
            BigDecimal total,
            String etaLabel,
            String paymentStatus) {
        String orderId = "ord-" + UUID.randomUUID().toString().substring(0, 8);
        String itemSummary = items.stream()
                .map(item -> item.quantity() + "x " + item.name())
                .reduce((left, right) -> left + ", " + right)
                .orElse("draft pending");
        jdbcClient.sql("""
                insert into delivery_orders (order_id, customer_id, item_summary, subtotal, total, eta_label, payment_status, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """)
            .params(orderId, customerId, itemSummary, subtotal, total, etaLabel, paymentStatus, Timestamp.from(Instant.now()))
                .update();
        return orderId;
    }

    Optional<StoredOrder> findLatestOrderForUser(String customerId) {
        return jdbcClient.sql("""
                select order_id, item_summary, subtotal, total, eta_label, payment_status
                from delivery_orders
                where customer_id = ?
                order by created_at desc
                limit 1
                """)
                .param(customerId)
                .query(this::mapStoredOrder)
                .optional();
    }

    private StoredOrder mapStoredOrder(ResultSet resultSet, int rowNum) throws SQLException {
        return new StoredOrder(
                resultSet.getString("order_id"),
                resultSet.getString("item_summary"),
                resultSet.getBigDecimal("subtotal"),
                resultSet.getBigDecimal("total"),
                resultSet.getString("eta_label"),
                resultSet.getString("payment_status"));
    }
}

interface OrderSessionStore {

    Optional<OrderChatSession> load(String sessionId);

    void save(OrderChatSession session);
}

@Component
@ConditionalOnProperty(name = "delivery.order.session-store", havingValue = "redis", matchIfMissing = true)
class RedisOrderSessionStore implements OrderSessionStore {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    RedisOrderSessionStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<OrderChatSession> load(String sessionId) {
        String json = redisTemplate.opsForValue().get(key(sessionId));
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, OrderChatSession.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to read session " + sessionId, exception);
        }
    }

    @Override
    public void save(OrderChatSession session) {
        try {
            redisTemplate.opsForValue().set(key(session.sessionId()), objectMapper.writeValueAsString(session));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to save session " + session.sessionId(), exception);
        }
    }

    private String key(String sessionId) {
        return "delivery:chat-session:" + sessionId;
    }
}

@Component
@ConditionalOnProperty(name = "delivery.order.session-store", havingValue = "in-memory")
class InMemoryOrderSessionStore implements OrderSessionStore {

    private final ConcurrentMap<String, OrderChatSession> sessions = new ConcurrentHashMap<>();

    @Override
    public Optional<OrderChatSession> load(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    @Override
    public void save(OrderChatSession session) {
        sessions.put(session.sessionId(), session);
    }
}

@Configuration
class OrderArachneConfiguration {

    @Bean
    Tool recentOrderLookupTool(OrderRepository orderRepository) {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec("recent_order_lookup", "Read the most recent confirmed order for the active customer.", schema());
            }

            @Override
            public ToolResult invoke(Object input) {
                return invoke(input, new ToolInvocationContext("recent_order_lookup", null, input, null));
            }

            @Override
            public ToolResult invoke(Object input, ToolInvocationContext context) {
                String customerId = String.valueOf(values(input).getOrDefault("customerId", "demo-user"));
                StoredOrder order = orderRepository.findLatestOrderForUser(customerId)
                        .orElse(new StoredOrder("", "No previous order found", BigDecimal.ZERO, BigDecimal.ZERO, "", "PENDING"));
                return ToolResult.success(context.toolUseId(), Map.of(
                        "itemSummary", order.itemSummary(),
                        "etaLabel", order.etaLabel(),
                        "paymentStatus", order.paymentStatus()));
            }
        };
    }

    @Bean
    @ConditionalOnProperty(name = "delivery.model.mode", havingValue = "deterministic", matchIfMissing = false)
    Model orderDeterministicModel() {
        return new OrderDeterministicModel();
    }

    @Bean
    ApplicationRunner seedHistoricalOrder(OrderRepository orderRepository) {
        return args -> {
            if (orderRepository.findLatestOrderForUser("demo-user").isEmpty()) {
                orderRepository.saveConfirmedOrder(
                        "demo-user",
                        List.of(
                                new OrderLineItem("Crispy Chicken Box", 2, new BigDecimal("980.00"), "seed"),
                                new OrderLineItem("Curly Fries", 1, new BigDecimal("330.00"), "seed"),
                                new OrderLineItem("Lemon Soda", 2, new BigDecimal("240.00"), "seed")),
                        new BigDecimal("2530.00"),
                        new BigDecimal("2830.00"),
                        "18 min via In-house Express",
                        "CHARGED");
            }
        };
    }

    private static ObjectNode schema() {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("type", "object");
        ObjectNode properties = root.putObject("properties");
        properties.putObject("customerId").put("type", "string");
        root.putArray("required").add("customerId");
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

    private static final class OrderDeterministicModel implements Model {

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
            return coordinatorDeterministic(messages);
        }

        private Iterable<ModelEvent> coordinatorDeterministic(List<Message> messages) {
            String userText = latestUserText(messages);
            String normalized = userText == null ? "" : userText.toLowerCase();
            boolean confirm = normalized.contains("確定") || normalized.contains("confirm") || normalized.contains("注文して");
            boolean fastest = normalized.contains("最速") || normalized.contains("fast") || normalized.contains("急ぎ");

            Map<String, Object> menuResult = latestToolContent(messages, "suggest-menu");
            Map<String, Object> kitchenResult = latestToolContent(messages, "check-kitchen");
            Map<String, Object> deliveryResult = latestToolContent(messages, "quote-delivery");
            Map<String, Object> paymentResult = latestToolContent(messages, "prepare-payment");

            if (menuResult == null) {
                return List.of(
                        new ModelEvent.ToolUse("suggest-menu", "suggest_menu",
                                Map.of("message", userText != null ? userText : "")),
                        new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
            }
            if (kitchenResult == null) {
                List<?> itemIds = menuResult.get("itemIds") instanceof List<?> ids ? ids : List.of();
                return List.of(
                        new ModelEvent.ToolUse("check-kitchen", "check_kitchen",
                                Map.of("itemIds", itemIds, "message", userText != null ? userText : "")),
                        new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
            }
            if (deliveryResult == null) {
                List<?> itemNames = kitchenResult.get("itemNames") instanceof List<?> names ? names : List.of();
                return List.of(
                        new ModelEvent.ToolUse("quote-delivery", "quote_delivery",
                                Map.of("itemNames", itemNames, "message", userText != null ? userText : "")),
                        new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
            }
            if (paymentResult == null) {
                return List.of(
                        new ModelEvent.ToolUse("prepare-payment", "prepare_payment",
                                Map.of("confirmRequested", confirm, "fastestDelivery", fastest)),
                        new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
            }
            boolean charged = Boolean.TRUE.equals(paymentResult.get("charged"));
            String reply = charged
                    ? "Your order is now confirmed and payment has been processed."
                    : "Here is your draft order. Say 'confirm' to place it.";
            return List.of(
                    new ModelEvent.TextDelta(reply),
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
            return null;
        }
    }
}

record ChatRequest(String sessionId, String message) {}

record ChatResponse(
        String sessionId,
        List<ConversationMessage> conversation,
        String assistantMessage,
        OrderDraft draft,
        List<ServiceTrace> trace,
    RoutingDecision routing,
        List<String> suggestions,
        List<String> choices) {}

record ConversationMessage(String role, String text) {}

record ServiceTrace(String service, String agent, String headline, String detail, RoutingDecision routing) {}

record RoutingDecision(String intent, String selectedSkill, String entryStep, String reason) {}

record OrderDraft(
        String status,
        List<OrderLineItem> items,
        BigDecimal subtotal,
        BigDecimal total,
        String etaLabel,
        String paymentStatus,
        String paymentMethod,
        String orderId) {}

record OrderLineItem(String name, int quantity, BigDecimal unitPrice, String note) {}

record OrderChatSession(String sessionId, List<ConversationMessage> conversation, OrderDraft draft) {}

record StoredOrder(String orderId, String itemSummary, BigDecimal subtotal, BigDecimal total, String etaLabel, String paymentStatus) {}

record MenuSuggestionRequest(String sessionId, String message) {}

record MenuSuggestionResponse(String service, String agent, String headline, String summary, List<MenuItemView> items) {}

record MenuItemView(String id, String name, String description, BigDecimal price, int suggestedQuantity) {}

record KitchenCheckRequest(String sessionId, String message, List<String> itemIds) {}

record KitchenCheckResponse(
    String service,
    String agent,
    String headline,
    String summary,
    int readyInMinutes,
    List<KitchenItemStatusView> items,
    List<AgentCollaborationView> collaborations) {}

record KitchenItemStatusView(String itemId, boolean available, int prepMinutes, String substituteItemId, String substituteName, BigDecimal substitutePrice) {}

record AgentCollaborationView(String service, String agent, String headline, String summary) {}

record DeliveryQuoteRequest(String sessionId, String message, List<String> itemNames) {}

record DeliveryQuoteResponse(String service, String agent, String headline, String summary, List<DeliveryOptionView> options) {}

record DeliveryOptionView(String code, String label, int etaMinutes, BigDecimal fee) {}

record PaymentPrepareRequest(String sessionId, String message, BigDecimal total, boolean confirmRequested) {}

record PaymentPrepareResponse(
        String service,
        String agent,
        String headline,
        String summary,
        String selectedMethod,
        BigDecimal total,
        String paymentStatus,
        boolean charged,
        String authorizationId) {}