package com.mahitotsu.arachne.samples.delivery.orderservice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootApplication
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }

    @Bean
    SecurityFilterChain orderSecurity(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> {
                }))
                .build();
    }

    @Bean
    ApplicationRunner registerOrderService(
            RestClient.Builder restClientBuilder,
            @Value("${DELIVERY_REGISTRY_BASE_URL:}") String registryBaseUrl,
            @Value("${DELIVERY_ORDER_ENDPOINT:http://order-service:8080}") String serviceEndpoint) {
        return args -> {
            if (registryBaseUrl.isBlank()) {
                return;
            }
            restClientBuilder.baseUrl(registryBaseUrl).build().post()
                    .uri("/registry/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "serviceName", "order-service",
                            "endpoint", serviceEndpoint,
                            "capability", "注文ワークフロー管理、メニュー提案、配送選択、支払い確認、注文履歴参照を扱う。",
                            "agentName", "order-service",
                            "systemPrompt", "注文ワークフローを段階的に進める。",
                            "skills", List.of(Map.of("name", "order-workflow", "content", "注文の提案から確定までの段階進行")),
                            "requestMethod", "POST",
                            "requestPath", "/api/order/suggest",
                            "healthEndpoint", serviceEndpoint + "/actuator/health",
                            "status", "AVAILABLE"))
                    .retrieve()
                    .toBodilessEntity();
        };
    }
}

@RestController
@RequestMapping(path = "/api/order", produces = MediaType.APPLICATION_JSON_VALUE)
class OrderController {

    private final OrderApplicationService applicationService;

    OrderController(OrderApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping(path = "/suggest", consumes = MediaType.APPLICATION_JSON_VALUE)
    SuggestOrderResponse suggest(@RequestBody SuggestOrderRequest request) {
        return applicationService.suggest(request);
    }

    @PostMapping(path = "/confirm-items", consumes = MediaType.APPLICATION_JSON_VALUE)
    ConfirmItemsResponse confirmItems(@RequestBody ConfirmItemsRequest request) {
        return applicationService.confirmItems(request);
    }

    @PostMapping(path = "/confirm-delivery", consumes = MediaType.APPLICATION_JSON_VALUE)
    ConfirmDeliveryResponse confirmDelivery(@RequestBody ConfirmDeliveryRequest request) {
        return applicationService.confirmDelivery(request);
    }

    @PostMapping(path = "/confirm-payment", consumes = MediaType.APPLICATION_JSON_VALUE)
    ConfirmPaymentResponse confirmPayment(@RequestBody ConfirmPaymentRequest request) {
        return applicationService.confirmPayment(request);
    }

    @GetMapping("/session/{sessionId}")
    OrderSessionView session(@PathVariable String sessionId) {
        return applicationService.session(sessionId);
    }
}

@RestController
@RequestMapping(path = "/api/orders", produces = MediaType.APPLICATION_JSON_VALUE)
class OrderHistoryController {

    private final OrderApplicationService applicationService;

    OrderHistoryController(OrderApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping("/history")
    List<StoredOrderSummary> orderHistory() {
        return applicationService.recentOrderHistory();
    }
}

@Service
class OrderApplicationService {

    private final OrderSessionStore sessionStore;
    private final OrderRepository orderRepository;
    private final MenuGateway menuGateway;
    private final DeliveryGateway deliveryGateway;
    private final PaymentGateway paymentGateway;
    private final SupportGateway supportGateway;
    private final AuthenticatedCustomerResolver authenticatedCustomerResolver;

    OrderApplicationService(
            OrderSessionStore sessionStore,
            OrderRepository orderRepository,
            MenuGateway menuGateway,
            DeliveryGateway deliveryGateway,
            PaymentGateway paymentGateway,
            SupportGateway supportGateway,
            AuthenticatedCustomerResolver authenticatedCustomerResolver) {
        this.sessionStore = sessionStore;
        this.orderRepository = orderRepository;
        this.menuGateway = menuGateway;
        this.deliveryGateway = deliveryGateway;
        this.paymentGateway = paymentGateway;
        this.supportGateway = supportGateway;
        this.authenticatedCustomerResolver = authenticatedCustomerResolver;
    }

