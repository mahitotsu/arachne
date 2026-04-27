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

    public MenuSuggestionResponse suggest(MenuSuggestionRequest request) {
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

    public MenuSubstitutionResponse suggestSubstitutes(MenuSubstitutionRequest request) {
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