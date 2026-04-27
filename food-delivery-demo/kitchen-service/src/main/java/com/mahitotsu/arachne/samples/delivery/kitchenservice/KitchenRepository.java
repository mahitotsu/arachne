package com.mahitotsu.arachne.samples.delivery.kitchenservice;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

@Component
class KitchenRepository {

    private static final Map<String, KitchenStockState> STOCK = Map.ofEntries(
            Map.entry("combo-crispy", stock(true, 14, "fry")),
            Map.entry("combo-smash", stock(true, 16, "grill")),
            Map.entry("combo-kids", stock(true, 9, "assembly")),
            Map.entry("combo-teriyaki", stock(true, 14, "grill")),
            Map.entry("combo-spicy-tuna", stock(true, 12, "assembly")),
            Map.entry("side-fries", stock(false, 7, "fry")),
            Map.entry("side-nuggets", stock(true, 8, "fry")),
            Map.entry("side-onion-rings", stock(true, 7, "fry")),
            Map.entry("drink-lemon", stock(true, 3, "drink")),
            Map.entry("drink-latte", stock(true, 4, "drink")),
            Map.entry("drink-matcha-latte", stock(true, 5, "drink")),
            Map.entry("wrap-garden", stock(true, 11, "assembly")),
            Map.entry("bowl-salmon", stock(true, 10, "assembly")),
            Map.entry("bowl-veggie", stock(true, 8, "assembly")),
            Map.entry("dessert-choco", stock(true, 6, "assembly")),
            Map.entry("dessert-matcha", stock(true, 4, "assembly")));

    private final ConcurrentMap<String, Integer> queueDepthByLine = new ConcurrentHashMap<>();

    List<KitchenItemStatus> check(List<String> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return List.of(toStatus("combo-crispy", STOCK.get("combo-crispy")));
        }
        return itemIds.stream()
                .map(itemId -> toStatus(itemId, STOCK.getOrDefault(itemId, stock(true, 12, "assembly"))))
                .toList();
    }

    PrepSchedule schedule(List<String> itemIds) {
        Map<String, Integer> maxPrepByLine = new LinkedHashMap<>();
        for (String itemId : itemIds) {
            KitchenStockState state = STOCK.getOrDefault(itemId, stock(true, 12, "assembly"));
            maxPrepByLine.merge(state.lineType(), state.prepMinutes(), Math::max);
        }
        if (maxPrepByLine.isEmpty()) {
            return new PrepSchedule(12, "assembly", "現在の負荷は軽く、通常どおり準備できます。", null);
        }

        int readyInMinutes = 0;
        String slowestLine = "assembly";
        for (Map.Entry<String, Integer> entry : maxPrepByLine.entrySet()) {
            int delay = queueDelayMinutes(entry.getKey());
            int candidateEta = entry.getValue() + delay;
            if (candidateEta >= readyInMinutes) {
                readyInMinutes = candidateEta;
                slowestLine = entry.getKey();
            }
        }

        String summary = slowestLine + "-line の見込みは約" + readyInMinutes + "分です。";
        String alternative = null;
        if ("grill".equals(slowestLine) && queueDelayMinutes("grill") > 15) {
            int assemblyEta = assemblyEtaMinutes();
            alternative = "現在 grill-line が混雑中です。assembly 系（サーモン丼など）であれば今すぐ約"
                    + assemblyEta + "分で提供できます。";
            summary += " " + alternative;
        }
        return new PrepSchedule(readyInMinutes, slowestLine, summary, alternative);
    }

    void setQueueDepth(String lineType, int depth) {
        if (depth <= 0) {
            queueDepthByLine.remove(lineType);
            return;
        }
        queueDepthByLine.put(lineType, depth);
    }

    void clearQueueDepths() {
        queueDepthByLine.clear();
    }

    String headline(List<KitchenItemStatus> statuses) {
        long unavailable = statuses.stream().filter(status -> !status.available()).count();
        if (unavailable == 0) {
            return "kitchen-agent が全アイテムの在庫を確認しました";
        }
        return "kitchen-agent が " + unavailable + " 件の代替点を検出しました";
    }

    String describe(List<String> itemIds) {
        List<KitchenItemStatus> statuses = check(itemIds);
        return describeStatuses(statuses);
    }

    String describeSchedule(List<String> itemIds) {
        return schedule(itemIds).summary();
    }

    String describeStatuses(List<KitchenItemStatus> statuses) {
        return statuses.stream()
                .map(status -> status.available()
                        ? status.itemId() + " は約" + status.prepMinutes() + "分で準備できます"
                        : status.itemId() + " は在庫切れのため代替品が必要です")
                .reduce((left, right) -> left + "; " + right)
                .orElse("キッチンは準備完了です");
    }

    private static KitchenStockState stock(boolean available, int prepMinutes, String lineType) {
        return new KitchenStockState(available, prepMinutes, lineType);
    }

    private static KitchenItemStatus toStatus(String itemId, KitchenStockState state) {
        return new KitchenItemStatus(itemId, state.available(), state.prepMinutes(), null, null, null);
    }

    private int queueDelayMinutes(String lineType) {
        return queueDepthByLine.getOrDefault(lineType, 0) * 4;
    }

    private int assemblyEtaMinutes() {
        int maxPrep = STOCK.values().stream()
                .filter(state -> "assembly".equals(state.lineType()))
                .mapToInt(KitchenStockState::prepMinutes)
                .max()
                .orElse(10);
        return maxPrep + queueDelayMinutes("assembly");
    }
}