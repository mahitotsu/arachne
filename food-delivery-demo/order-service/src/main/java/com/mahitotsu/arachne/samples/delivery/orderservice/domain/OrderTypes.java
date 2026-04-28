package com.mahitotsu.arachne.samples.delivery.orderservice.domain;

import java.math.BigDecimal;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public final class OrderTypes {

    private OrderTypes() {
    }

    @Schema(description = "注文ワークフローで商品提案または再調整を依頼する要求です。")
    public record SuggestOrderRequest(
            @Schema(description = "既存ワークフローのセッション ID。新規注文では空にします。") String sessionId,
                        @Schema(description = "このターンの注文意図を表す構造化入力です。") OrderIntentInput intent,
            @Schema(description = "応答言語に関する任意の locale ヒント。", example = "ja-JP") String locale,
            @Schema(description = "前回提案を絞り込む任意の追加入力。") String refinement) {

                public SuggestOrderRequest {
                        if (intent == null) {
                                intent = new OrderIntentInput(null, null, null, null);
                        }
                }

                public SuggestOrderRequest(String sessionId, String message, String locale, String refinement) {
                        this(sessionId, new OrderIntentInput(message, null, null, null), locale, refinement);
                }
        }

        @Schema(description = "商品提案に使う注文意図の構造化入力です。")
        public record OrderIntentInput(
                        @Schema(description = "自由記述の元メッセージ。構造化項目で表しきれない意図やニュアンスを保持します。", example = "4人で4000円以内、子ども1人います") String rawMessage,
                        @Schema(description = "想定している人数。", example = "4") Integer partySize,
                        @Schema(description = "許容する予算上限。", example = "4000") BigDecimal budgetUpperBound,
                        @Schema(description = "子どもの人数。", example = "1") Integer childCount) {
    }

    @Schema(description = "配送選択へ進めるために選択した提案商品です。")
    public record ConfirmItemsRequest(
            @Schema(description = "suggest ステップで返されたワークフローのセッション ID。") String sessionId,
            @Schema(description = "suggest で返された提案から選択した商品。") List<SelectedProposalItem> items) {
    }

    @Schema(description = "現在のワークフローセッションで選択した配送レーンです。")
    public record ConfirmDeliveryRequest(
            @Schema(description = "前段ステップで返されたワークフローのセッション ID。") String sessionId,
            @Schema(description = "選択した配送候補のコード。", example = "express") String deliveryCode) {
    }

    @Schema(description = "現在のワークフローセッションに対する明示的な支払い確定要求です。")
    public record ConfirmPaymentRequest(@Schema(description = "前段ステップで返されたワークフローのセッション ID。") String sessionId) {
    }

    @Schema(description = "クライアントへ返す商品提案とワークフロースナップショットです。")
    public record SuggestOrderResponse(
            @Schema(description = "後続の確認 API で使うワークフローのセッション ID。") String sessionId,
            @Schema(description = "この操作後の現在のワークフロー段階。", example = "item-selection") String workflowStep,
            @Schema(description = "提案結果を示す短いユーザー向け見出し。") String headline,
            @Schema(description = "この商品群が提案された理由の要約。") String summary,
            @Schema(description = "調理から受け渡しまでの見込み時間（分）。") int etaMinutes,
            @Schema(description = "現在の注文意図に対する提案商品。") List<ProposalItem> proposals,
            @Schema(description = "提案後の現在の注文下書き状態。") OrderDraft draft,
            @Schema(description = "このターンで使われた service と agent の要約 trace。") List<ServiceTrace> trace) {
    }

    @Schema(description = "商品確定後の配送候補と更新済み下書きです。")
    public record ConfirmItemsResponse(
            @Schema(description = "ワークフローのセッション ID。") String sessionId,
            @Schema(description = "商品確定後の現在のワークフロー段階。", example = "delivery-selection") String workflowStep,
            @Schema(description = "配送選択ステップ向けの短いユーザー向け見出し。") String headline,
            @Schema(description = "チェックアウトへ引き継ぐ正規化済みの注文明細。") List<OrderLineItem> items,
            @Schema(description = "現在の下書きで利用可能な配送候補。") List<DeliveryOptionChoice> deliveryOptions,
            @Schema(description = "現在の注文下書き状態。") OrderDraft draft,
            @Schema(description = "このターンで使われた service と agent の要約 trace。") List<ServiceTrace> trace) {
    }

    @Schema(description = "配送確定後の支払い要約と更新済み下書きです。")
    public record ConfirmDeliveryResponse(
            @Schema(description = "ワークフローのセッション ID。") String sessionId,
            @Schema(description = "配送確定後の現在のワークフロー段階。", example = "payment") String workflowStep,
            @Schema(description = "支払いステップ向けの短いユーザー向け見出し。") String headline,
            @Schema(description = "明示的な確定のために準備された支払い要約。") PaymentSummary payment,
            @Schema(description = "現在の注文下書き状態。") OrderDraft draft,
            @Schema(description = "このターンで使われた service と agent の要約 trace。") List<ServiceTrace> trace) {
    }

    @Schema(description = "最終的な注文確定結果です。")
    public record ConfirmPaymentResponse(
            @Schema(description = "ワークフローのセッション ID。") String sessionId,
            @Schema(description = "支払い確定後の現在のワークフロー段階。", example = "completed") String workflowStep,
            @Schema(description = "customer に表示する最終ワークフロー要約。") String summary,
            @Schema(description = "最終 orderId を含む確定済み注文下書き。") OrderDraft draft,
            @Schema(description = "このターンで使われた service と agent の要約 trace。") List<ServiceTrace> trace) {
    }

    @Schema(description = "ブラウザのワークフローで復元可能なセッションスナップショットです。")
    public record OrderSessionView(
            @Schema(description = "ワークフローのセッション ID。") String sessionId,
            @Schema(description = "現在のワークフロー段階。") String workflowStep,
            @Schema(description = "現在の注文下書きスナップショット。") OrderDraft draft,
            @Schema(description = "セッションが商品選択中であれば保留中の提案商品。") List<ProposalItem> pendingProposal,
            @Schema(description = "セッションが配送選択中であれば保留中の配送候補。") List<DeliveryOptionChoice> pendingDeliveryOptions) {
    }

    @Schema(description = "UI の透明性向上のために返す service / agent trace 項目です。")
    public record ServiceTrace(
            @Schema(description = "この trace 項目を生成した service 境界。") String service,
            @Schema(description = "この項目に責任を持つ agent または component 名。") String agent,
            @Schema(description = "trace イベントの短い見出し。") String headline,
            @Schema(description = "UI 向けの詳細な trace 説明。") String detail) {
    }

    @Schema(description = "商品提案ステップで返す提案商品です。")
    public record ProposalItem(
            @Schema(description = "安定した catalog item id。") String itemId,
            @Schema(description = "customer に表示する商品名。") String name,
            @Schema(description = "この商品に対する提案数量。") int quantity,
            @Schema(description = "この商品の単価。") BigDecimal unitPrice,
            @Schema(description = "この商品が提案された理由。") String reason) {
    }

    @Schema(description = "確定のために service へ返す選択済み提案商品です。")
    public record SelectedProposalItem(@Schema(description = "client が選択した安定した catalog item id。") String itemId) {
    }

    @Schema(description = "現在の注文下書きで利用可能な配送候補です。")
    public record DeliveryOptionChoice(
            @Schema(description = "安定した配送候補コード。") String code,
            @Schema(description = "配送候補の表示ラベル。") String label,
            @Schema(description = "見込み到着時間（分）。") int etaMinutes,
            @Schema(description = "この配送候補で請求する配送手数料。") BigDecimal fee,
            @Schema(description = "customer に表示するこの候補の理由。") String reason,
            @Schema(description = "この候補が推奨かどうか。") boolean recommended) {
    }

    @Schema(description = "配送選択後に準備される支払い要約です。")
    public record PaymentSummary(
            @Schema(description = "保留中のチェックアウトに含まれる商品。") List<OrderLineItem> items,
            @Schema(description = "選択済みの配送料。") BigDecimal deliveryFee,
            @Schema(description = "確定時に課金される最終合計。") BigDecimal total,
            @Schema(description = "選択済み支払い方法のラベル。") String paymentMethod) {
    }

    @Schema(description = "ワークフロー全体で維持する現在の注文下書きです。")
    public record OrderDraft(
            @Schema(description = "現在のワークフロー段階に対応する下書き状態。") String status,
            @Schema(description = "現在の下書き注文明細。") List<OrderLineItem> items,
            @Schema(description = "配送料を含まない現在の小計。") BigDecimal subtotal,
            @Schema(description = "利用可能であれば配送料を含む現在の合計。") BigDecimal total,
            @Schema(description = "customer に表示する ETA ラベル。") String etaLabel,
            @Schema(description = "現在のワークフロー段階における支払い状態。") String paymentStatus,
            @Schema(description = "利用可能な場合の選択済み支払い方法ラベル。") String paymentMethod,
            @Schema(description = "チェックアウト完了後の確定済み orderId。") String orderId) {
    }

    @Schema(description = "下書きと支払い要約で使う正規化済みの注文明細です。")
    public record OrderLineItem(
            @Schema(description = "商品の表示名。") String name,
            @Schema(description = "この明細で選択した数量。") int quantity,
            @Schema(description = "この明細の単価。") BigDecimal unitPrice,
            @Schema(description = "明細と一緒に表示する任意のメモ。") String note) {
    }

    public record OrderSession(
            String sessionId,
            String workflowStep,
            OrderDraft draft,
            PendingProposal pendingProposal,
            PendingDeliverySelection pendingDeliverySelection,
            DeliveryOptionChoice selectedDelivery) {
    }

    public record PendingProposal(
            String customerMessage,
            String locale,
            String summary,
            List<ProposalItem> items,
            int etaMinutes,
            KitchenTraceView kitchenTrace) {
    }

    public record PendingDeliverySelection(String summary, List<DeliveryOptionChoice> options) {
    }

    public record StoredOrder(String orderId, String itemSummary, BigDecimal subtotal, BigDecimal total, String etaLabel, String paymentStatus) {
    }

    @Schema(description = "履歴エンドポイントが返すコンパクトな注文履歴項目です。")
    public record StoredOrderSummary(
            @Schema(description = "確定済み orderId。") String orderId,
            @Schema(description = "注文商品のコンパクトなテキスト要約。") String itemSummary,
            @Schema(description = "最終課金合計。") BigDecimal total,
            @Schema(description = "注文に記録された ETA ラベル。") String etaLabel,
            @Schema(description = "最終的な支払い状態。") String paymentStatus,
            @Schema(description = "確定済み注文の作成タイムスタンプ。") String createdAt) {
    }

        public record MenuSuggestionRequest(String sessionId, String query, String refinement, String recentOrderSummary) {

                public MenuSuggestionRequest(String sessionId, String query) {
                        this(sessionId, query, null, null);
                }
    }

    public record MenuSuggestionResponse(
            String service,
            String agent,
            String headline,
            String summary,
            List<MenuItemView> items,
            int etaMinutes,
            KitchenTraceView kitchenTrace) {
    }

    public record MenuItemView(String id, String name, String description, BigDecimal price, int suggestedQuantity,
            String category, List<String> tags) {
    }

    public record KitchenTraceView(String summary, List<String> notes) {
    }

    public record DeliveryQuoteRequest(String sessionId, String message, List<String> itemNames) {
    }

    public record DeliveryQuoteResponse(
            String service,
            String agent,
            String headline,
            String summary,
            List<DeliveryOptionView> options,
            String recommendedTier,
            String recommendationReason) {
    }

    public record DeliveryOptionView(String code, String label, int etaMinutes, BigDecimal fee) {
    }

        public record PaymentPrepareRequest(String sessionId, PaymentInstructionInput instruction, BigDecimal total, boolean confirmRequested) {

                public PaymentPrepareRequest {
                        if (instruction == null) {
                                instruction = new PaymentInstructionInput(null, null);
                        }
                }

                public PaymentPrepareRequest(String sessionId, String message, BigDecimal total, boolean confirmRequested) {
                        this(sessionId, new PaymentInstructionInput(message, null), total, confirmRequested);
                }
        }

        public record PaymentInstructionInput(String rawMessage, PaymentMethodPreference requestedMethod) {
        }

        public enum PaymentMethodPreference {
                APPLE_PAY,
                CASH_ON_DELIVERY,
                SAVED_CARD
    }

    public record PaymentPrepareResponse(
            String service,
            String agent,
            String headline,
            String summary,
            String selectedMethod,
            BigDecimal total,
            String paymentStatus,
            boolean charged,
            String authorizationId) {
    }

    public record SupportFeedbackRequestPayload(String orderId, Integer rating, String message) {
    }

    public record SupportFeedbackResponse(
            String service,
            String agent,
            String headline,
            String summary,
            String classification,
            boolean escalationRequired) {
    }

    public record RegistryServiceDescriptorPayload(
            String serviceName,
            String endpoint,
            String capability,
            String agentName,
            String requestMethod,
            String requestPath,
            String status) {
    }
}