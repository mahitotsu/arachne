package com.mahitotsu.arachne.samples.delivery.supportservice.infrastructure;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.mahitotsu.arachne.samples.delivery.supportservice.domain.FaqEntry;

@Component
public class FaqRepository {

    private final List<FaqEntry> entries = List.of(
            new FaqEntry(
                    "faq-cancel",
                    "注文をキャンセルできますか？",
                    "調理前であれば注文チャットから変更やキャンセルを案内できます。必要なら support-agent が order-service への handoff を案内します。",
                    List.of("キャンセル", "注文変更", "refund")),
            new FaqEntry(
                    "faq-payment",
                    "使える支払い方法を教えてください。",
                    "現在はデモ用にカード決済を想定しています。支払い準備は payment-service が注文確定前に再計算します。",
                    List.of("支払い", "payment", "カード")),
            new FaqEntry(
                    "faq-delivery",
                    "配送が遅いときはどうなりますか？",
                    "配送状況は support-service から稼働状況を確認できます。再注文や配送変更が必要なら注文チャットへ引き継ぎます。",
                    List.of("配送", "遅延", "eta", "問い合わせ")));

    public List<FaqEntry> lookup(String query, int limit) {
        LinkedHashSet<String> terms = terms(query);
        return entries.stream()
                .map(entry -> new RankedFaq(entry, score(entry, terms)))
                .filter(rank -> rank.score() > 0)
                .sorted(Comparator.comparingInt(RankedFaq::score).reversed()
                        .thenComparing(rank -> rank.entry().id()))
                .map(RankedFaq::entry)
                .limit(limit)
                .toList();
    }

    private int score(FaqEntry entry, LinkedHashSet<String> terms) {
        String haystack = (entry.question() + " " + entry.answer() + " " + String.join(" ", entry.tags()))
                .toLowerCase(Locale.ROOT);
        int score = 0;
        for (String term : terms) {
            if (haystack.contains(term)) {
                score += 10;
            }
        }
        return score;
    }

    private LinkedHashSet<String> terms(String text) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        String lowered = Objects.requireNonNullElse(text, "").toLowerCase(Locale.ROOT);
        for (String token : lowered.split("[^\\p{IsAlphabetic}\\p{IsDigit}\\p{IsIdeographic}]+")) {
            if (!token.isBlank()) {
                terms.add(token);
            }
        }
        if (terms.isEmpty()) {
            terms.add("faq");
        }
        return terms;
    }

    private record RankedFaq(FaqEntry entry, int score) {
    }
}