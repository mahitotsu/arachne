package com.mahitotsu.arachne.samples.delivery.orderservice.domain;

import java.math.BigDecimal;
import java.util.List;

public final class OrderTypes {

    private OrderTypes() {
    }

    public record SuggestOrderRequest(String sessionId, String message, String locale, String refinement) {
    }

    public record ConfirmItemsRequest(String sessionId, List<SelectedProposalItem> items) {
    }

    public record ConfirmDeliveryRequest(String sessionId, String deliveryCode) {
    }

    public record ConfirmPaymentRequest(String sessionId) {
    }

    public record SuggestOrderResponse(
            String sessionId,
            String workflowStep,
            String headline,
            String summary,
            int etaMinutes,
            List<ProposalItem> proposals,
            OrderDraft draft,
            List<ServiceTrace> trace) {
    }

    public record ConfirmItemsResponse(
            String sessionId,
            String workflowStep,
            String headline,
            List<OrderLineItem> items,
            List<DeliveryOptionChoice> deliveryOptions,
            OrderDraft draft,
            List<ServiceTrace> trace) {
    }

    public record ConfirmDeliveryResponse(
            String sessionId,
            String workflowStep,
            String headline,
            PaymentSummary payment,
            OrderDraft draft,
            List<ServiceTrace> trace) {
    }

    public record ConfirmPaymentResponse(
            String sessionId,
            String workflowStep,
            String summary,
            OrderDraft draft,
            List<ServiceTrace> trace) {
    }

    public record OrderSessionView(
            String sessionId,
            String workflowStep,
            OrderDraft draft,
            List<ProposalItem> pendingProposal,
            List<DeliveryOptionChoice> pendingDeliveryOptions) {
    }

    public record ServiceTrace(String service, String agent, String headline, String detail) {
    }

    public record ProposalItem(String itemId, String name, int quantity, BigDecimal unitPrice, String reason) {
    }

    public record SelectedProposalItem(String itemId) {
    }

    public record DeliveryOptionChoice(String code, String label, int etaMinutes, BigDecimal fee, String reason, boolean recommended) {
    }

    public record PaymentSummary(List<OrderLineItem> items, BigDecimal deliveryFee, BigDecimal total, String paymentMethod) {
    }

    public record OrderDraft(
            String status,
            List<OrderLineItem> items,
            BigDecimal subtotal,
            BigDecimal total,
            String etaLabel,
            String paymentStatus,
            String paymentMethod,
            String orderId) {
    }

    public record OrderLineItem(String name, int quantity, BigDecimal unitPrice, String note) {
    }

    public record OrderSession(
            String sessionId,
            String workflowStep,
            OrderDraft draft,
            PendingProposal pendingProposal,
            PendingDeliverySelection pendingDeliverySelection,
            DeliveryOptionChoice selectedDelivery) {
    }

    public record PendingProposal(
            String customerMessage,
            String locale,
            String summary,
            List<ProposalItem> items,
            int etaMinutes,
            KitchenTraceView kitchenTrace) {
    }

    public record PendingDeliverySelection(String summary, List<DeliveryOptionChoice> options) {
    }

    public record StoredOrder(String orderId, String itemSummary, BigDecimal subtotal, BigDecimal total, String etaLabel, String paymentStatus) {
    }

    public record StoredOrderSummary(String orderId, String itemSummary, BigDecimal total, String etaLabel, String paymentStatus, String createdAt) {
    }

    public record MenuSuggestionRequest(String sessionId, String message) {
    }

    public record MenuSuggestionResponse(
            String service,
            String agent,
            String headline,
            String summary,
            List<MenuItemView> items,
            int etaMinutes,
            KitchenTraceView kitchenTrace) {
    }

    public record MenuItemView(String id, String name, String description, BigDecimal price, int suggestedQuantity,
            String category, List<String> tags) {
    }

    public record KitchenTraceView(String summary, List<String> notes) {
    }

    public record DeliveryQuoteRequest(String sessionId, String message, List<String> itemNames) {
    }

    public record DeliveryQuoteResponse(
            String service,
            String agent,
            String headline,
            String summary,
            List<DeliveryOptionView> options,
            String recommendedTier,
            String recommendationReason) {
    }

    public record DeliveryOptionView(String code, String label, int etaMinutes, BigDecimal fee) {
    }

    public record PaymentPrepareRequest(String sessionId, String message, BigDecimal total, boolean confirmRequested) {
    }

    public record PaymentPrepareResponse(
            String service,
            String agent,
            String headline,
            String summary,
            String selectedMethod,
            BigDecimal total,
            String paymentStatus,
            boolean charged,
            String authorizationId) {
    }

    public record SupportFeedbackRequestPayload(String orderId, Integer rating, String message) {
    }

    public record SupportFeedbackResponse(
            String service,
            String agent,
            String headline,
            String summary,
            String classification,
            boolean escalationRequired) {
    }

    public record RegistryServiceDescriptorPayload(
            String serviceName,
            String endpoint,
            String capability,
            String agentName,
            String requestMethod,
            String requestPath,
            String status) {
    }
}