package com.mahitotsu.arachne.samples.delivery.menuservice.application;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.mahitotsu.arachne.samples.delivery.menuservice.config.SecurityAccessors;
import com.mahitotsu.arachne.samples.delivery.menuservice.domain.MenuTypes.ContextualAdditionsDecision;
import com.mahitotsu.arachne.samples.delivery.menuservice.domain.MenuTypes.ExplicitItemsDecision;
import com.mahitotsu.arachne.samples.delivery.menuservice.domain.MenuTypes.KitchenCheckRequest;
import com.mahitotsu.arachne.samples.delivery.menuservice.domain.MenuTypes.KitchenCheckResponse;
import com.mahitotsu.arachne.samples.delivery.menuservice.domain.MenuTypes.KitchenItemStatus;
import com.mahitotsu.arachne.samples.delivery.menuservice.domain.MenuTypes.KitchenTrace;
import com.mahitotsu.arachne.samples.delivery.menuservice.domain.MenuTypes.MenuItem;
import com.mahitotsu.arachne.samples.delivery.menuservice.domain.MenuTypes.MenuSubstitutionDecision;
import com.mahitotsu.arachne.samples.delivery.menuservice.domain.MenuTypes.MenuSubstitutionRequest;
import com.mahitotsu.arachne.samples.delivery.menuservice.domain.MenuTypes.MenuSubstitutionResponse;
import com.mahitotsu.arachne.samples.delivery.menuservice.domain.MenuTypes.MenuSuggestionDecision;
import com.mahitotsu.arachne.samples.delivery.menuservice.domain.MenuTypes.MenuSuggestionRequest;
import com.mahitotsu.arachne.samples.delivery.menuservice.domain.MenuTypes.MenuSuggestionResponse;
import com.mahitotsu.arachne.samples.delivery.menuservice.infrastructure.KitchenCheckGateway;
import com.mahitotsu.arachne.samples.delivery.menuservice.infrastructure.MenuRepository;
import com.mahitotsu.arachne.samples.delivery.menuservice.observation.AgentObservationSupport;
import com.mahitotsu.arachne.samples.delivery.menuservice.observation.ArachneLifecycleHistoryListener;
import com.mahitotsu.arachne.strands.agent.AgentResult;
import com.mahitotsu.arachne.strands.agent.AgentState;
import com.mahitotsu.arachne.strands.spring.AgentFactory;
import com.mahitotsu.arachne.strands.tool.Tool;

@Service
public class MenuApplicationService {

    private final AgentFactory agentFactory;
    private final MenuRepository repository;
    private final Tool catalogLookupTool;
    private final Tool calculateTotalTool;
    private final Tool menuSubstitutionLookupTool;
    private final KitchenCheckGateway kitchenCheckGateway;
    private final AgentObservationSupport agentObservationSupport;

    MenuApplicationService(
            AgentFactory agentFactory,
            MenuRepository repository,
            @Qualifier("catalogLookupTool") Tool catalogLookupTool,
            @Qualifier("calculateTotalTool") Tool calculateTotalTool,
            @Qualifier("menuSubstitutionLookupTool") Tool menuSubstitutionLookupTool,
            KitchenCheckGateway kitchenCheckGateway,
            AgentObservationSupport agentObservationSupport) {
        this.agentFactory = agentFactory;
        this.repository = repository;
        this.catalogLookupTool = catalogLookupTool;
        this.calculateTotalTool = calculateTotalTool;
        this.menuSubstitutionLookupTool = menuSubstitutionLookupTool;
        this.kitchenCheckGateway = kitchenCheckGateway;
        this.agentObservationSupport = agentObservationSupport;
    }

