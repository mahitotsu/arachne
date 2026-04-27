package com.mahitotsu.arachne.samples.delivery.menuservice;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
class MenuRepository {

    private static final Pattern YEN_BUDGET_PATTERN = Pattern.compile("(\\d{3,5})\\s*円");
    private static final Pattern CHILD_COUNT_PATTERN = Pattern.compile("(?:子ども|子供|kids?)\\s*(\\d+)人?");

    private static final List<MenuItem> ITEMS = List.of(
            menuItem("combo-crispy", "Crispy Chicken Box", "クリスピーチキン、フライドポテト、レモンソーダのセット。", "980.00", "combo", "chicken", "fry", "popular"),
            menuItem("combo-smash", "Smash Burger Combo", "ダブルスマッシュバーガーにフライドポテトとコーラが付いたコンボ。", "1050.00", "combo", "burger", "grill", "popular"),
            menuItem("combo-kids", "Kids Cheeseburger Set", "ミニチーズバーガー、コーンカップ、アップルジュースのキッズセット。", "720.00", "combo", "kids", "mild", "small-portion"),
            menuItem("combo-teriyaki", "Teriyaki Chicken Box", "照り焼きチキン、ゴハン、味噌汁のセット。", "920.00", "combo", "chicken", "grill", "japanese"),
            menuItem("combo-spicy-tuna", "Spicy Tuna Rice Box", "旬のスパイシーツナをすし飯にのせ、紅ショウガを添えたライスボックス。", "1080.00", "combo", "fish", "spicy", "japanese"),
            menuItem("side-fries", "Curly Fries", "スパイスの効いたカーリーフライ。", "330.00", "side", "vegetarian", "fry"),
            menuItem("side-nuggets", "Nugget Share Box", "ソース付き10個入りナゲットボックス。", "640.00", "side", "chicken", "fry", "kids"),
            menuItem("side-onion-rings", "Crispy Onion Rings", "ビール衣のオニオンリング、チポトレディップ添え。", "380.00", "side", "vegetarian", "fry"),
            menuItem("drink-lemon", "Lemon Soda", "甘さ控えめの生レモンソーダ。", "240.00", "drink", "cold", "light"),
            menuItem("drink-latte", "Iced Latte", "ミルクフォーム入りアイスカフェラテ。", "320.00", "drink", "cold", "coffee"),
            menuItem("drink-matcha-latte", "Hot Matcha Latte", "濃茶をオーツミルクで蒸し立てたホットマッチャラテ。", "350.00", "drink", "hot", "matcha", "japanese"),
            menuItem("wrap-garden", "Garden Wrap", "新鮮な野菜をヨーグルトソースで包んだガーデンラップ。", "760.00", "wrap", "vegetarian", "healthy", "light"),
            menuItem("bowl-salmon", "Salmon Rice Bowl", "焼き鮭の切り身を味付けごはんにのせ、ごまとネギを添えたサーモン丼。", "890.00", "bowl", "fish", "healthy", "japanese"),
            menuItem("bowl-veggie", "Veggie Grain Bowl", "旬の野菜をローストしてキヌアにのせ、タヒニドレッシングをかけたグレインボウル。", "750.00", "bowl", "vegetarian", "healthy", "light"),
            menuItem("dessert-choco", "Chocolate Fondant", "バニラアイスクリームを添えた温かいダークチョコレートフォンダン。", "380.00", "dessert", "sweet", "warm"),
            menuItem("dessert-matcha", "Matcha Soft Serve", "抹茶ソフトクリームに小倉あんをのせた一品。", "290.00", "dessert", "sweet", "matcha", "japanese"));

    private static final Map<String, MenuItem> ITEM_BY_ID = ITEMS.stream()
            .collect(LinkedHashMap::new, (map, item) -> map.put(item.id(), item), Map::putAll);

    List<MenuItem> findAll() {
        return List.copyOf(ITEMS);
    }

    Optional<MenuItem> findById(String itemId) {
        return Optional.ofNullable(ITEM_BY_ID.get(itemId));
    }

    List<MenuItem> search(String query) {
        String normalized = normalize(query);
        if (isFamilyQuery(normalized)) {
            return buildFamilySelection(query, normalized);
        }
        List<MenuItem> matches = ITEMS.stream()
                .filter(item -> matches(normalized, item))
                .toList();
        if (!matches.isEmpty()) {
            return tuneQuantities(matches, query);
        }
        return tuneQuantities(ITEMS.stream()
                .filter(item -> List.of("combo-crispy", "combo-smash", "side-fries", "drink-lemon").contains(item.id()))
                .toList(), query);
    }