    SuggestOrderResponse suggest(SuggestOrderRequest request) {
        String sessionId = sessionId(request.sessionId());
        OrderSession existing = sessionStore.load(sessionId).orElse(emptySession(sessionId));
        String accessToken = SecurityAccessors.requiredAccessToken();
        String suggestionMessage = buildSuggestionMessage(request, existing);

        MenuSuggestionResponse menuResponse = menuGateway.suggest(
                new MenuSuggestionRequest(sessionId, suggestionMessage),
                accessToken);
        List<ProposalItem> proposals = menuResponse.items().stream()
                .map(item -> new ProposalItem(
                        item.id(),
                        item.name(),
                        item.suggestedQuantity(),
                        item.price(),
                        proposalReason(menuResponse)))
                .toList();

        BigDecimal zero = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        OrderDraft draft = new OrderDraft(
                "PROPOSAL_READY",
                List.of(),
                zero,
                zero,
                menuResponse.etaMinutes() + " min kitchen prep",
                "PENDING",
                "",
                "");
        OrderSession updated = new OrderSession(
                sessionId,
                "item-selection",
                draft,
                new PendingProposal(
                        request.message(),
                        request.locale(),
                        menuResponse.summary(),
                        proposals,
                        menuResponse.etaMinutes(),
                        menuResponse.kitchenTrace()),
                null,
                null);
        sessionStore.save(updated);

        return new SuggestOrderResponse(
                sessionId,
                updated.workflowStep(),
                menuResponse.headline(),
                menuResponse.summary(),
                menuResponse.etaMinutes(),
                proposals,
                draft,
                buildSuggestTrace(menuResponse));
    }

    ConfirmItemsResponse confirmItems(ConfirmItemsRequest request) {
        OrderSession session = requiredSession(request.sessionId(), "item-selection");
        PendingProposal pendingProposal = requirePendingProposal(session.pendingProposal());
        String accessToken = SecurityAccessors.requiredAccessToken();

        List<ProposalItem> selectedItems = selectProposalItems(pendingProposal.items(), request.items());
        List<OrderLineItem> lineItems = selectedItems.stream()
                .map(item -> new OrderLineItem(item.name(), item.quantity(), item.unitPrice(), item.reason()))
                .toList();
        DeliveryQuoteResponse deliveryResponse = deliveryGateway.quote(
                new DeliveryQuoteRequest(
                        session.sessionId(),
                        pendingProposal.customerMessage(),
                        lineItems.stream().map(OrderLineItem::name).toList()),
                accessToken);
        List<DeliveryOptionChoice> deliveryOptions = toDeliveryChoices(deliveryResponse);
        BigDecimal subtotal = subtotal(lineItems);
        OrderDraft draft = new OrderDraft(
                "ITEMS_CONFIRMED",
                lineItems,
                subtotal,
                subtotal,
                session.draft().etaLabel(),
                "PENDING",
                "",
                session.draft().orderId());
        OrderSession updated = new OrderSession(
                session.sessionId(),
                "delivery-selection",
                draft,
                null,
                new PendingDeliverySelection(deliveryResponse.summary(), deliveryOptions),
                null);
        sessionStore.save(updated);

        List<ServiceTrace> trace = List.of(
                new ServiceTrace(
                        deliveryResponse.service(),
                        deliveryResponse.agent(),
                        deliveryResponse.headline(),
                        deliveryResponse.summary()),
                new ServiceTrace(
                        "order-service",
                        "order-workflow",
                        "order-service が配送候補を返しました",
                        "アイテム確定後に delivery-service へ進みました。"));
        return new ConfirmItemsResponse(
                session.sessionId(),
                updated.workflowStep(),
                "配送候補を返しました",
                lineItems,
                deliveryOptions,
                draft,
                trace);
    }