    public MenuSuggestionResponse suggest(MenuSuggestionRequest request) {
        String accessToken = SecurityAccessors.requiredAccessToken();
        MenuAgentUserPrompt userPrompt = MenuAgentUserPrompt.from(request);
        AgentState agentState = agentState(request.sessionId());
        // --- Step 1: 明示指定アイテム検出 ---
        // ユーザーが商品名を具体的に指定した場合のみ selectedItemIds に含める。
        // 「チキン系」「おすすめ」など曖昧表現は空リストを返す。
        AgentResult explicitResult = agentObservationSupport.observe(
                "menu-service", "menu-agent-step1", request.sessionId(), userPrompt.render(), agentState,
                () -> agentFactory.builder()
                    .sessionId(request.sessionId())
                    .state(agentState)
                    .systemPrompt("""
                    あなたは単一ブランドのクラウドキッチンアプリの menu-agent です（Step 1: 明示指定検出）。

                    まず catalog_lookup_tool を呼んでカタログを確認してください。
                    ユーザーが商品名・通称を明示的に指定した場合、それぞれの名前に対応するカタログの itemId を selectedItemIds に含めてください。

                    【ルール】
                    - "〜と〜を1つずつ" のように複数の名前が列挙されたときは、それぞれを別アイテムとして扱い、全部を selectedItemIds に含めてください。
                    - 1つの名前に対して1つの itemId を対応させてください。1つのアイテムで複数名前をまとめて満たそうとしてはいけません。
                    - 「おすすめ」「何かいいもの」「チキン系」「家族向け」など曖昧な表現は明示指定ではありません。その場合は selectedItemIds を空リストで返してください。

                    【例】
                    - "テリヤキとクリスピーを1つずつ" → selectedItemIds: ["combo-teriyaki", "combo-crispy"]
                    - "スマッシュバーガーを2人分" → selectedItemIds: ["combo-smash"]
                    - "おすすめを見せて" → selectedItemIds: []

                    提案に使ってよい itemId は catalog_lookup_tool が返したものだけです。
                    最終回答は structured_output を使い、selectedItemIds だけを返してください。""")
                    .tools(catalogLookupTool)
                    .build()
                    .run(userPrompt.render(), ExplicitItemsDecision.class));
        ExplicitItemsDecision explicitDecision = explicitResult.structuredOutput(ExplicitItemsDecision.class);
        List<String> explicitIds = explicitDecision.selectedItemIds() != null
                ? explicitDecision.selectedItemIds() : List.of();

        // --- Step 2: 潜在的要望への追加提案 ---
        // 明示指定済みアイテムを伝えた上で、人数・好み・場面などの潜在ニーズに応える追加を提案する。
        String step2Prompt = explicitIds.isEmpty()
                ? userPrompt.render()
                : userPrompt.render() + "\nexplicit_selected=" + String.join(", ", explicitIds)
                    + "  ※これらはすでに確定済みです。additionalItemIds に重複して含めないでください。";
        AgentResult contextualResult = agentObservationSupport.observe(
                "menu-service", "menu-agent-step2", request.sessionId(), step2Prompt, agentState,
                () -> agentFactory.builder()
                    .sessionId(request.sessionId())
                    .state(agentState)
                    .systemPrompt("""
                    あなたは単一ブランドのクラウドキッチンアプリの menu-agent です（Step 2: 潜在要望への追加提案）。

                    このビジネスは1つのキッチンのみです。現在のメニューからのみアイテムを推奨してください。
                    「おすすめ」「何がいい？」のように広く相談されたときは proactive-recommendation を有効化してください。
                    家族・複数人・子ども向けの相談では family-order-guide を有効化してください。

                    ユーザープロンプトに explicit_selected が含まれる場合、それらはすでに Step 1 で確定済みです。
                    あなたのタスクは、人数・好み・場面・組み合わせなど明示指定以外の潜在的要望に応える追加アイテムを提案することです。
                    explicit_selected に含まれる itemId を additionalItemIds に重複して含めないでください。
                    追加が不要と判断した場合は additionalItemIds を空リストで返してください。

                    まず catalog_lookup_tool を呼んでカタログを確認してください。提案に使ってよい itemId は catalog_lookup_tool が返したものだけです。
                    最終回答は structured_output を使い、additionalItemIds, skillTag, recommendationReason を返してください。
                    recommendationReason には価格・合計金額を含めず、商品の特徴・人数・予算適合の根拠のみ記載してください。
                    欠品・提供可否・調理 ETA は kitchen-service 側で行われます。在庫や提供時間を約束しないでください。""")
                    .tools(catalogLookupTool, calculateTotalTool)
                    .build()
                    .run(step2Prompt, ContextualAdditionsDecision.class));
        ContextualAdditionsDecision contextualDecision = contextualResult.structuredOutput(ContextualAdditionsDecision.class);
        List<String> additionalIds = contextualDecision.additionalItemIds() != null
                ? contextualDecision.additionalItemIds() : List.of();

        // explicit を先頭に、contextual の追加分(重複除外)を後ろに結合
        List<String> mergedIds = Stream.concat(
                explicitIds.stream(),
                additionalIds.stream().filter(id -> !explicitIds.contains(id)))
                .toList();

        // Step 2 の skillTag・recommendationReason を採用し、downstream の型を統一する
        MenuSuggestionDecision decision = new MenuSuggestionDecision(
                mergedIds, contextualDecision.skillTag(), contextualDecision.recommendationReason());

        List<MenuItem> items = resolveSuggestedItems(request.query(), decision.selectedItemIds());
        KitchenCheckResponse kitchenResponse = kitchenCheckGateway.check(
                new KitchenCheckRequest(request.sessionId(), request.query(), items.stream().map(MenuItem::id).toList()),
                accessToken);
        List<MenuItem> resolvedItems = resolveItems(items, kitchenResponse.items());
        BigDecimal total = repository.calculateTotal(resolvedItems);
        String summary = renderSuggestionSummary(resolvedItems, decision, kitchenResponse, total);
        return new MenuSuggestionResponse(
                "menu-service",
                "menu-agent",
                repository.headline(resolvedItems),
                summary,
                resolvedItems,
                kitchenResponse.readyInMinutes(),
                new KitchenTrace(kitchenResponse.summary(), kitchenTraceNotes(kitchenResponse)));
    }

