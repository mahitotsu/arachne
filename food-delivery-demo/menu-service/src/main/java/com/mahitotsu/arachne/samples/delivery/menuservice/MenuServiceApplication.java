package com.mahitotsu.arachne.samples.delivery.menuservice;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mahitotsu.arachne.strands.model.Model;
import com.mahitotsu.arachne.strands.model.ModelEvent;
import com.mahitotsu.arachne.strands.model.ToolSelection;
import com.mahitotsu.arachne.strands.model.ToolSpec;
import com.mahitotsu.arachne.strands.spring.AgentFactory;
import com.mahitotsu.arachne.strands.tool.Tool;
import com.mahitotsu.arachne.strands.tool.ToolInvocationContext;
import com.mahitotsu.arachne.strands.tool.ToolResult;
import com.mahitotsu.arachne.strands.types.ContentBlock;
import com.mahitotsu.arachne.strands.types.Message;

@SpringBootApplication
public class MenuServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MenuServiceApplication.class, args);
    }

    @Bean
    SecurityFilterChain menuSecurity(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
                .build();
    }

    @Bean("kitchenRestClient")
    RestClient kitchenRestClient(@Value("${KITCHEN_SERVICE_BASE_URL:http://localhost:8082}") String baseUrl) {
        return RestClient.builder().baseUrl(baseUrl).build();
    }
}

@RestController
@RequestMapping(path = "/internal/menu", produces = MediaType.APPLICATION_JSON_VALUE)
class MenuController {

    private final MenuApplicationService applicationService;

    MenuController(MenuApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/suggest")
    MenuSuggestionResponse suggest(@RequestBody MenuSuggestionRequest request) {
        return applicationService.suggest(request);
    }

    @PostMapping("/substitutes")
    MenuSubstitutionResponse substitutes(@RequestBody MenuSubstitutionRequest request) {
        return applicationService.suggestSubstitutes(request);
    }
}

@RestController
@RequestMapping(path = "/api/menu", produces = MediaType.APPLICATION_JSON_VALUE)
class MenuCatalogController {

    private final MenuRepository repository;

    MenuCatalogController(MenuRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/catalog")
    List<MenuItem> catalog() {
        return repository.findAll();
    }
}

@Service
class MenuApplicationService {

    private final AgentFactory agentFactory;
    private final MenuRepository repository;
        private final Tool catalogLookupTool;
        private final Tool calculateTotalTool;
    private final Tool menuSubstitutionLookupTool;
        private final KitchenCheckGateway kitchenCheckGateway;

    MenuApplicationService(
            AgentFactory agentFactory,
            MenuRepository repository,
            @Qualifier("catalogLookupTool") Tool catalogLookupTool,
            @Qualifier("calculateTotalTool") Tool calculateTotalTool,
            @Qualifier("menuSubstitutionLookupTool") Tool menuSubstitutionLookupTool,
            KitchenCheckGateway kitchenCheckGateway) {
        this.agentFactory = agentFactory;
        this.repository = repository;
        this.catalogLookupTool = catalogLookupTool;
        this.calculateTotalTool = calculateTotalTool;
        this.menuSubstitutionLookupTool = menuSubstitutionLookupTool;
        this.kitchenCheckGateway = kitchenCheckGateway;
    }