    ConfirmDeliveryResponse confirmDelivery(ConfirmDeliveryRequest request) {
        OrderSession session = requiredSession(request.sessionId(), "delivery-selection");
        PendingDeliverySelection pendingDeliverySelection = requirePendingDeliverySelection(session.pendingDeliverySelection());
        DeliveryOptionChoice selectedDelivery = selectDelivery(pendingDeliverySelection.options(), request.deliveryCode());
        String accessToken = SecurityAccessors.requiredAccessToken();

        BigDecimal subtotal = session.draft().subtotal();
        BigDecimal total = subtotal.add(selectedDelivery.fee()).setScale(2, RoundingMode.HALF_UP);
        PaymentPrepareResponse paymentResponse = paymentGateway.prepare(
                new PaymentPrepareRequest(session.sessionId(), "", total, false),
                accessToken);
        OrderDraft draft = new OrderDraft(
                "PAYMENT_READY",
                session.draft().items(),
                subtotal,
                total,
                selectedDelivery.label() + " / " + selectedDelivery.etaMinutes() + " min",
                paymentResponse.paymentStatus(),
                paymentResponse.selectedMethod(),
                session.draft().orderId());
        OrderSession updated = new OrderSession(
                session.sessionId(),
                "payment",
                draft,
                null,
                null,
                selectedDelivery);
        sessionStore.save(updated);

        List<ServiceTrace> trace = List.of(
                new ServiceTrace(
                        paymentResponse.service(),
                        paymentResponse.agent(),
                        paymentResponse.headline(),
                        paymentResponse.summary()),
                new ServiceTrace(
                        "order-service",
                        "order-workflow",
                        "order-service が支払い準備へ進みました",
                        selectedDelivery.label() + " を選択して合計を確定しました。"));
        return new ConfirmDeliveryResponse(
                session.sessionId(),
                updated.workflowStep(),
                "支払い準備が整いました",
                new PaymentSummary(draft.items(), selectedDelivery.fee(), draft.total(), paymentResponse.selectedMethod()),
                draft,
                trace);
    }

    ConfirmPaymentResponse confirmPayment(ConfirmPaymentRequest request) {
        OrderSession session = requiredSession(request.sessionId(), "payment");
        DeliveryOptionChoice selectedDelivery = requireSelectedDelivery(session.selectedDelivery());
        String accessToken = SecurityAccessors.requiredAccessToken();
        PaymentPrepareResponse paymentResponse = paymentGateway.prepare(
                new PaymentPrepareRequest(session.sessionId(), "", session.draft().total(), true),
                accessToken);

        String orderId = session.draft().orderId();
        String status = session.draft().status();
        SupportFeedbackResponse supportFeedback = null;
        if (paymentResponse.charged()) {
            orderId = orderRepository.saveConfirmedOrder(
                    authenticatedCustomerResolver.currentCustomerId(),
                    session.draft().items(),
                    session.draft().subtotal(),
                    session.draft().total(),
                    selectedDelivery.etaMinutes() + " min via " + selectedDelivery.label(),
                    paymentResponse.paymentStatus());
            status = "CONFIRMED";
                    supportFeedback = supportGateway.recordFeedback(
                        new SupportFeedbackRequestPayload(
                            orderId,
                            null,
                            confirmedOrderFeedbackMessage(orderId, session.draft(), selectedDelivery)),
                        accessToken)
                        .orElse(null);
        }

        OrderDraft draft = new OrderDraft(
                status,
                session.draft().items(),
                session.draft().subtotal(),
                session.draft().total(),
                session.draft().etaLabel(),
                paymentResponse.paymentStatus(),
                paymentResponse.selectedMethod(),
                orderId);
        OrderSession updated = new OrderSession(
                session.sessionId(),
                paymentResponse.charged() ? "completed" : "payment",
                draft,
                null,
                null,
                selectedDelivery);
        sessionStore.save(updated);

        List<ServiceTrace> trace = buildConfirmPaymentTrace(paymentResponse, supportFeedback);
        String summary = paymentResponse.charged()
                ? "注文を確定しました。合計は " + formatYen(draft.total()) + " です。"
                : "payment-service がまだ明示的な確定を待っています。";
        return new ConfirmPaymentResponse(
                session.sessionId(),
                updated.workflowStep(),
                summary,
                draft,
                trace);
    }