    String headline(List<MenuItem> items) {
        return "menu-agent が " + items.size() + " 件のメニューオプションをマッチしました";
    }

    String describeSearch(String query) {
        List<MenuItem> matches = search(query);
        return matches.stream().map(MenuItem::name).limit(3).reduce((left, right) -> left + ", " + right).orElse("本日の人気コンボ");
    }

    BigDecimal calculateTotal(List<MenuItem> items) {
        return items.stream()
                .map(item -> item.price().multiply(BigDecimal.valueOf(item.suggestedQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    List<MenuItem> findSubstituteCandidates(String unavailableItemId) {
        MenuItem unavailableItem = ITEM_BY_ID.get(unavailableItemId);
        if (unavailableItem == null) {
            return ITEMS.stream().limit(3).toList();
        }

        List<MenuItem> sameCategory = ITEMS.stream()
                .filter(item -> !item.id().equals(unavailableItemId))
                .filter(item -> item.category().equals(unavailableItem.category()))
                .toList();
        List<MenuItem> baseCandidates = sameCategory.isEmpty()
                ? ITEMS.stream().filter(item -> !item.id().equals(unavailableItemId)).toList()
                : sameCategory;
        return baseCandidates.stream()
                .sorted(Comparator
                        .comparingInt((MenuItem item) -> tagOverlap(unavailableItem, item)).reversed()
                        .thenComparing(item -> unavailableItem.price().subtract(item.price()).abs())
                        .thenComparing(MenuItem::name))
                .limit(3)
                .toList();
    }

    List<MenuItem> findSubstitutes(String unavailableItemId, String customerMessage) {
        String normalized = normalize(customerMessage);
        List<MenuItem> rankedCandidates = findSubstituteCandidates(unavailableItemId).stream()
                .sorted(Comparator.comparingInt((MenuItem item) -> intentScore(normalized, item)).reversed())
                .toList();
        return tuneQuantities(rankedCandidates, customerMessage);
    }

    String substitutionHeadline(List<MenuItem> items) {
        return "menu-agent が " + items.size() + " 件の代替候補を準備しました";
    }

    String describeSubstitutes(String unavailableItemId, String customerMessage) {
        return findSubstitutes(unavailableItemId, customerMessage).stream()
                .map(MenuItem::name)
                .reduce((left, right) -> left + ", " + right)
                .orElse("最も近い利用可能なコンボ");
    }

    private List<MenuItem> buildFamilySelection(String query, String normalized) {
        int partySize = Math.max(1, detectPartySize(query));
        int childCount = Math.min(partySize, detectChildCount(normalized));
        int adultCount = Math.max(0, partySize - childCount);
        BigDecimal budget = detectBudget(normalized).orElse(new BigDecimal("999999"));

        MenuItem kidsCombo = ITEM_BY_ID.get("combo-kids");
        MenuItem adultCombo = ITEM_BY_ID.get("combo-teriyaki");
        MenuItem familyDrink = ITEM_BY_ID.get("drink-lemon");

        int adultComboQuantity = affordableAdultComboQuantity(budget, adultCount, childCount, partySize, adultCombo, kidsCombo,
                familyDrink);
        LinkedHashMap<String, MenuItem> selection = new LinkedHashMap<>();
        if (childCount > 0) {
            selection.put(kidsCombo.id(), withQuantity(kidsCombo, childCount));
        }
        if (adultComboQuantity > 0) {
            selection.put(adultCombo.id(), withQuantity(adultCombo, adultComboQuantity));
        }
        selection.put(familyDrink.id(), withQuantity(familyDrink, partySize));
        return List.copyOf(selection.values());
    }

    private List<MenuItem> tuneQuantities(List<MenuItem> items, String query) {
        int partySize = detectPartySize(query);
        if (partySize <= 1) {
            return items;
        }
        return items.stream()
                .map(item -> new MenuItem(
                        item.id(),
                        item.name(),
                        item.description(),
                        item.price(),
                        Math.max(1, Math.min(4, partySize / 2 + item.suggestedQuantity())),
                        item.category(),
                        item.tags()))
                .toList();
    }

    private MenuItem withQuantity(MenuItem item, int quantity) {
        return new MenuItem(
                item.id(),
                item.name(),
                item.description(),
                item.price(),
                quantity,
                item.category(),
                item.tags());
    }

    private boolean isFamilyQuery(String normalized) {
        return normalized.contains("家族") || normalized.contains("子ども") || normalized.contains("子供")
                || normalized.contains("kids") || normalized.contains("2人") || normalized.contains("3人")
                || normalized.contains("4人");
    }

    private int affordableAdultComboQuantity(
            BigDecimal budget,
            int adultCount,
            int childCount,
            int partySize,
            MenuItem adultCombo,
            MenuItem kidsCombo,
            MenuItem familyDrink) {
        if (adultCount == 0) {
            return 0;
        }
        BigDecimal baseCost = familyDrink.price().multiply(BigDecimal.valueOf(partySize))
                .add(kidsCombo.price().multiply(BigDecimal.valueOf(childCount)));
        BigDecimal remaining = budget.subtract(baseCost);
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            return 1;
        }
        int affordable = remaining.divideToIntegralValue(adultCombo.price()).intValue();
        return Math.max(1, Math.min(adultCount, affordable));
    }

    private boolean matches(String normalized, MenuItem item) {
        if (normalized.isBlank()) {
            return false;
        }
        return containsAny(normalized, item.name().toLowerCase(Locale.ROOT), item.description().toLowerCase(Locale.ROOT))
                || item.tags().stream().anyMatch(normalized::contains)
                || normalized.contains(item.category())
                || (normalized.contains("チキン") && item.name().contains("Chicken"))
                || (normalized.contains("バーガー") && item.name().contains("Burger"))
                || (normalized.contains("子ども") && item.name().contains("Kids"))
                || (normalized.contains("飲み物") && "drink".equals(item.category()))
                || (normalized.contains("ポテト") && item.name().contains("Fries"));
    }

    private boolean containsAny(String normalized, String... fields) {
        for (String field : fields) {
            for (String token : normalized.split("\\s+")) {
                if (!token.isBlank() && field.contains(token)) {
                    return true;
                }
            }
        }
        return false;
    }

    private int intentScore(String normalized, MenuItem item) {
        if (normalized.isBlank()) {
            return 0;
        }
        int score = 0;
        if ((normalized.contains("子ども") || normalized.contains("kids")) && item.tags().contains("kids")) {
            score += 3;
        }
        if ((normalized.contains("チキン") || normalized.contains("chicken")) && item.tags().contains("chicken")) {
            score += 3;
        }
        if ((normalized.contains("辛さ控えめ") || normalized.contains("mild"))
                && (item.tags().contains("mild") || item.tags().contains("light"))) {
            score += 2;
        }
        if ((normalized.contains("飲み物") || normalized.contains("drink")) && "drink".equals(item.category())) {
            score += 2;
        }
        if ((normalized.contains("ヘルシー") || normalized.contains("healthy")) && item.tags().contains("healthy")) {
            score += 2;
        }
        if ((normalized.contains("和風") || normalized.contains("和食") || normalized.contains("japanese"))
                && item.tags().contains("japanese")) {
            score += 1;
        }
        return score;
    }

    private int tagOverlap(MenuItem left, MenuItem right) {
        Set<String> leftTags = Set.copyOf(left.tags());
        return (int) right.tags().stream().filter(leftTags::contains).count();
    }

    private int detectPartySize(String query) {
        if (query.contains("3人") || query.contains("三人")) {
            return 3;
        }
        if (query.contains("4人") || query.contains("四人")) {
            return 4;
        }
        if (query.contains("2人") || query.contains("二人") || query.contains("ふたり")) {
            return 2;
        }
        return 1;
    }

    private int detectChildCount(String normalized) {
        java.util.regex.Matcher matcher = CHILD_COUNT_PATTERN.matcher(normalized);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        if (normalized.contains("子ども") || normalized.contains("子供") || normalized.contains("kids")) {
            return 1;
        }
        return 0;
    }

    private Optional<BigDecimal> detectBudget(String normalized) {
        java.util.regex.Matcher matcher = YEN_BUDGET_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.of(new BigDecimal(matcher.group(1)));
    }

    private String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }

    private static MenuItem menuItem(
            String id,
            String name,
            String description,
            String price,
            String category,
            String... tags) {
        return new MenuItem(id, name, description, new BigDecimal(price), 1, category, List.of(tags));
    }
}