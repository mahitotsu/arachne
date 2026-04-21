package com.mahitotsu.arachne.samples.delivery.orderservice;

import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
        ArrayList<ConversationMessage> conversation = new ArrayList<>(existingSession.conversation());
        conversation.add(new ConversationMessage("user", request.message()));

        InterpretedRequest interpreted = InterpretedRequest.from(request.message());
        Optional<StoredOrder> recentOrder = orderRepository.findLatestOrderForUser(DEMO_CUSTOMER_ID);
        String workingMessage = interpreted.repeatLastOrder() && recentOrder.isPresent()
                ? request.message() + " | previous-order=" + recentOrder.get().itemSummary()
                : request.message();

        MenuSuggestionResponse menuResponse = menuGateway.suggest(new MenuSuggestionRequest(sessionId, workingMessage));
        KitchenCheckResponse kitchenResponse = kitchenGateway.check(new KitchenCheckRequest(
                sessionId,
                request.message(),
                menuResponse.items().stream().map(MenuItemView::id).toList()));
        List<OrderLineItem> lineItems = buildLineItems(menuResponse.items(), kitchenResponse.items());
        DeliveryQuoteResponse deliveryResponse = deliveryGateway.quote(new DeliveryQuoteRequest(
                sessionId,
                request.message(),
                lineItems.stream().map(OrderLineItem::name).toList()));
        DeliveryOptionView selectedDelivery = selectDeliveryOption(deliveryResponse.options(), interpreted.fastestDelivery());
        BigDecimal subtotal = subtotal(lineItems);
        BigDecimal total = subtotal.add(selectedDelivery.fee()).setScale(2, RoundingMode.HALF_UP);
        PaymentPrepareResponse paymentResponse = paymentGateway.prepare(new PaymentPrepareRequest(
                sessionId,
                request.message(),
                total,
                interpreted.confirmRequested()));

        String orderId = existingSession.draft().orderId();
        String status = "DRAFT_READY";
        if (paymentResponse.charged()) {
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
                paymentResponse.paymentStatus(),
                paymentResponse.selectedMethod(),
                orderId);

        String assistantMessage = agentFactory.builder()
                .systemPrompt("You are the order-agent. Summarize the multi-service plan in clear customer-facing language.")
                .tools(recentOrderLookupTool)
                .build()
                .run(buildAgentPrompt(request.message(), draft, interpreted, recentOrder.isPresent()))
                .text();

        conversation.add(new ConversationMessage("assistant", assistantMessage));
        OrderChatSession updated = new OrderChatSession(sessionId, List.copyOf(conversation), draft);
        sessionStore.save(updated);

        List<ServiceTrace> trace = List.of(
                new ServiceTrace(menuResponse.service(), menuResponse.agent(), menuResponse.headline(), menuResponse.summary()),
                new ServiceTrace(kitchenResponse.service(), kitchenResponse.agent(), kitchenResponse.headline(), kitchenResponse.summary()),
                new ServiceTrace(deliveryResponse.service(), deliveryResponse.agent(), deliveryResponse.headline(), deliveryResponse.summary()),
                new ServiceTrace(paymentResponse.service(), paymentResponse.agent(), paymentResponse.headline(), paymentResponse.summary()),
                new ServiceTrace("order-service", "order-agent", status.equals("CONFIRMED") ? "order-agent finalized the order" : "order-agent assembled the draft", assistantMessage));

        return new ChatResponse(sessionId, updated.conversation(), assistantMessage, draft, trace, defaultSuggestions());
    }

    ChatResponse session(String sessionId) {
        OrderChatSession session = sessionStore.load(sessionId).orElse(emptySession(sessionId));
        return new ChatResponse(session.sessionId(), session.conversation(), "", session.draft(), List.of(), defaultSuggestions());
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
            return new DeliveryOptionView("standard", "Standard route", 27, new BigDecimal("180.00"));
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

    private String buildAgentPrompt(String message, OrderDraft draft, InterpretedRequest interpreted, boolean historyAvailable) {
        String draftSummary = draft.items().stream()
                .map(item -> item.quantity() + "x " + item.name())
                .reduce((left, right) -> left + ", " + right)
                .orElse("draft pending");
        return String.join("\n",
                "historyRequested=" + interpreted.repeatLastOrder(),
                "historyAvailable=" + historyAvailable,
                "userMessage=" + message,
                "draftSummary=" + draftSummary,
                "eta=" + draft.etaLabel(),
                "paymentStatus=" + draft.paymentStatus(),
                "paymentMethod=" + draft.paymentMethod(),
                "confirmed=" + "CONFIRMED".equals(draft.status()),
                "total=" + draft.total());
    }

    private OrderChatSession emptySession(String sessionId) {
        return new OrderChatSession(sessionId, List.of(), emptyDraft());
    }

    private OrderDraft emptyDraft() {
        return new OrderDraft("EMPTY", List.of(), BigDecimal.ZERO, BigDecimal.ZERO, "", "PENDING", "", "");
    }

    private List<String> defaultSuggestions() {
        return List.of(
                "2人分でスパイシー少なめのおすすめを見せて",
                "子ども向けセットを足して",
                "前回と同じ量で最速配送にして",
                "この内容で注文確定して");
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
    @ConditionalOnProperty(name = "delivery.model.mode", havingValue = "deterministic", matchIfMissing = true)
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
                        "18 min via Express rider",
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
            Map<String, String> prompt = promptValues(messages);
            Map<String, Object> history = latestToolContent(messages, "recent-order-lookup");
            if (Boolean.parseBoolean(prompt.getOrDefault("historyRequested", "false")) && history == null) {
                return List.of(
                        new ModelEvent.ToolUse("recent-order-lookup", "recent_order_lookup", Map.of("customerId", "demo-user")),
                        new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
            }
            StringBuilder message = new StringBuilder();
            message.append("order-agent lined up ").append(prompt.getOrDefault("draftSummary", "a draft"))
                    .append(". Estimated arrival is ").append(prompt.getOrDefault("eta", "soon"))
                    .append(", and payment is ").append(prompt.getOrDefault("paymentStatus", "PENDING")).append(" via ")
                    .append(prompt.getOrDefault("paymentMethod", "the saved method")).append('.');
            if (history != null) {
                message.append(" It used your previous order as a baseline: ")
                        .append(history.getOrDefault("itemSummary", "no previous order")).append('.');
            }
            if (Boolean.parseBoolean(prompt.getOrDefault("confirmed", "false"))) {
                message.append(" The order is now confirmed.");
            } else {
                message.append(" Say 'confirm' whenever you want the charge to run.");
            }
            return List.of(
                    new ModelEvent.TextDelta(message.toString()),
                    new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
        }

        private Map<String, String> promptValues(List<Message> messages) {
            LinkedHashMap<String, String> values = new LinkedHashMap<>();
            String text = latestUserText(messages);
            if (text == null) {
                return values;
            }
            for (String line : text.split("\\R")) {
                int separator = line.indexOf('=');
                if (separator <= 0) {
                    continue;
                }
                values.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
            }
            return values;
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
        List<String> suggestions) {}

record ConversationMessage(String role, String text) {}

record ServiceTrace(String service, String agent, String headline, String detail) {}

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

record InterpretedRequest(boolean repeatLastOrder, boolean fastestDelivery, boolean confirmRequested) {

    static InterpretedRequest from(String message) {
        String normalized = message == null ? "" : message.toLowerCase();
        boolean repeat = normalized.contains("前回") || normalized.contains("same") || normalized.contains("いつも");
        boolean fastest = normalized.contains("最速") || normalized.contains("fast") || normalized.contains("急ぎ");
        boolean confirm = normalized.contains("確定") || normalized.contains("confirm") || normalized.contains("注文して");
        return new InterpretedRequest(repeat, fastest, confirm);
    }
}

record MenuSuggestionRequest(String sessionId, String message) {}

record MenuSuggestionResponse(String service, String agent, String headline, String summary, List<MenuItemView> items) {}

record MenuItemView(String id, String name, String description, BigDecimal price, int suggestedQuantity) {}

record KitchenCheckRequest(String sessionId, String message, List<String> itemIds) {}

record KitchenCheckResponse(String service, String agent, String headline, String summary, int readyInMinutes, List<KitchenItemStatusView> items) {}

record KitchenItemStatusView(String itemId, boolean available, int prepMinutes, String substituteItemId, String substituteName, BigDecimal substitutePrice) {}

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