            private List<ServiceTrace> buildConfirmPaymentTrace(
                PaymentPrepareResponse paymentResponse,
                SupportFeedbackResponse supportFeedback) {
            List<ServiceTrace> trace = new java.util.ArrayList<>();
            trace.add(new ServiceTrace(
                paymentResponse.service(),
                paymentResponse.agent(),
                paymentResponse.headline(),
                paymentResponse.summary()));
            if (supportFeedback != null) {
                trace.add(new ServiceTrace(
                    supportFeedback.service(),
                    supportFeedback.agent(),
                    supportFeedback.headline(),
                    supportFeedback.summary()));
            }
            trace.add(new ServiceTrace(
                "order-service",
                "order-workflow",
                paymentResponse.charged() ? "order-service が注文を確定しました" : "order-service が支払い待ちです",
                paymentResponse.charged()
                    ? "支払い完了後に注文を保存しました。"
                    : "payment-service はまだ確定を待っています。"));
            return List.copyOf(trace);
            }

            private String confirmedOrderFeedbackMessage(
                String orderId,
                OrderDraft draft,
                DeliveryOptionChoice selectedDelivery) {
            String itemSummary = draft.items().stream()
                .map(item -> item.quantity() + "x " + item.name())
                .reduce((left, right) -> left + ", " + right)
                .orElse("items pending");
            return "注文 " + orderId + " が確定しました。"
                + " items=" + itemSummary
                + " total=" + formatYen(draft.total())
                + " delivery=" + selectedDelivery.label()
                + " eta=" + selectedDelivery.etaMinutes() + " min";
            }

    OrderSessionView session(String sessionId) {
        OrderSession session = sessionStore.load(sessionId).orElse(emptySession(sessionId));
        List<ProposalItem> pendingProposal = session.pendingProposal() == null ? List.of() : session.pendingProposal().items();
        List<DeliveryOptionChoice> pendingDeliveryOptions = session.pendingDeliverySelection() == null
                ? List.of()
                : session.pendingDeliverySelection().options();
        return new OrderSessionView(
                session.sessionId(),
                session.workflowStep(),
                session.draft(),
                pendingProposal,
                pendingDeliveryOptions);
    }

    List<StoredOrderSummary> recentOrderHistory() {
        return orderRepository.findRecentOrdersForUser(authenticatedCustomerResolver.currentCustomerId(), 5);
    }

    private String buildSuggestionMessage(SuggestOrderRequest request, OrderSession existing) {
        String baseMessage = firstNonBlank(
                request.message(),
                existing.pendingProposal() == null ? null : existing.pendingProposal().customerMessage());
        String refinement = request.refinement() == null || request.refinement().isBlank()
                ? ""
                : "\nrefinement=" + request.refinement().trim();
        String recentOrderContext = needsRecentOrderContext(baseMessage)
                ? orderRepository.findLatestOrderForUser(authenticatedCustomerResolver.currentCustomerId())
                        .map(order -> "\nrecent_order=" + order.itemSummary())
                        .orElse("\nrecent_order=none")
                : "";
        return baseMessage + refinement + recentOrderContext;
    }

    private boolean needsRecentOrderContext(String message) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        return normalized.contains("前回") || normalized.contains("いつもの") || normalized.contains("same as last time");
    }

    private String proposalReason(MenuSuggestionResponse menuResponse) {
        if (menuResponse.kitchenTrace() == null || menuResponse.kitchenTrace().summary() == null
                || menuResponse.kitchenTrace().summary().isBlank()) {
            return menuResponse.summary();
        }
        return menuResponse.summary() + " " + menuResponse.kitchenTrace().summary();
    }