    MenuSuggestionResponse suggest(MenuSuggestionRequest request) {
        String accessToken = SecurityAccessors.requiredAccessToken();
        List<MenuItem> items = repository.search(request.message());
        String agentSummary = agentFactory.builder()
                .systemPrompt("""
                あなたは単一ブランドのクラウドキッチンアプリの menu-agent です。

                このビジネスは1つのキッチンのみです。現在のメニューからのみアイテムを推奨してください。
                別の支店、別のキッチン、またはテイクアウトの計画は言及しないでください。

            利用可能なスキルを必要に応じて有効化してください。
            その後は必ず catalog_lookup_tool を使って候補を確認し、人数・予算・好み・履歴文脈に合う提案セットを選んでください。
            提案後は calculate_total_tool を使って合計を検算し、短い理由文を添えてください。
            欠品や混雑の最終判断は kitchen-service 側で行われるため、推薦理由はメニュー意図に集中してください。""")
            .tools(catalogLookupTool, calculateTotalTool)
                .build()
                .run("query=" + request.message())
                .text();
        KitchenCheckResponse kitchenResponse = kitchenCheckGateway.check(
            new KitchenCheckRequest(request.sessionId(), request.message(), items.stream().map(MenuItem::id).toList()),
            accessToken);
        List<MenuItem> resolvedItems = resolveItems(items, kitchenResponse.items());
        BigDecimal total = repository.calculateTotal(resolvedItems);
        String summary = renderSuggestionSummary(resolvedItems, agentSummary, kitchenResponse, total);
        return new MenuSuggestionResponse(
            "menu-service",
            "menu-agent",
            repository.headline(resolvedItems),
            summary,
            resolvedItems,
            kitchenResponse.readyInMinutes(),
            new KitchenTrace(kitchenResponse.summary(), kitchenTraceNotes(kitchenResponse)));
    }

        private String renderSuggestionSummary(
            List<MenuItem> items,
            String agentSummary,
            KitchenCheckResponse kitchenResponse,
            BigDecimal total) {
        String skillPrefix = extractSkillPrefix(agentSummary);
        String itemSummary = items.stream()
                .map(item -> item.name() + " x" + item.suggestedQuantity())
                .reduce((left, right) -> left + ", " + right)
                .orElse("本日の人気コンボ");
        String prefix = skillPrefix.isBlank() ? "" : skillPrefix + " ";
        return prefix + "menu-agent が " + itemSummary + " をおすすめします。"
            + " キッチン確認後の提供目安は約" + kitchenResponse.readyInMinutes() + "分、合計は"
            + formatYen(total) + "です。";
    }

    private String extractSkillPrefix(String agentSummary) {
        if (agentSummary == null) {
            return "";
        }
        int closingBracket = agentSummary.indexOf(']');
        if (agentSummary.startsWith("[") && closingBracket > 0) {
            return agentSummary.substring(0, closingBracket + 1);
        }
        return "";
    }

    MenuSubstitutionResponse suggestSubstitutes(MenuSubstitutionRequest request) {
        List<MenuItem> items = repository.findSubstitutes(request.unavailableItemId(), request.message());
        String summary = agentFactory.builder()
                .systemPrompt("""
                あなたは唯一のクラウドキッチンでアイテムが在庫切れのときに kitchen-agent をサポートする menu-agent です。

                        menu_substitution_lookup を呼び出して、お客様の意図に近い代替品の候補を準備してください。
                同ブランドのメニュー内に留め、別のキッチンは言及しないでください。
                答えは簡潔にし、kitchen-agent が検証する代替提案であることを言及してください。
                        """)
                .tools(menuSubstitutionLookupTool)
                .build()
                .run("unavailableItemId=" + request.unavailableItemId() + "\ncustomerMessage=" + request.message())
                .text();
        return new MenuSubstitutionResponse(
                "menu-service",
                "menu-agent",
                repository.substitutionHeadline(items),
                summary,
                items);
    }

