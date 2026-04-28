package com.mahitotsu.arachne.samples.delivery.orderservice.domain;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public final class OrderTypes {

    private OrderTypes() {
    }

    @Schema(description = "Request for item suggestion or refinement in the order workflow.")
    public record SuggestOrderRequest(
            @Schema(description = "Existing workflow session id. Leave blank to start a new order flow.") String sessionId,
            @Schema(description = "Natural-language order intent for this turn.", example = "4人で4000円以内、子ども1人います") String message,
            @Schema(description = "Optional locale hint for the response language.", example = "ja-JP") String locale,
            @Schema(description = "Optional follow-up instruction that refines the previous suggestion.") String refinement) {
    }

    @Schema(description = "Selected proposal items to carry into delivery selection.")
    public record ConfirmItemsRequest(
            @Schema(description = "Workflow session id returned by the suggest step.") String sessionId,
            @Schema(description = "Items chosen from the proposal returned by suggest.") List<SelectedProposalItem> items) {
    }

    @Schema(description = "Selected delivery lane for the current workflow session.")
    public record ConfirmDeliveryRequest(
            @Schema(description = "Workflow session id returned by earlier steps.") String sessionId,
            @Schema(description = "Code of the selected delivery option.", example = "express") String deliveryCode) {
    }

    @Schema(description = "Explicit payment confirmation for the current workflow session.")
    public record ConfirmPaymentRequest(@Schema(description = "Workflow session id returned by earlier steps.") String sessionId) {
    }

    @Schema(description = "Order item suggestion and workflow snapshot returned to the client.")
    public record SuggestOrderResponse(
            @Schema(description = "Workflow session id to use in subsequent confirmation calls.") String sessionId,
            @Schema(description = "Current workflow stage after this operation.", example = "item-selection") String workflowStep,
            @Schema(description = "Short user-facing headline for the suggestion result.") String headline,
            @Schema(description = "Summary of why these items were suggested.") String summary,
            @Schema(description = "Estimated preparation and handoff time in minutes.") int etaMinutes,
            @Schema(description = "Suggested items for the current order intent.") List<ProposalItem> proposals,
            @Schema(description = "Current draft order state after suggestion.") OrderDraft draft,
            @Schema(description = "Trace of service and agent summaries used in this turn.") List<ServiceTrace> trace) {
    }

    @Schema(description = "Delivery candidates and updated draft after item confirmation.")
    public record ConfirmItemsResponse(
            @Schema(description = "Workflow session id.") String sessionId,
            @Schema(description = "Current workflow stage after item confirmation.", example = "delivery-selection") String workflowStep,
            @Schema(description = "Short user-facing headline for the delivery-selection step.") String headline,
            @Schema(description = "Normalized line items carried into checkout.") List<OrderLineItem> items,
            @Schema(description = "Available delivery options for the current draft.") List<DeliveryOptionChoice> deliveryOptions,
            @Schema(description = "Current draft order state.") OrderDraft draft,
            @Schema(description = "Trace of service and agent summaries used in this turn.") List<ServiceTrace> trace) {
    }

    @Schema(description = "Payment summary and updated draft after delivery confirmation.")
    public record ConfirmDeliveryResponse(
            @Schema(description = "Workflow session id.") String sessionId,
            @Schema(description = "Current workflow stage after delivery confirmation.", example = "payment") String workflowStep,
            @Schema(description = "Short user-facing headline for the payment step.") String headline,
            @Schema(description = "Payment summary prepared for explicit confirmation.") PaymentSummary payment,
            @Schema(description = "Current draft order state.") OrderDraft draft,
            @Schema(description = "Trace of service and agent summaries used in this turn.") List<ServiceTrace> trace) {
    }

    @Schema(description = "Final order confirmation result.")
    public record ConfirmPaymentResponse(
            @Schema(description = "Workflow session id.") String sessionId,
            @Schema(description = "Current workflow stage after payment confirmation.", example = "completed") String workflowStep,
            @Schema(description = "Final workflow summary shown to the customer.") String summary,
            @Schema(description = "Confirmed order draft, including final order id.") OrderDraft draft,
            @Schema(description = "Trace of service and agent summaries used in this turn.") List<ServiceTrace> trace) {
    }

    @Schema(description = "Restorable session snapshot for the browser workflow.")
    public record OrderSessionView(
            @Schema(description = "Workflow session id.") String sessionId,
            @Schema(description = "Current workflow stage.") String workflowStep,
            @Schema(description = "Current order draft snapshot.") OrderDraft draft,
            @Schema(description = "Pending proposal items, if the session is still in item selection.") List<ProposalItem> pendingProposal,
            @Schema(description = "Pending delivery options, if the session is still in delivery selection.") List<DeliveryOptionChoice> pendingDeliveryOptions) {
    }

    @Schema(description = "Service and agent trace entry returned for UI transparency.")
    public record ServiceTrace(
            @Schema(description = "Service boundary that produced the trace entry.") String service,
            @Schema(description = "Agent or component name responsible for the entry.") String agent,
            @Schema(description = "Short headline for the trace event.") String headline,
            @Schema(description = "Detailed trace explanation for the UI.") String detail) {
    }

    @Schema(description = "Suggested item returned by the item recommendation step.")
    public record ProposalItem(
            @Schema(description = "Stable catalog item id.") String itemId,
            @Schema(description = "Display name presented to the customer.") String name,
            @Schema(description = "Suggested quantity for this item.") int quantity,
            @Schema(description = "Unit price for this item.") BigDecimal unitPrice,
            @Schema(description = "Reason this item was suggested.") String reason) {
    }

    @Schema(description = "Selected proposal item passed back to the service for confirmation.")
    public record SelectedProposalItem(@Schema(description = "Stable catalog item id selected by the client.") String itemId) {
    }

    @Schema(description = "Delivery option available for the current order draft.")
    public record DeliveryOptionChoice(
            @Schema(description = "Stable delivery option code.") String code,
            @Schema(description = "Display label for the delivery option.") String label,
            @Schema(description = "Estimated arrival time in minutes.") int etaMinutes,
            @Schema(description = "Delivery fee charged for this option.") BigDecimal fee,
            @Schema(description = "Reason shown to the customer for this option.") String reason,
            @Schema(description = "Whether this option is the recommended one.") boolean recommended) {
    }

    @Schema(description = "Payment summary prepared after delivery selection.")
    public record PaymentSummary(
            @Schema(description = "Items included in the pending checkout.") List<OrderLineItem> items,
            @Schema(description = "Selected delivery fee.") BigDecimal deliveryFee,
            @Schema(description = "Final total that will be charged on confirmation.") BigDecimal total,
            @Schema(description = "Selected payment method label.") String paymentMethod) {
    }

    @Schema(description = "Current order draft maintained across the workflow.")
    public record OrderDraft(
            @Schema(description = "Current workflow-specific draft status.") String status,
            @Schema(description = "Current draft line items.") List<OrderLineItem> items,
            @Schema(description = "Current subtotal before delivery fee.") BigDecimal subtotal,
            @Schema(description = "Current total including delivery fee when available.") BigDecimal total,
            @Schema(description = "ETA label shown to the customer.") String etaLabel,
            @Schema(description = "Payment status at the current workflow stage.") String paymentStatus,
            @Schema(description = "Selected payment method label when available.") String paymentMethod,
            @Schema(description = "Confirmed order id once checkout completes.") String orderId) {
    }

    @Schema(description = "Normalized order line item used in draft and payment summaries.")
    public record OrderLineItem(
            @Schema(description = "Display name of the item.") String name,
            @Schema(description = "Quantity selected for this line.") int quantity,
            @Schema(description = "Unit price for this line.") BigDecimal unitPrice,
            @Schema(description = "Optional note shown alongside the line item.") String note) {
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

    @Schema(description = "Compact order history entry returned by the history endpoint.")
    public record StoredOrderSummary(
            @Schema(description = "Confirmed order id.") String orderId,
            @Schema(description = "Compact text summary of ordered items.") String itemSummary,
            @Schema(description = "Final charged total.") BigDecimal total,
            @Schema(description = "ETA label recorded for the order.") String etaLabel,
            @Schema(description = "Final payment status.") String paymentStatus,
            @Schema(description = "Creation timestamp for the confirmed order.") String createdAt) {
    }

        public record MenuSuggestionRequest(String sessionId, String query, String refinement, String recentOrderSummary) {

                public MenuSuggestionRequest(String sessionId, String query) {
                        this(sessionId, query, null, null);
                }
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