    private List<ServiceTrace> buildSuggestTrace(MenuSuggestionResponse menuResponse) {
        if (menuResponse.kitchenTrace() == null) {
            return List.of(
                    new ServiceTrace(
                            menuResponse.service(),
                            menuResponse.agent(),
                            menuResponse.headline(),
                            menuResponse.summary()),
                    new ServiceTrace(
                            "order-service",
                            "order-workflow",
                            "order-service が提案ステップを開始しました",
                            "menu-service の suggest_menu_and_check を1回呼び出しました。"));
        }
        return List.of(
                new ServiceTrace(
                        menuResponse.service(),
                        menuResponse.agent(),
                        menuResponse.headline(),
                        menuResponse.summary()),
                new ServiceTrace(
                        "kitchen-service/support",
                        "kitchen-agent",
                        "kitchen-service が提供可否を返しました",
                        menuResponse.kitchenTrace().summary()),
                new ServiceTrace(
                        "order-service",
                        "order-workflow",
                        "order-service が提案ステップを開始しました",
                        "menu-service の suggest_menu_and_check を1回呼び出しました。"));
    }

    private List<ProposalItem> selectProposalItems(List<ProposalItem> proposals, List<SelectedProposalItem> selectedItems) {
        if (selectedItems == null || selectedItems.isEmpty()) {
            return proposals;
        }
        Map<String, ProposalItem> byId = proposals.stream()
                .collect(LinkedHashMap::new, (map, item) -> map.put(item.itemId(), item), Map::putAll);
        return selectedItems.stream()
                .map(item -> byId.get(item.itemId()))
                .filter(Objects::nonNull)
                .toList();
    }

    private List<DeliveryOptionChoice> toDeliveryChoices(DeliveryQuoteResponse response) {
        String recommendedCode = firstNonBlank(
            response.recommendedTier(),
            response.options().isEmpty() ? "" : response.options().get(0).code());
        return response.options().stream()
                .map(option -> new DeliveryOptionChoice(
                        option.code(),
                        option.label(),
                        option.etaMinutes(),
                        option.fee(),
                        response.summary(),
                        option.code().equals(recommendedCode)))
                .toList();
    }

    private DeliveryOptionChoice selectDelivery(List<DeliveryOptionChoice> options, String deliveryCode) {
        return options.stream()
                .filter(option -> option.code().equalsIgnoreCase(deliveryCode))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown delivery option: " + deliveryCode));
    }

    private OrderSession requiredSession(String sessionId, String expectedWorkflowStep) {
        OrderSession session = sessionStore.load(sessionId(sessionId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown session: " + sessionId));
        if (!expectedWorkflowStep.equals(session.workflowStep())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Workflow step mismatch. Expected " + expectedWorkflowStep + " but was " + session.workflowStep());
        }
        return session;
    }

    private PendingProposal requirePendingProposal(PendingProposal pendingProposal) {
        if (pendingProposal == null || pendingProposal.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No pending proposal is available");
        }
        return pendingProposal;
    }

    private PendingDeliverySelection requirePendingDeliverySelection(PendingDeliverySelection pendingDeliverySelection) {
        if (pendingDeliverySelection == null || pendingDeliverySelection.options().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No delivery options are available");
        }
        return pendingDeliverySelection;
    }

    private DeliveryOptionChoice requireSelectedDelivery(DeliveryOptionChoice selectedDelivery) {
        if (selectedDelivery == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No delivery option has been selected");
        }
        return selectedDelivery;
    }

    private OrderSession emptySession(String sessionId) {
        return new OrderSession(sessionId, "initial", emptyDraft(), null, null, null);
    }

    private OrderDraft emptyDraft() {
        BigDecimal zero = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        return new OrderDraft("INITIAL", List.of(), zero, zero, "", "PENDING", "", "");
    }

    private String sessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return "session-" + UUID.randomUUID().toString().substring(0, 8);
        }
        return sessionId;
    }