    private List<MenuItem> resolveItems(List<MenuItem> originalItems, List<KitchenItemStatus> statuses) {
        Map<String, KitchenItemStatus> statusByItemId = statuses.stream()
                .collect(LinkedHashMap::new, (map, status) -> map.put(status.itemId(), status), Map::putAll);
        return originalItems.stream()
                .map(item -> resolveItem(item, statusByItemId.get(item.id())))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private MenuItem resolveItem(MenuItem item, KitchenItemStatus status) {
        if (status == null || status.available()) {
            return item;
        }
        if (status.substituteItemId() != null) {
            MenuItem substitute = repository.findById(status.substituteItemId()).orElse(item);
            return new MenuItem(
                    substitute.id(),
                    status.substituteName() == null ? substitute.name() : status.substituteName(),
                    substitute.description(),
                    status.substitutePrice() == null ? substitute.price() : status.substitutePrice(),
                    item.suggestedQuantity(),
                    substitute.category(),
                    substitute.tags());
        }
        return null;
    }

    private List<String> kitchenTraceNotes(KitchenCheckResponse kitchenResponse) {
        if (kitchenResponse.collaborations() == null || kitchenResponse.collaborations().isEmpty()) {
            return List.of();
        }
        return kitchenResponse.collaborations().stream()
                .map(collaboration -> collaboration.service() + ": " + collaboration.summary())
                .toList();
    }

    private String formatYen(BigDecimal total) {
        return "¥" + total.stripTrailingZeros().toPlainString();
    }
}

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

    java.util.Optional<MenuItem> findById(String itemId) {
        return java.util.Optional.ofNullable(ITEM_BY_ID.get(itemId));
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

    private boolean matchesIntent(String normalized, MenuItem item) {
        return intentScore(normalized, item) > 0 || normalized.isBlank();
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

    private java.util.Optional<BigDecimal> detectBudget(String normalized) {
        java.util.regex.Matcher matcher = YEN_BUDGET_PATTERN.matcher(normalized);
        if (!matcher.find()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(new BigDecimal(matcher.group(1)));
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

@Configuration
class MenuArachneConfiguration {

    @Bean
    Tool catalogLookupTool(MenuRepository repository) {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec("catalog_lookup_tool", "ローカルメニューカタログを参照し、候補一覧を返す。", schema());
            }

            @Override
            public ToolResult invoke(Object input) {
                return invoke(input, new ToolInvocationContext("catalog_lookup_tool", null, input, null));
            }

            @Override
            public ToolResult invoke(Object input, ToolInvocationContext context) {
                String query = String.valueOf(values(input).getOrDefault("query", ""));
                List<MenuItem> matches = repository.search(query);
                return ToolResult.success(context.toolUseId(), Map.of(
                        "matchSummary", repository.describeSearch(query),
                        "itemIds", matches.stream().map(MenuItem::id).toList()));
            }
        };
    }

    @Bean
    Tool calculateTotalTool(MenuRepository repository) {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec("calculate_total_tool", "候補セットの合計金額を計算する。", totalSchema());
            }

            @Override
            public ToolResult invoke(Object input) {
                return invoke(input, new ToolInvocationContext("calculate_total_tool", null, input, null));
            }

            @Override
            public ToolResult invoke(Object input, ToolInvocationContext context) {
                List<String> itemIds = stringList(values(input).get("itemIds"));
                BigDecimal total = repository.calculateTotal(itemIds.stream()
                        .map(id -> repository.findById(id).orElse(null))
                        .filter(java.util.Objects::nonNull)
                        .toList());
                return ToolResult.success(context.toolUseId(), Map.of("total", total));
            }
        };
    }

    @Bean
    Tool menuSubstitutionLookupTool(MenuRepository repository) {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec(
                        "menu_substitution_lookup",
                        "リクエストされたアイテムが在庫切れのときに kitchen-agent 向けのメニュー代替品を準備する。",
                        substitutionSchema());
            }

            @Override
            public ToolResult invoke(Object input) {
                return invoke(input, new ToolInvocationContext("menu_substitution_lookup", null, input, null));
            }

            @Override
            public ToolResult invoke(Object input, ToolInvocationContext context) {
                Map<String, Object> values = values(input);
                String unavailableItemId = String.valueOf(values.getOrDefault("unavailableItemId", ""));
                String customerMessage = String.valueOf(values.getOrDefault("customerMessage", ""));
                return ToolResult.success(context.toolUseId(), Map.of(
                        "substitutionSummary", repository.describeSubstitutes(unavailableItemId, customerMessage)));
            }
        };
    }