    public MenuSubstitutionResponse suggestSubstitutes(MenuSubstitutionRequest request) {
        AgentState agentState = agentState(request.sessionId());
        AgentResult decisionResult = agentObservationSupport.observe(
                "menu-service",
                "menu-agent",
                request.sessionId(),
                "unavailableItemId=" + request.unavailableItemId() + " message=" + request.message(),
                agentState,
                () -> agentFactory.builder()
            .sessionId(request.sessionId())
            .state(agentState)
            .systemPrompt("""
            あなたは唯一のクラウドキッチンでアイテムが在庫切れのときに kitchen-agent をサポートする menu-agent です。

            substitution-support-boundary を有効化し、最初に menu_substitution_lookup を呼び出して、お客様の意図に近い代替品の候補を準備してください。
            同ブランドのメニュー内に留め、別のキッチンや別ブランドは言及しないでください。
            同カテゴリの候補を優先し、同カテゴリに妥当候補がない場合だけ広げてください。
            あなたは候補を準備するだけで、在庫可否、調理可否、最終承認は行いません。kitchen-agent が承認する前提で summary を書いてください。
            最終回答は structured_output を使い、selectedItemIds と summary を返してください。
            """)
            .tools(menuSubstitutionLookupTool)
            .build()
                .run("unavailableItemId=" + request.unavailableItemId() + "\nmessage=" + request.message(),
                    MenuSubstitutionDecision.class));
        MenuSubstitutionDecision decision = decisionResult.structuredOutput(MenuSubstitutionDecision.class);
        List<MenuItem> items = resolveSubstitutionItems(request.unavailableItemId(), request.message(), decision.selectedItemIds());
        return new MenuSubstitutionResponse(
                "menu-service",
                "menu-agent",
                repository.substitutionHeadline(items),
                decision.summary(),
                items);
    }

    private String renderSuggestionSummary(
            List<MenuItem> items,
            MenuSuggestionDecision decision,
            KitchenCheckResponse kitchenResponse,
            BigDecimal total) {
        String itemSummary = items.stream()
                .map(item -> item.name() + " x" + item.suggestedQuantity())
                .reduce((left, right) -> left + ", " + right)
                .orElse("本日の人気コンボ");
        String skillPrefix = (decision.skillTag() == null || decision.skillTag().isBlank() || "none".equalsIgnoreCase(decision.skillTag()))
                ? "" : "[" + decision.skillTag() + "] ";
        String reason = decision.recommendationReason() == null || decision.recommendationReason().isBlank()
                ? ""
                : " " + decision.recommendationReason();
        return skillPrefix + "menu-agent が " + itemSummary + " をおすすめします。" + reason
                + " キッチン確認後の提供目安は約" + kitchenResponse.readyInMinutes() + "分。";
    }

    private List<MenuItem> resolveSuggestedItems(String message, List<String> selectedItemIds) {
        if (selectedItemIds == null || selectedItemIds.isEmpty()) {
            return repository.search(message);
        }
        // search() は人数・予算コンテキストから suggestedQuantity を設定するため優先する。
        // AI が返した itemId のうち search 結果に含まれないものは findById でカバーする。
        Map<String, MenuItem> searchById = repository.search(message).stream()
                .collect(java.util.stream.Collectors.toMap(MenuItem::id, item -> item, (a, b) -> a));
        List<MenuItem> resolved = selectedItemIds.stream()
                .map(id -> {
                    MenuItem fromSearch = searchById.get(id);
                    return fromSearch != null ? fromSearch : repository.findById(id).orElse(null);
                })
                .filter(java.util.Objects::nonNull)
                .toList();
        return resolved.isEmpty() ? repository.search(message) : resolved;
    }

    private List<MenuItem> resolveSubstitutionItems(String unavailableItemId, String message, List<String> selectedItemIds) {
        List<MenuItem> fallbackItems = repository.findSubstitutes(unavailableItemId, message);
        if (selectedItemIds == null || selectedItemIds.isEmpty()) {
            return fallbackItems;
        }
        Map<String, MenuItem> fallbackById = fallbackItems.stream()
                .collect(LinkedHashMap::new, (map, item) -> map.put(item.id(), item), Map::putAll);
        List<MenuItem> resolved = selectedItemIds.stream()
                .map(fallbackById::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        return resolved.isEmpty() ? fallbackItems : resolved;
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

    private AgentState agentState(String sessionId) {
        AgentState state = new AgentState();
        if (sessionId != null && !sessionId.isBlank()) {
            state.put(ArachneLifecycleHistoryListener.SESSION_STATE_KEY, sessionId);
        }
        return state;
    }
}