    private BigDecimal subtotal(List<OrderLineItem> items) {
        return items.stream()
                .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String formatYen(BigDecimal amount) {
        return "¥" + amount.stripTrailingZeros().toPlainString();
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

    List<StoredOrderSummary> findRecentOrdersForUser(String customerId, int limit) {
        return jdbcClient.sql("""
                select order_id, item_summary, total, eta_label, payment_status, created_at
                from delivery_orders
                where customer_id = ?
                order by created_at desc
                limit ?
                """)
                .params(customerId, limit)
                .query(this::mapStoredOrderSummary)
                .list();
    }

    private StoredOrder mapStoredOrder(ResultSet resultSet, int row) throws SQLException {
        return new StoredOrder(
                resultSet.getString("order_id"),
                resultSet.getString("item_summary"),
                resultSet.getBigDecimal("subtotal"),
                resultSet.getBigDecimal("total"),
                resultSet.getString("eta_label"),
                resultSet.getString("payment_status"));
    }

    private StoredOrderSummary mapStoredOrderSummary(ResultSet resultSet, int row) throws SQLException {
        Timestamp createdAt = resultSet.getTimestamp("created_at");
        return new StoredOrderSummary(
                resultSet.getString("order_id"),
                resultSet.getString("item_summary"),
                resultSet.getBigDecimal("total"),
                resultSet.getString("eta_label"),
                resultSet.getString("payment_status"),
                createdAt == null ? "" : createdAt.toInstant().toString());
    }
}

interface OrderSessionStore {

    Optional<OrderSession> load(String sessionId);

    void save(OrderSession session);
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
    public Optional<OrderSession> load(String sessionId) {
        String json = redisTemplate.opsForValue().get(key(sessionId));
        if (json == null || json.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, OrderSession.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to read session " + sessionId, exception);
        }
    }

    @Override
    public void save(OrderSession session) {
        try {
            redisTemplate.opsForValue().set(key(session.sessionId()), objectMapper.writeValueAsString(session));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to save session " + session.sessionId(), exception);
        }
    }

    private String key(String sessionId) {
        return "delivery:workflow-session:" + SecurityAccessors.currentCustomerId() + ":" + sessionId;
    }
}

@Component
@ConditionalOnProperty(name = "delivery.order.session-store", havingValue = "in-memory")
class InMemoryOrderSessionStore implements OrderSessionStore {

    private final ConcurrentMap<String, OrderSession> sessions = new ConcurrentHashMap<>();

    @Override
    public Optional<OrderSession> load(String sessionId) {
        return Optional.ofNullable(sessions.get(key(sessionId)));
    }

    @Override
    public void save(OrderSession session) {
        sessions.put(key(session.sessionId()), session);
    }

    private String key(String sessionId) {
        return SecurityAccessors.currentCustomerId() + ":" + sessionId;
    }
}

@Component
class AuthenticatedCustomerResolver {

    String currentCustomerId() {
        return SecurityAccessors.currentCustomerId();
    }
}

@Configuration
class OrderConfiguration {

    @Bean
    ApplicationRunner seedHistoricalOrder(OrderRepository orderRepository) {
        return args -> {
            if (orderRepository.findLatestOrderForUser("cust-demo-001").isEmpty()) {
                orderRepository.saveConfirmedOrder(
                        "cust-demo-001",
                        List.of(
                                new OrderLineItem("Crispy Chicken Box", 2, new BigDecimal("980.00"), "初期データ"),
                                new OrderLineItem("Curly Fries", 1, new BigDecimal("330.00"), "初期データ"),
                                new OrderLineItem("Lemon Soda", 2, new BigDecimal("240.00"), "初期データ")),
                        new BigDecimal("2530.00"),
                        new BigDecimal("2830.00"),
                        "18 分・自社エクスプレス",
                        "CHARGED");
            }
        };
    }
}

final class SecurityAccessors {

    private SecurityAccessors() {
    }

    static String currentCustomerId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            return jwtAuthenticationToken.getToken().getSubject();
        }
        throw new IllegalStateException("No authenticated customer is available in the security context");
    }

