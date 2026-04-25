package com.mahitotsu.arachne.samples.delivery.menuservice;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    private final Tool menuLookupTool;
    private final Tool menuSubstitutionLookupTool;

    MenuApplicationService(
            AgentFactory agentFactory,
            MenuRepository repository,
            @Qualifier("menuLookupTool") Tool menuLookupTool,
            @Qualifier("menuSubstitutionLookupTool") Tool menuSubstitutionLookupTool) {
        this.agentFactory = agentFactory;
        this.repository = repository;
        this.menuLookupTool = menuLookupTool;
        this.menuSubstitutionLookupTool = menuSubstitutionLookupTool;
    }

    MenuSuggestionResponse suggest(MenuSuggestionRequest request) {
        List<MenuItem> items = repository.search(request.message());
        String agentSummary = agentFactory.builder()
                .systemPrompt("""
                あなたは単一ブランドのクラウドキッチンアプリの menu-agent です。

                このビジネスは1つのキッチンのみです。現在のメニューからのみアイテムを推奨してください。
                別の支店、別のキッチン、またはテイクアウトの計画は言及しないでください。

                        お客様の状況に合わせて返答をカスタマイズするために利用可能なスキルを活用してください:
                        - お客様が複数人、お子様、またはファミリーに言及したときは family-order-guide を有効化する。
                        - お客様が特定のアイテムを念頭に置かずにブラウジングしているときは proactive-recommendation を有効化する。

                        関連するスキルを有効化した後、menu_catalog_lookup を呼び出してマッチするアイテムを検索し、\
                        そのスキルの指示に従って最適なマッチを説明してください。""")
                .tools(menuLookupTool)
                .build()
                .run("query=" + request.message())
                .text();
        String summary = renderSuggestionSummary(items, agentSummary);
        return new MenuSuggestionResponse("menu-service", "menu-agent", repository.headline(items), summary, items);
    }

    private String renderSuggestionSummary(List<MenuItem> items, String agentSummary) {
        String skillPrefix = extractSkillPrefix(agentSummary);
        String itemSummary = items.stream()
                .map(item -> item.name() + " x" + item.suggestedQuantity())
                .reduce((left, right) -> left + ", " + right)
                .orElse("本日の人気コンボ");
        String prefix = skillPrefix.isBlank() ? "" : skillPrefix + " ";
        return prefix + "menu-agent が " + itemSummary + " をおすすめします。現在のメニューに沿った内容です。";
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
}

@Component
class MenuRepository {

    private static final List<MenuItem> ITEMS = List.of(
            new MenuItem("combo-crispy", "Crispy Chicken Box", "クリスピーチキン、フライドポテト、レモンソーダのセット。", new BigDecimal("980.00"), 1),
            new MenuItem("combo-smash", "Smash Burger Combo", "ダブルスマッシュバーガーにフライドポテトとコーラが付いたコンボ。", new BigDecimal("1050.00"), 1),
            new MenuItem("combo-kids", "Kids Cheeseburger Set", "ミニチーズバーガー、コーンカップ、アップルジュースのキッズセット。", new BigDecimal("720.00"), 1),
            new MenuItem("combo-teriyaki", "Teriyaki Chicken Box", "照り焼きチキン、ゴハン、味噌汁のセット。", new BigDecimal("920.00"), 1),
            new MenuItem("combo-spicy-tuna", "Spicy Tuna Rice Box", "旬のスパイシーツナをすし飯にのせ、紅ショウガを添えたライスボックス。", new BigDecimal("1080.00"), 1),
            new MenuItem("side-fries", "Curly Fries", "スパイスの効いたカーリーフライ。", new BigDecimal("330.00"), 1),
            new MenuItem("side-nuggets", "Nugget Share Box", "ソース付き10個入りナゲットボックス。", new BigDecimal("640.00"), 1),
            new MenuItem("side-onion-rings", "Crispy Onion Rings", "ビール衣のオニオンリング、チポトレディップ添え。", new BigDecimal("380.00"), 1),
            new MenuItem("drink-lemon", "Lemon Soda", "甘さ控えめの生レモンソーダ。", new BigDecimal("240.00"), 1),
            new MenuItem("drink-latte", "Iced Latte", "ミルクフォーム入りアイスカフェラテ。", new BigDecimal("320.00"), 1),
            new MenuItem("drink-matcha-latte", "Hot Matcha Latte", "濃茶をオーツミルクで蒸し立てたホットマッチャラテ。", new BigDecimal("350.00"), 1),
            new MenuItem("wrap-garden", "Garden Wrap", "新鮮な野菜をヨーグルトソースで包んだガーデンラップ。", new BigDecimal("760.00"), 1),
            new MenuItem("bowl-salmon", "Salmon Rice Bowl", "焼き髦の切り身を味付けごはんにのせ、ごまとネギを添えたサーモン丼。", new BigDecimal("890.00"), 1),
            new MenuItem("bowl-veggie", "Veggie Grain Bowl", "旬の野菜をローストしてキヌアにのせ、タヒニドレッシングをかけたグレインボウル。", new BigDecimal("750.00"), 1),
            new MenuItem("dessert-choco", "Chocolate Fondant", "バニラアイスクリームを添えた温かいダークチョコレートフォンダン。", new BigDecimal("380.00"), 1),
            new MenuItem("dessert-matcha", "Matcha Soft Serve", "抹茶ソフトクリームに小倉あんをのせた一品。", new BigDecimal("290.00"), 1));

