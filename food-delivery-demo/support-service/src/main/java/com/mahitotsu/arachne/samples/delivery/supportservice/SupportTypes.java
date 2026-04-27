package com.mahitotsu.arachne.samples.delivery.supportservice;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

record SupportIntent(
        boolean includesFaq,
        boolean includesCampaigns,
        boolean includesStatuses,
        boolean includesOrderHistory,
        boolean includesFeedback) {

    static SupportIntent fromMessage(String message) {
        String lowered = Objects.requireNonNullElse(message, "").toLowerCase(Locale.ROOT);
        boolean campaigns = containsAny(lowered, "キャンペーン", "クーポン", "割引", "特典", "ポイント");
        boolean statuses = containsAny(lowered, "稼働", "status", "状態", "配送状況", "営業", "available");
        boolean orderHistory = containsAny(lowered, "注文履歴", "履歴", "前回", "最近の注文", "いつもの");
        boolean feedback = containsAny(lowered, "問い合わせ", "クレーム", "苦情", "遅", "冷め", "違う", "誤配送");
        boolean faq = containsAny(lowered, "faq", "よくある", "支払い", "キャンセル", "注文方法", "配送")
                || (!campaigns && !statuses && !orderHistory && !feedback);
        return new SupportIntent(faq, campaigns, statuses, orderHistory, feedback);
    }

    private static boolean containsAny(String source, String... candidates) {
        for (String candidate : candidates) {
            if (source.contains(candidate)) {
                return true;
            }
        }
        return false;
    }
}

record HandoffInstruction(String target, String message) {

    static HandoffInstruction fromMessage(String message) {
        String lowered = Objects.requireNonNullElse(message, "").toLowerCase(Locale.ROOT);
        if (lowered.contains("注文") && (lowered.contains("変更") || lowered.contains("再注文") || lowered.contains("キャンセル") || lowered.contains("したい"))) {
            return new HandoffInstruction("order", "注文内容の変更や再注文は order-service の注文ワークフローへ引き継ぎます。");
        }
        return new HandoffInstruction("", "");
    }
}

record SupportChatRequest(String sessionId, String message) {
}

record SupportFeedbackRequest(String orderId, Integer rating, String message) {
}

record SupportChatResponse(
        String sessionId,
        String service,
        String agent,
        String headline,
        String summary,
        List<FaqEntry> faqMatches,
        List<CampaignSummary> campaigns,
        List<ServiceHealthSummary> serviceStatuses,
        List<CustomerOrderHistoryEntry> recentOrders,
        List<FeedbackInsight> relatedFeedback,
        String handoffTarget,
        String handoffMessage) {
}

record SupportFeedbackResponse(
        String service,
        String agent,
        String headline,
        String summary,
        String classification,
        boolean escalationRequired) {
}

record SupportStatusResponse(String service, String agent, String summary, List<ServiceHealthSummary> services) {
}

record FaqEntry(String id, String question, String answer, List<String> tags) {
}

record CampaignSummary(String campaignId, String title, String description, String badge, String validUntil) {
}

record ServiceHealthSummary(String serviceName, String status, String healthEndpoint) {
}

record CustomerOrderHistoryEntry(
        String orderId,
        String itemSummary,
        BigDecimal total,
        String etaLabel,
        String paymentStatus,
        String createdAt) {
}

record FeedbackInsight(
        String feedbackId,
        String orderId,
        String category,
        String summary,
        boolean escalationRequired,
        String createdAt) {
}

record SupportFeedbackRecord(
        String feedbackId,
        String customerId,
        String orderId,
        String classification,
        String summary,
        boolean escalationRequired,
        String createdAt) {
}

record RegistryHealthResponsePayload(List<RegistryHealthEntryPayload> services) {
}

record RegistryHealthEntryPayload(String serviceName, String status, String healthEndpoint) {
}

record RegistryServiceDescriptorPayload(String serviceName, String endpoint, String status) {
}