    static Optional<String> currentAccessToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            return Optional.of(jwtAuthenticationToken.getToken().getTokenValue());
        }
        return Optional.empty();
    }

    static String requiredAccessToken() {
        return currentAccessToken()
                .orElseThrow(() -> new IllegalStateException("No access token is available in the security context"));
    }
}

record SuggestOrderRequest(String sessionId, String message, String locale, String refinement) {}

record ConfirmItemsRequest(String sessionId, List<SelectedProposalItem> items) {}

record ConfirmDeliveryRequest(String sessionId, String deliveryCode) {}

record ConfirmPaymentRequest(String sessionId) {}

record SuggestOrderResponse(
        String sessionId,
        String workflowStep,
        String headline,
        String summary,
        int etaMinutes,
        List<ProposalItem> proposals,
        OrderDraft draft,
        List<ServiceTrace> trace) {}

record ConfirmItemsResponse(
        String sessionId,
        String workflowStep,
        String headline,
        List<OrderLineItem> items,
        List<DeliveryOptionChoice> deliveryOptions,
        OrderDraft draft,
        List<ServiceTrace> trace) {}

record ConfirmDeliveryResponse(
        String sessionId,
        String workflowStep,
        String headline,
        PaymentSummary payment,
        OrderDraft draft,
        List<ServiceTrace> trace) {}

record ConfirmPaymentResponse(
        String sessionId,
        String workflowStep,
        String summary,
        OrderDraft draft,
        List<ServiceTrace> trace) {}

record OrderSessionView(
        String sessionId,
        String workflowStep,
        OrderDraft draft,
        List<ProposalItem> pendingProposal,
        List<DeliveryOptionChoice> pendingDeliveryOptions) {}

record ServiceTrace(String service, String agent, String headline, String detail) {}

record ProposalItem(String itemId, String name, int quantity, BigDecimal unitPrice, String reason) {}

record SelectedProposalItem(String itemId) {}

record DeliveryOptionChoice(String code, String label, int etaMinutes, BigDecimal fee, String reason, boolean recommended) {}

record PaymentSummary(List<OrderLineItem> items, BigDecimal deliveryFee, BigDecimal total, String paymentMethod) {}

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

record OrderSession(
        String sessionId,
        String workflowStep,
        OrderDraft draft,
        PendingProposal pendingProposal,
        PendingDeliverySelection pendingDeliverySelection,
        DeliveryOptionChoice selectedDelivery) {}

record PendingProposal(
        String customerMessage,
        String locale,
        String summary,
        List<ProposalItem> items,
        int etaMinutes,
        KitchenTraceView kitchenTrace) {}

record PendingDeliverySelection(String summary, List<DeliveryOptionChoice> options) {}

record StoredOrder(String orderId, String itemSummary, BigDecimal subtotal, BigDecimal total, String etaLabel, String paymentStatus) {}

record StoredOrderSummary(String orderId, String itemSummary, BigDecimal total, String etaLabel, String paymentStatus, String createdAt) {}

record MenuSuggestionRequest(String sessionId, String message) {}

record MenuSuggestionResponse(
        String service,
        String agent,
        String headline,
        String summary,
        List<MenuItemView> items,
        int etaMinutes,
        KitchenTraceView kitchenTrace) {}

record MenuItemView(String id, String name, String description, BigDecimal price, int suggestedQuantity) {}

record KitchenTraceView(String summary, List<String> notes) {}

record DeliveryQuoteRequest(String sessionId, String message, List<String> itemNames) {}

record DeliveryQuoteResponse(
    String service,
    String agent,
    String headline,
    String summary,
    List<DeliveryOptionView> options,
    String recommendedTier,
    String recommendationReason) {}

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

    record SupportFeedbackRequestPayload(String orderId, Integer rating, String message) {}

    record SupportFeedbackResponse(
        String service,
        String agent,
        String headline,
        String summary,
        String classification,
        boolean escalationRequired) {}

    record RegistryServiceDescriptorPayload(String serviceName, String endpoint, String status) {}