    @Bean
    @ConditionalOnProperty(name = "delivery.model.mode", havingValue = "deterministic", matchIfMissing = false)
    Model menuDeterministicModel() {
        return new MenuDeterministicModel();
    }

    private static ObjectNode schema() {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("type", "object");
        ObjectNode properties = root.putObject("properties");
        properties.putObject("query").put("type", "string");
        root.putArray("required").add("query");
        root.put("additionalProperties", false);
        return root;
    }

    private static ObjectNode substitutionSchema() {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("type", "object");
        ObjectNode properties = root.putObject("properties");
        properties.putObject("unavailableItemId").put("type", "string");
        properties.putObject("customerMessage").put("type", "string");
        root.putArray("required").add("unavailableItemId").add("customerMessage");
        root.put("additionalProperties", false);
        return root;
    }

    private static ObjectNode totalSchema() {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("type", "object");
        ObjectNode properties = root.putObject("properties");
        ObjectNode itemIds = properties.putObject("itemIds");
        itemIds.put("type", "array");
        itemIds.putObject("items").put("type", "string");
        root.putArray("required").add("itemIds");
        root.put("additionalProperties", false);
        return root;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> values(Object input) {
        if (input instanceof Map<?, ?> rawValues) {
            LinkedHashMap<String, Object> values = new LinkedHashMap<>();
            rawValues.forEach((key, value) -> values.put(String.valueOf(key), value));
            return values;
        }
        return Map.of();
    }

    private static List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private static final class MenuDeterministicModel implements Model {

        @Override
        public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools) {
            return converse(messages, tools, null, null);
        }

        @Override
        public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools, String systemPrompt) {
            return converse(messages, tools, systemPrompt, null);
        }

