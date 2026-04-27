package com.mahitotsu.arachne.samples.delivery.supportservice;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.springframework.stereotype.Component;

@Component
class FeedbackRepository {

    private final ConcurrentLinkedDeque<SupportFeedbackRecord> entries = new ConcurrentLinkedDeque<>();

    FeedbackRepository() {
        entries.add(new SupportFeedbackRecord(
                "fb-001",
                "cust-demo-001",
                "ord-0931",
                "DELAY",
                "雨の日に配送が15分遅れたという問い合わせ。",
                true,
                Instant.parse("2026-04-20T10:15:00Z").toString()));
        entries.add(new SupportFeedbackRecord(
                "fb-002",
                "cust-family-001",
                "ord-0924",
                "QUALITY",
                "ポテトが冷めていたという品質問い合わせ。",
                true,
                Instant.parse("2026-04-19T08:00:00Z").toString()));
        entries.add(new SupportFeedbackRecord(
                "fb-003",
                "cust-solo-001",
                "ord-0902",
                "SATISFACTION",
                "配達員の案内が丁寧だったという満足フィードバック。",
                false,
                Instant.parse("2026-04-18T12:30:00Z").toString()));
    }

    SupportFeedbackRecord record(String customerId, SupportFeedbackRequest request) {
        String classification = classify(request.message(), request.rating());
        boolean escalationRequired = needsEscalation(classification, request.rating(), request.message());
        SupportFeedbackRecord record = new SupportFeedbackRecord(
                "fb-" + Long.toHexString(System.nanoTime()),
                customerId,
                Objects.requireNonNullElse(request.orderId(), ""),
                classification,
                Objects.requireNonNullElse(request.message(), ""),
                escalationRequired,
                Instant.now().toString());
        entries.addFirst(record);
        return record;
    }

    List<FeedbackInsight> lookup(String query, int limit) {
        LinkedHashSet<String> terms = terms(query);
        return entries.stream()
                .map(entry -> new RankedFeedback(entry, score(entry, terms)))
                .filter(rank -> rank.score() > 0)
                .sorted(Comparator.comparingInt(RankedFeedback::score).reversed()
                        .thenComparing(rank -> rank.entry().feedbackId()))
                .map(rank -> new FeedbackInsight(
                        rank.entry().feedbackId(),
                        rank.entry().orderId(),
                        rank.entry().classification(),
                        rank.entry().summary(),
                        rank.entry().escalationRequired(),
                        rank.entry().createdAt()))
                .limit(limit)
                .toList();
    }

    private int score(SupportFeedbackRecord entry, LinkedHashSet<String> terms) {
        String haystack = (entry.classification() + " " + entry.summary()).toLowerCase(Locale.ROOT);
        int score = 0;
        for (String term : terms) {
            if (haystack.contains(term)) {
                score += 10;
            }
        }
        if (terms.contains("問い合わせ") || terms.contains("クレーム")) {
            score += 5;
        }
        return score;
    }

    private String classify(String message, Integer rating) {
        String lowered = Objects.requireNonNullElse(message, "").toLowerCase(Locale.ROOT);
        if (lowered.contains("遅") || lowered.contains("delay") || lowered.contains("届か")) {
            return "DELAY";
        }
        if (lowered.contains("冷め") || lowered.contains("品質") || lowered.contains("こぼ")) {
            return "QUALITY";
        }
        if (lowered.contains("違") || lowered.contains("誤") || lowered.contains("missing")) {
            return "WRONG_DELIVERY";
        }
        if (rating != null && rating >= 4) {
            return "SATISFACTION";
        }
        return "GENERAL";
    }

    private boolean needsEscalation(String classification, Integer rating, String message) {
        if (Objects.requireNonNullElse(message, "").contains("返金")) {
            return true;
        }
        if (rating != null && rating <= 2) {
            return true;
        }
        return switch (classification) {
            case "DELAY", "QUALITY", "WRONG_DELIVERY" -> true;
            default -> false;
        };
    }

    private LinkedHashSet<String> terms(String text) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        String lowered = Objects.requireNonNullElse(text, "").toLowerCase(Locale.ROOT);
        for (String token : lowered.split("[^\\p{IsAlphabetic}\\p{IsDigit}\\p{IsIdeographic}]+")) {
            if (!token.isBlank()) {
                terms.add(token);
            }
        }
        if (lowered.contains("問い合わせ") || lowered.contains("クレーム")) {
            terms.add("問い合わせ");
        }
        if (lowered.contains("遅")) {
            terms.add("delay");
            terms.add("遅延");
        }
        if (terms.isEmpty()) {
            terms.add("問い合わせ");
        }
        return terms;
    }

    private record RankedFeedback(SupportFeedbackRecord entry, int score) {
    }
}