package com.mahitotsu.arachne.samples.delivery.deliveryservice.domain;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import io.swagger.v3.oas.annotations.media.Schema;

public final class DeliveryTypes {

    private DeliveryTypes() {
    }

        @Schema(description = "Delivery quote request.")
        public record DeliveryQuoteRequest(
            @Schema(description = "Correlation identifier for the parent workflow.") String sessionId,
            @Schema(description = "Natural-language delivery preference such as speed or price.", example = "最速配送でお願い") String message,
            @Schema(description = "Item names included in the order draft for quoting.") List<String> itemNames) {
    }

        @Schema(description = "Delivery quote response with ranked options and recommendation metadata.")
    public record DeliveryQuoteResponse(
            String service,
            String agent,
            String headline,
            String summary,
            List<DeliveryOption> options,
            String recommendedTier,
            String recommendationReason) {
    }

        public record DeliveryDecision(String summary, String recommendedTier, String recommendationReason) {
        }

    public record DeliveryOption(String code, String label, int etaMinutes, BigDecimal fee) {
    }

    public record CourierStatus(boolean expressAvailable, int expressReadyInMinutes, int standardReadyInMinutes) {
    }

    public record TrafficWeatherStatus(String trafficLevel, String weather, int trafficDelayMinutes, int weatherDelayMinutes) {
    }

    public record RegistryDiscoverRequestPayload(String query, Boolean availableOnly) {
    }

    public record RegistryDiscoverResponsePayload(String service, String agent, String summary, List<RegistryServiceMatchPayload> matches) {
    }

    public record RegistryServiceMatchPayload(
            String serviceName,
            String endpoint,
            String capability,
            String agentName,
            String systemPrompt,
            List<Map<String, Object>> skills,
            String requestMethod,
            String requestPath,
            String status) {
    }

    public record EtaServiceTarget(String serviceName, String url) {
    }

    public record AdapterEtaRequestPayload(List<String> itemNames, String context) {
    }

    public record AdapterEtaResponsePayload(String service, String status, int etaMinutes, String congestion, BigDecimal fee, String note) {
    }

    public record ExternalEtaQuote(String serviceName, int etaMinutes, String congestion, BigDecimal fee, String note) {
    }

    public record DeliveryRanking(List<DeliveryOption> options, String recommendedTier, String recommendationReason) {
    }

    public static final class DeliveryRankingPolicy {

        private DeliveryRankingPolicy() {
        }

        public static DeliveryRanking rank(List<DeliveryOption> options, String message) {
            List<DeliveryOption> safeOptions = options == null ? List.of() : List.copyOf(options);
            if (safeOptions.isEmpty()) {
                return new DeliveryRanking(List.of(), "", "現在利用可能な配送候補がありません。");
            }
            DeliveryPreference preference = preferenceFor(message);
            Comparator<DeliveryOption> comparator = switch (preference) {
                case CHEAP -> Comparator.comparing(DeliveryOption::fee, BigDecimal::compareTo)
                        .thenComparingInt(DeliveryOption::etaMinutes)
                        .thenComparing(DeliveryOption::code);
                case URGENT, BALANCED -> Comparator.comparingInt(DeliveryOption::etaMinutes)
                        .thenComparing(DeliveryOption::fee, BigDecimal::compareTo)
                        .thenComparing(DeliveryOption::code);
            };
            List<DeliveryOption> ranked = safeOptions.stream().sorted(comparator).toList();
            DeliveryOption best = ranked.get(0);
            String reason = switch (preference) {
                case CHEAP -> "「安く」の文脈なので最安の " + best.label() + " を優先しました。";
                case URGENT -> "「急いで」の文脈なので最短 ETA の " + best.label() + " を優先しました。";
                case BALANCED -> "現在の可用性では ETA と料金のバランスが良い " + best.label() + " を優先しました。";
            };
            return new DeliveryRanking(ranked, best.code(), reason);
        }

        private static DeliveryPreference preferenceFor(String message) {
            String normalized = Objects.requireNonNullElse(message, "").toLowerCase(Locale.ROOT);
            if (containsAny(normalized, "安く", "節約", "料金", "cheap", "budget")) {
                return DeliveryPreference.CHEAP;
            }
            if (containsAny(normalized, "急いで", "最速", "早く", "すぐ", "fast", "quick")) {
                return DeliveryPreference.URGENT;
            }
            return DeliveryPreference.BALANCED;
        }

        private static boolean containsAny(String value, String... candidates) {
            for (String candidate : candidates) {
                if (value.contains(candidate)) {
                    return true;
                }
            }
            return false;
        }
    }

    public enum DeliveryPreference {
        URGENT,
        CHEAP,
        BALANCED
    }
}