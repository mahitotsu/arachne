package com.mahitotsu.arachne.samples.delivery.orderservice.application;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.NormalizedOrderIntent;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.OrderIntentInput;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.OrderSession;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.StoredOrder;
import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.SuggestOrderRequest;
import com.mahitotsu.arachne.samples.delivery.orderservice.observation.OrderAgentObservationSupport;
import com.mahitotsu.arachne.strands.agent.AgentResult;
import com.mahitotsu.arachne.strands.spring.AgentFactory;

@Service
class ArachneOrderIntentPlanner implements OrderIntentPlanner {

    static final String AGENT_NAME = "order-intake-agent";

    private static final List<String> DIRECT_ITEM_KEYWORDS = List.of(
            "セット", "box", "ボックス", "burger", "バーガー", "wrap", "ラップ", "soda", "ソーダ",
            "latte", "ラテ", "フライ", "fries", "チキン", "chicken", "サーモン", "salmon", "bowl", "dessert");

    private final AgentFactory agentFactory;
    private final OrderAgentObservationSupport observationSupport;

    ArachneOrderIntentPlanner(
            AgentFactory agentFactory,
            OrderAgentObservationSupport observationSupport) {
        this.agentFactory = agentFactory;
        this.observationSupport = observationSupport;
    }

    @Override
    public NormalizedOrderIntent plan(
            String sessionId,
            SuggestOrderRequest request,
            OrderSession existing,
            Optional<StoredOrder> recentOrder) {
        OrderIntentAgentUserPrompt prompt = OrderIntentAgentUserPrompt.from(request, existing, recentOrder);
        AgentResult result = observationSupport.observe(
                "order-service",
                AGENT_NAME,
                sessionId,
                prompt.render(),
                () -> agentFactory.builder()
                        .systemPrompt(systemPrompt())
                        .build()
                        .run(prompt.render(), NormalizedOrderIntent.class));
        return normalize(result.structuredOutput(NormalizedOrderIntent.class), request, existing, recentOrder);
    }

    private NormalizedOrderIntent normalize(
            NormalizedOrderIntent planned,
            SuggestOrderRequest request,
            OrderSession existing,
            Optional<StoredOrder> recentOrder) {
        String customerMessage = firstNonBlank(
                planned == null ? null : planned.customerMessage(),
                MenuSuggestionPromptRequestFactory.resolveCustomerMessage(request, existing),
                recentOrder.map(StoredOrder::itemSummary).orElse(null));
        String intentMode = normalizeIntentMode(planned == null ? null : planned.intentMode(), request, existing, customerMessage);
        String recentOrderSummary = MenuSuggestionPromptRequestFactory.needsRecentOrderContext(customerMessage)
                ? firstNonBlank(planned == null ? null : planned.recentOrderSummary(), recentOrder.map(StoredOrder::itemSummary).orElse(null))
                : null;
        String directItemHint = blankToNull(planned == null ? null : planned.directItemHint());
        if (directItemHint == null && "DIRECT_ITEM".equals(intentMode)) {
            directItemHint = customerMessage;
        }
        String menuQuery = firstNonBlank(
                planned == null ? null : planned.menuQuery(),
                buildMenuQuery(customerMessage, directItemHint, request.intent()));
        String rationale = firstNonBlank(
                planned == null ? null : planned.rationale(),
                fallbackRationale(intentMode));
        OrderIntentInput intent = request.intent();
        return new NormalizedOrderIntent(
                customerMessage,
                intentMode,
                menuQuery,
                directItemHint,
                intent == null ? null : intent.partySize(),
                intent == null ? null : intent.budgetUpperBound(),
                intent == null ? null : intent.childCount(),
                recentOrderSummary,
                rationale);
    }

    private String systemPrompt() {
        return """
                あなたは order-service の order-intake-agent です。
                あなたの責務は customer の注文意図を正規化し、menu-service へ catalog grounding 用の構造化 handoff を返すことです。

                やること:
                - DIRECT_ITEM / RECOMMENDATION / REORDER / REFINEMENT のどれかを intentMode に設定する
                - customerMessage に workflow 全体で保持すべき意図の要約を書く
                - menuQuery に menu-service が catalog grounding に使う query を書く
                - directItemHint は明示的な商品指定があるときだけ入れる
                - recentOrderSummary は再注文文脈が必要なときだけ入れる
                - rationale には短く理由を書く

                やってはいけないこと:
                - 具体的な商品 ID や最終提案商品を決めない
                - 在庫や調理可否を判断しない
                - 配送や支払いの意思決定をしない

                人数・予算・子ども人数などの構造化制約は失わずに保持してください。
                最終回答は structured_output のみを使って返してください。
                """;
    }

    private String normalizeIntentMode(
            String plannedIntentMode,
            SuggestOrderRequest request,
            OrderSession existing,
            String customerMessage) {
        String normalized = blankToNull(plannedIntentMode);
        if (normalized != null) {
            String upper = normalized.toUpperCase(Locale.ROOT);
            if (List.of("DIRECT_ITEM", "RECOMMENDATION", "REORDER", "REFINEMENT").contains(upper)) {
                return upper;
            }
        }
        if (request.refinement() != null && !request.refinement().isBlank() && existing.pendingProposal() != null) {
            return "REFINEMENT";
        }
        if (MenuSuggestionPromptRequestFactory.needsRecentOrderContext(customerMessage)) {
            return "REORDER";
        }
        if (looksLikeDirectItemRequest(customerMessage)) {
            return "DIRECT_ITEM";
        }
        return "RECOMMENDATION";
    }

    private boolean looksLikeDirectItemRequest(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        if (normalized.contains("おすすめ") || normalized.contains("何か") || normalized.contains("向け") || normalized.contains("いつもの")) {
            return false;
        }
        return DIRECT_ITEM_KEYWORDS.stream().anyMatch(normalized::contains);
    }

    private String buildMenuQuery(String customerMessage, String directItemHint, OrderIntentInput intent) {
        LinkedHashSet<String> parts = new LinkedHashSet<>();
        add(parts, directItemHint);
        add(parts, customerMessage);
        add(parts, MenuSuggestionPromptRequestFactory.structuredIntentSummary(intent));
        return String.join("、", parts);
    }

    private void add(LinkedHashSet<String> parts, String value) {
        String normalized = blankToNull(value);
        if (normalized != null) {
            parts.add(normalized);
        }
    }

    private String fallbackRationale(String intentMode) {
        return switch (intentMode) {
            case "DIRECT_ITEM" -> "商品名らしい指定があるため catalog grounding に直接渡します。";
            case "REORDER" -> "再注文の文脈があるため前回注文を参照する形に正規化しました。";
            case "REFINEMENT" -> "既存提案の再調整として扱います。";
            default -> "人数や予算などの条件から recommendation planning として扱います。";
        };
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = blankToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return "";
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}