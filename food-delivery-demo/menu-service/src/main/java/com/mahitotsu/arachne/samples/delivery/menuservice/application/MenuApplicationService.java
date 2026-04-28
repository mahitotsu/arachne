package com.mahitotsu.arachne.samples.delivery.menuservice.application;

import static com.mahitotsu.arachne.samples.delivery.menuservice.domain.MenuTypes.*;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.mahitotsu.arachne.samples.delivery.menuservice.config.SecurityAccessors;
import com.mahitotsu.arachne.samples.delivery.menuservice.infrastructure.KitchenCheckGateway;
import com.mahitotsu.arachne.samples.delivery.menuservice.infrastructure.MenuRepository;
import com.mahitotsu.arachne.samples.delivery.menuservice.observation.AgentObservationSupport;
import com.mahitotsu.arachne.strands.agent.AgentResult;
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
        AgentResult decisionResult = agentObservationSupport.observe("menu-service", "menu-agent", () -> agentFactory.builder()
            .systemPrompt("""
            あなたは単一ブランドのクラウドキッチンアプリの menu-agent です。

            このビジネスは1つのキッチンのみです。現在のメニューからのみアイテムを推奨してください。
            別の支店、別のキッチン、またはテイクアウトの計画は言及しないでください。

            「おすすめ」「何がいい？」のように広く相談されたときは proactive-recommendation を有効化してください。
            家族・複数人・子ども向けの相談では family-order-guide を有効化してください。
            ユーザープロンプトは query を必須とし、必要に応じて refinement と recent_order が追加されます。
            スキルを使うかどうかに関係なく、最初に必ず catalog_lookup_tool を呼んで現在のカタログを確認してください。
            提案に使ってよい itemId は catalog_lookup_tool が返したものだけです。
            人数・予算・好み・履歴文脈に合う提案セットを選び、最後に calculate_total_tool を使って選んだ itemIds の合計を検算してください。
            最終回答は structured_output を使い、selectedItemIds, skillTag, recommendationReason を返してください。
            欠品、提供可否、調理 ETA、最終的な代替承認は kitchen-service 側で行われます。推薦理由はメニュー意図とカタログ根拠に集中し、在庫や提供時間を約束しないでください。""")
            .tools(catalogLookupTool, calculateTotalTool)
            .build()
            .run(userPrompt.render(), MenuSuggestionDecision.class));
        MenuSuggestionDecision decision = decisionResult.structuredOutput(MenuSuggestionDecision.class);
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
        AgentResult decisionResult = agentObservationSupport.observe("menu-service", "menu-agent", () -> agentFactory.builder()
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
        String skillPrefix = decision.skillTag() == null || decision.skillTag().isBlank() ? "" : "[" + decision.skillTag() + "] ";
        String reason = decision.recommendationReason() == null || decision.recommendationReason().isBlank()
                ? ""
                : " " + decision.recommendationReason();
        return skillPrefix + "menu-agent が " + itemSummary + " をおすすめします。" + reason
                + " キッチン確認後の提供目安は約" + kitchenResponse.readyInMinutes() + "分、合計は"
                + formatYen(total) + "です。";
    }

    private List<MenuItem> resolveSuggestedItems(String message, List<String> selectedItemIds) {
        List<MenuItem> fallbackItems = repository.search(message);
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
}