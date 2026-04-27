package com.mahitotsu.arachne.samples.delivery.supportservice.domain;

import java.util.Locale;
import java.util.Objects;

public record SupportIntent(
        boolean includesFaq,
        boolean includesCampaigns,
        boolean includesStatuses,
        boolean includesOrderHistory,
        boolean includesFeedback) {

    public static SupportIntent fromMessage(String message) {
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