    List<MenuItem> findAll() {
        return List.copyOf(ITEMS);
    }

    List<MenuItem> search(String query) {
        String normalized = normalize(query);
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

    List<MenuItem> findSubstitutes(String unavailableItemId, String customerMessage) {
        String normalized = normalize(customerMessage);
        List<MenuItem> sameCategory = ITEMS.stream()
                .filter(item -> !item.id().equals(unavailableItemId))
                .filter(item -> sameCategory(unavailableItemId, item.id()))
                .toList();
        List<MenuItem> intentAligned = sameCategory.stream()
                .filter(item -> matchesIntent(normalized, item))
                .toList();
        List<MenuItem> matches = intentAligned.isEmpty() ? sameCategory : intentAligned;
        if (matches.isEmpty()) {
            matches = ITEMS.stream()
                    .filter(item -> !item.id().equals(unavailableItemId))
                    .filter(item -> matchesIntent(normalized, item))
                    .toList();
        }
        if (matches.isEmpty()) {
            matches = ITEMS.stream()
                    .filter(item -> !item.id().equals(unavailableItemId))
                    .toList();
        }
        return tuneQuantities(matches.stream().limit(3).toList(), customerMessage);
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
                .map(item -> new MenuItem(item.id(), item.name(), item.description(), item.price(), Math.max(1, Math.min(4, partySize / 2 + item.suggestedQuantity()))))
                .toList();
    }

    private boolean matches(String normalized, MenuItem item) {
        if (normalized.isBlank()) {
            return false;
        }
        return containsAny(normalized, item.name().toLowerCase(Locale.ROOT), item.description().toLowerCase(Locale.ROOT))
                || (normalized.contains("チキン") && item.name().contains("Chicken"))
                || (normalized.contains("バーガー") && item.name().contains("Burger"))
                || (normalized.contains("子ども") && item.name().contains("Kids"))
            || (normalized.contains("飲み物") && (item.name().contains("Lemon") || item.name().contains("Latte")))
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

    private boolean sameCategory(String leftId, String rightId) {
        return categoryOf(leftId).equals(categoryOf(rightId));
    }

    private String categoryOf(String itemId) {
        int separator = itemId.indexOf('-');
        return separator >= 0 ? itemId.substring(0, separator) : itemId;
    }

    private boolean matchesIntent(String normalized, MenuItem item) {
        if (normalized.isBlank()) {
            return true;
        }
        if (normalized.contains("子ども") || normalized.contains("kids")) {
            return item.name().contains("Kids") || item.description().contains("apple") || item.description().contains("corn");
        }
        if (normalized.contains("辛さ控えめ") || normalized.contains("mild")) {
            return item.name().contains("Garden") || item.name().contains("Lemon") || item.name().contains("Kids");
        }
        if (normalized.contains("飲み物") || normalized.contains("drink")) {
            return item.id().startsWith("drink-");
        }
        return true;
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

    private String normalize(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT);
    }
}

@Configuration
class MenuArachneConfiguration {

    @Bean
    Tool menuLookupTool(MenuRepository repository) {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec("menu_catalog_lookup", "ローカルメニューカタログを参照し、リクエストに最も適したマッチをまとめる。", schema());
            }

            @Override
            public ToolResult invoke(Object input) {
                return invoke(input, new ToolInvocationContext("menu_catalog_lookup", null, input, null));
            }

            @Override
            public ToolResult invoke(Object input, ToolInvocationContext context) {
                String query = String.valueOf(values(input).getOrDefault("query", ""));
                return ToolResult.success(context.toolUseId(), Map.of("matchSummary", repository.describeSearch(query)));
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

    @SuppressWarnings("unchecked")
    private static Map<String, Object> values(Object input) {
        if (input instanceof Map<?, ?> rawValues) {
            LinkedHashMap<String, Object> values = new LinkedHashMap<>();
            rawValues.forEach((key, value) -> values.put(String.valueOf(key), value));
            return values;
        }
        return Map.of();
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
                        new ModelEvent.ToolUse("menu-lookup", "menu_catalog_lookup", Map.of("query", userText)),
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

record MenuSuggestionRequest(String sessionId, String message) {}

record MenuSubstitutionRequest(String sessionId, String message, String unavailableItemId) {}

record MenuSuggestionResponse(String service, String agent, String headline, String summary, List<MenuItem> items) {}

record MenuSubstitutionResponse(String service, String agent, String headline, String summary, List<MenuItem> items) {}

record MenuItem(String id, String name, String description, BigDecimal price, int suggestedQuantity) {}