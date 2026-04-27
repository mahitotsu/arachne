package com.mahitotsu.arachne.samples.delivery.orderservice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

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
                        "アイテム確定後に配送候補 capability を持つ協業先へ進みました。"));
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
                : "支払い準備 capability はまだ明示的な確定を待っています。";
        return new ConfirmPaymentResponse(
                session.sessionId(),
                updated.workflowStep(),
                summary,
                draft,
                trace);
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

    private List<ServiceTrace> buildConfirmPaymentTrace(
            PaymentPrepareResponse paymentResponse,
            SupportFeedbackResponse supportFeedback) {
        List<ServiceTrace> trace = new ArrayList<>();
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
                        : "支払い準備 capability はまだ確定を待っています。"));
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
                            "メニュー提案 capability を1回解決して協業先を呼び出しました。"));
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
                        "メニュー提案 capability を1回解決して協業先を呼び出しました。"));
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