        @Override
        public Iterable<ModelEvent> converse(
                List<Message> messages,
                List<ToolSpec> tools,
                String systemPrompt,
                ToolSelection toolSelection) {
            Map<String, String> requestArgs = latestRequestArgs(messages);
            String userText = requestArgs.getOrDefault("query", latestUserText(messages));
            boolean substitutionQuery = requestArgs.containsKey("unavailableItemId");
            boolean isFamilyQuery = isFamilyQuery(userText);

            if (!substitutionQuery && isFamilyQuery && !hasSkillActivation(messages)) {
                return List.of(
                        new ModelEvent.ToolUse("skill-family", "activate_skill", Map.of("name", "family-order-guide")),
                        new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
            }

            String toolUseId = substitutionQuery ? "menu-substitution-lookup" : "menu-lookup";
            Map<String, Object> toolContent = latestToolContent(messages, toolUseId);
            if (toolContent == null) {
            if (substitutionQuery) {
                return List.of(
                    new ModelEvent.ToolUse(
                        "menu-substitution-lookup",
                        "menu_substitution_lookup",
                        Map.of(
                            "unavailableItemId", requestArgs.getOrDefault("unavailableItemId", ""),
                            "customerMessage", requestArgs.getOrDefault("customerMessage", ""))),
                    new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
            }
                return List.of(
                        new ModelEvent.ToolUse("menu-lookup", "catalog_lookup_tool", Map.of("query", userText)),
                        new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
            }

            if (!substitutionQuery && latestToolContent(messages, "menu-total") == null) {
                Object itemIds = toolContent.getOrDefault("itemIds", List.of());
                return List.of(
                        new ModelEvent.ToolUse("menu-total", "calculate_total_tool", Map.of("itemIds", itemIds)),
                        new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
            }

            if (substitutionQuery) {
            return List.of(
                new ModelEvent.TextDelta("menu-agent が kitchen-agent に検証させる代替品として "
                    + toolContent.getOrDefault("substitutionSummary", "最も近い代替品")
                    + " を提案しました。"),
                new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
            }

            String prefix = isFamilyQuery ? "[family-order-guide] " : "";
            return List.of(
                    new ModelEvent.TextDelta(prefix + "menu-agent が " + toolContent.getOrDefault("matchSummary", "本日の人気コンボ")
                            + " をおすすめします。現在のメニューに沿った内容でチャットで簡単に確認できます。"),
                    new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
        }

        private boolean isFamilyQuery(String text) {
            if (text == null) {
                return false;
            }
            return text.contains("子ども") || text.contains("家族") || text.contains("kids")
                    || text.contains("2人") || text.contains("3人") || text.contains("4人");
        }

        private boolean hasSkillActivation(List<Message> messages) {
            for (Message message : messages) {
                for (ContentBlock block : message.content()) {
                    if (block instanceof ContentBlock.ToolResult toolResult
                            && toolResult.content() instanceof Map<?, ?> content
                            && "skill_activation".equals(content.get("type"))) {
                        return true;
                    }
                }
            }
            return false;
        }

        private Map<String, Object> latestToolContent(List<Message> messages, String toolUseId) {
            for (int index = messages.size() - 1; index >= 0; index--) {
                Message message = messages.get(index);
                for (ContentBlock block : message.content()) {
                    if (block instanceof ContentBlock.ToolResult result
                            && toolUseId.equals(result.toolUseId())
                            && result.content() instanceof Map<?, ?> content) {
                        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
                        content.forEach((key, value) -> values.put(String.valueOf(key), value));
                        return values;
                    }
                }
            }
            return null;
        }

        private String latestUserText(List<Message> messages) {
            for (int index = messages.size() - 1; index >= 0; index--) {
                Message message = messages.get(index);
                if (message.role() != Message.Role.USER) {
                    continue;
                }
                for (ContentBlock block : message.content()) {
                    if (block instanceof ContentBlock.Text text) {
                        return text.text().replace("query=", "");
                    }
                }
            }
            return "";
        }

        private Map<String, String> latestRequestArgs(List<Message> messages) {
            String raw = latestUserText(messages);
            LinkedHashMap<String, String> values = new LinkedHashMap<>();
            for (String line : raw.split("\\R")) {
                int separator = line.indexOf('=');
                if (separator <= 0) {
                    continue;
                }
                values.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
            }
            return values;
        }
    }
}

@Component
class KitchenCheckGateway {

    private final RestClient restClient;

    KitchenCheckGateway(@Qualifier("kitchenRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    KitchenCheckResponse check(KitchenCheckRequest request, String accessToken) {
        return java.util.Objects.requireNonNull(restClient.post()
                .uri("/internal/kitchen/check")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(KitchenCheckResponse.class));
    }
}

final class SecurityAccessors {

    private SecurityAccessors() {
    }

    static String requiredAccessToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            return jwtAuthenticationToken.getToken().getTokenValue();
        }
        throw new IllegalStateException("No access token is available in the security context");
    }
}

record MenuSuggestionRequest(String sessionId, String message) {}

record MenuSubstitutionRequest(String sessionId, String message, String unavailableItemId) {}

record MenuSuggestionResponse(String service, String agent, String headline, String summary, List<MenuItem> items,
        int etaMinutes, KitchenTrace kitchenTrace) {}

record MenuSubstitutionResponse(String service, String agent, String headline, String summary, List<MenuItem> items) {}

record MenuItem(String id, String name, String description, BigDecimal price, int suggestedQuantity, String category,
    List<String> tags) {}

record KitchenCheckRequest(String sessionId, String message, List<String> itemIds) {}

record KitchenCheckResponse(String service, String agent, String headline, String summary, int readyInMinutes,
        List<KitchenItemStatus> items, List<AgentCollaboration> collaborations) {}

record KitchenItemStatus(String itemId, boolean available, int prepMinutes, String substituteItemId, String substituteName,
        BigDecimal substitutePrice) {}

record AgentCollaboration(String service, String agent, String headline, String summary) {}

record KitchenTrace(String summary, List<String> notes) {}