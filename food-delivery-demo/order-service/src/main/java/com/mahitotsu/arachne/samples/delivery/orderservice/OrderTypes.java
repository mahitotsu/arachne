package com.mahitotsu.arachne.samples.delivery.orderservice;

import java.math.BigDecimal;
import java.util.List;

record SuggestOrderRequest(String sessionId, String message, String locale, String refinement) {
}

record ConfirmItemsRequest(String sessionId, List<SelectedProposalItem> items) {
}

record ConfirmDeliveryRequest(String sessionId, String deliveryCode) {
}

record ConfirmPaymentRequest(String sessionId) {
}

record SuggestOrderResponse(
        String sessionId,
        String workflowStep,
        String headline,
        String summary,
        int etaMinutes,
        List<ProposalItem> proposals,
        OrderDraft draft,
        List<ServiceTrace> trace) {
}

record ConfirmItemsResponse(
        String sessionId,
        String workflowStep,
        String headline,
        List<OrderLineItem> items,
        List<DeliveryOptionChoice> deliveryOptions,
        OrderDraft draft,
        List<ServiceTrace> trace) {
}

record ConfirmDeliveryResponse(
        String sessionId,
        String workflowStep,
        String headline,
        PaymentSummary payment,
        OrderDraft draft,
        List<ServiceTrace> trace) {
}

record ConfirmPaymentResponse(
        String sessionId,
        String workflowStep,
        String summary,
        OrderDraft draft,
        List<ServiceTrace> trace) {
}

record OrderSessionView(
        String sessionId,
        String workflowStep,
        OrderDraft draft,
        List<ProposalItem> pendingProposal,
        List<DeliveryOptionChoice> pendingDeliveryOptions) {
}

record ServiceTrace(String service, String agent, String headline, String detail) {
}

record ProposalItem(String itemId, String name, int quantity, BigDecimal unitPrice, String reason) {
}

record SelectedProposalItem(String itemId) {
}

record DeliveryOptionChoice(String code, String label, int etaMinutes, BigDecimal fee, String reason, boolean recommended) {
}

record PaymentSummary(List<OrderLineItem> items, BigDecimal deliveryFee, BigDecimal total, String paymentMethod) {
}

record OrderDraft(
        String status,
        List<OrderLineItem> items,
        BigDecimal subtotal,
        BigDecimal total,
        String etaLabel,
        String paymentStatus,
        String paymentMethod,
        String orderId) {
}

record OrderLineItem(String name, int quantity, BigDecimal unitPrice, String note) {
}

record OrderSession(
        String sessionId,
        String workflowStep,
        OrderDraft draft,
        PendingProposal pendingProposal,
        PendingDeliverySelection pendingDeliverySelection,
        DeliveryOptionChoice selectedDelivery) {
}

record PendingProposal(
        String customerMessage,
        String locale,
        String summary,
        List<ProposalItem> items,
        int etaMinutes,
        KitchenTraceView kitchenTrace) {
}

record PendingDeliverySelection(String summary, List<DeliveryOptionChoice> options) {
}

record StoredOrder(String orderId, String itemSummary, BigDecimal subtotal, BigDecimal total, String etaLabel, String paymentStatus) {
}

record StoredOrderSummary(String orderId, String itemSummary, BigDecimal total, String etaLabel, String paymentStatus, String createdAt) {
}

record MenuSuggestionRequest(String sessionId, String message) {
}

record MenuSuggestionResponse(
        String service,
        String agent,
        String headline,
        String summary,
        List<MenuItemView> items,
        int etaMinutes,
        KitchenTraceView kitchenTrace) {
}

record MenuItemView(String id, String name, String description, BigDecimal price, int suggestedQuantity,
        String category, List<String> tags) {
}

record KitchenTraceView(String summary, List<String> notes) {
}

record DeliveryQuoteRequest(String sessionId, String message, List<String> itemNames) {
}

record DeliveryQuoteResponse(
        String service,
        String agent,
        String headline,
        String summary,
        List<DeliveryOptionView> options,
        String recommendedTier,
        String recommendationReason) {
}

record DeliveryOptionView(String code, String label, int etaMinutes, BigDecimal fee) {
}

record PaymentPrepareRequest(String sessionId, String message, BigDecimal total, boolean confirmRequested) {
}

record PaymentPrepareResponse(
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

record SupportFeedbackRequestPayload(String orderId, Integer rating, String message) {
}

record SupportFeedbackResponse(
        String service,
        String agent,
        String headline,
        String summary,
        String classification,
        boolean escalationRequired) {
}

record RegistryServiceDescriptorPayload(
        String serviceName,
        String endpoint,
        String capability,
        String agentName,
        String requestMethod,
        String requestPath,
        String status) {
}