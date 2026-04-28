# フードデリバリーデモ API 仕様

## 公開 API

## ユーザープロンプト契約の原則

このデモでは、AI エージェントを HTTP API の内側に自然に埋め込んでいる。
そのため API 契約は JSON request/response だけでなく、各 endpoint が内部 agent にどのようなユーザープロンプト契約を渡すかも含めて読む。

原則:

- ユーザープロンプト契約は endpoint ごと、agent ごとに明示する。全 agent で無理に統一しない。
- 公開 API ではブラウザやクライアントが送る request フィールドの意味を契約とする。
- 内部 API では downstream service に渡す request DTO の意味を契約とする。
- system prompt や SKILL は agent の実行方針であり、ユーザープロンプト契約は「その agent が入力として受ける業務文脈」を表す。
- 実装上は agent 呼び出し直前で service-local な prompt contract 型に変換されることがあるが、API としては下記の意味契約を安定面として扱う。

`order-service`

- `POST /api/order/suggest`
  初期入力または refinement を受け取り、提案アイテム、ETA、提案トレースを返す。
  ユーザープロンプト契約:
  `message` は今回の注文意図を表す自然言語入力。必須。
  `refinement` は直前の提案に対する追加条件や修正指示。任意。
  `locale` は応答言語ヒント。任意。
  `message` が「前回」「いつもの」などを含む場合、order-service は直近注文履歴を取得し、menu-service へ recent-order 文脈を補強して渡してよい。
- `POST /api/order/confirm-items`
  選択した提案アイテムを受け取り、配送候補と更新済み下書きを返す。
- `POST /api/order/confirm-delivery`
  選択した配送レーンを受け取り、支払いサマリーと更新済み下書きを返す。
- `POST /api/order/confirm-payment`
  明示的な注文確定を受け取り、決済結果と確定済み下書きを返す。
- `GET /api/order/session/{sessionId}`
  ブラウザリフレッシュ用に現在のワークフロー段階、保留中提案、配送候補、注文下書きを復元する。
- `GET /api/orders/history`
  現在のカスタマーの直近注文履歴を返す。

`support-service`

- `POST /api/support/chat`
  JWT 認証済みのサポート問い合わせを受け取り、FAQ、キャンペーン、稼働状況、必要に応じて注文履歴をまとめて返す。
  ユーザープロンプト契約:
  `message` は問い合わせ本文を表す自然言語入力。必須。
  認証コンテキストの customerId は request body ではなく認証情報から補われ、support-agent に渡る問い合わせ文脈の一部として扱われる。
- `POST /api/support/feedback`
  注文に関する問い合わせやフィードバックを受け取り、分類結果とエスカレーション要否を返す。
- `GET /api/support/campaigns`
  現在有効なキャンペーン一覧を返す。
- `GET /api/support/status`
  registry-service の集約ヘルスをもとにしたサービス稼働状況を返す。

`registry-service`

- `POST /registry/register`
  サービス起動時にケイパビリティ、エンドポイント、エージェント仕様、ヘルス URL を登録する。
- `POST /registry/discover`
  自然言語クエリを受け取り、マッチしたサービス記述子と capability-registry-agent の要約を返す。
- `GET /registry/services`
  全登録サービスの仕様一覧を返す。停止中の `icarus-adapter` も含む。
- `GET /registry/health`
  登録済みサービスの集約ヘルス状態を返す。

## 内部 API

`menu-service`

- `POST /internal/menu/suggest`
  現在のユーザーの意図を受け取り、メニュー候補と `menu-agent` のコメントを返す。
  ユーザープロンプト契約:
  `query` は注文したい内容、人数、予算、好みなどの主要求。必須。
  `refinement` は再提案時の追加条件。任意。
  `recentOrderSummary` は repeat order 文脈。任意。
  menu-agent には query を必須、refinement と recent_order を任意の補助文脈として渡す。

`kitchen-service`

- `POST /internal/kitchen/check`
  選択したメニューアイテムの ID を受け取り、在庫・調理時間・`kitchen-agent` からの代替ガイダンス、および `kitchen-agent` が `menu-agent` に代替ヘルプを求めた場合のコラボレータートレースエントリを返す。
  ユーザープロンプト契約:
  `itemIds` は確認対象アイテム群。必須。
  `message` は元のカスタマー意図や制約。必須。
  kitchen-agent には items と message の 2 軸で文脈を渡す。

`menu-service`

- `POST /internal/menu/substitutes`
  在庫切れアイテムとカスタマーコンテキストを受け取り、`kitchen-agent` が検証するための menu-agent フォールバック候補を返す。
  ユーザープロンプト契約:
  `unavailableItemId` は欠品した元アイテム。必須。
  `message` は元のカスタマー意図。必須。
  menu-agent は欠品 item と customer context の両方を見て、同ブランド内の代替候補だけを返す。

`delivery-service`

- `POST /internal/delivery/quote`
  注文下書きを受け取り、`delivery-agent` から配送 ETA オプションを返す。自社エクスプレス、registry discover で見つかった AVAILABLE な外部 ETA 候補を同一レスポンスへまとめ、`recommendedTier` と `recommendationReason` で文脈別推奨も返す。
  ユーザープロンプト契約:
  `message` は配送方針に関わるカスタマー意図。必須。例: 早さ重視、安さ重視。
  `itemNames` は配送見積もり対象のアイテム名一覧。必須。
  delivery-agent には customer_message と item_names を渡し、優先度判断と説明に使う。

`payment-service`

- `POST /internal/payment/prepare`
  注文下書きを受け取り、支払い準備状況とオプションの決定論的課金実行を返す。

`support-service`

- `GET /api/orders/history` (`order-service` 依存)
  サポートチャットが認証済みカスタマーの直近注文履歴を参照する際に利用する。
- `GET /registry/health` (`registry-service` 依存)
  サポートチャットとダッシュボード向けに現在のサービス稼働状況を取得する。
- `POST /api/support/feedback` (`order-service` 依存)
  注文確定後に order-service が事後サポート受付を通知する際に利用する。

`hermes-adapter`

- `POST /adapter/eta`
  外部高速配送パートナーの ETA、混雑度、料金を返す。混雑時は `NOT_AVAILABLE` になり得る。
- `GET /adapter/health`
  アダプター独自の `AVAILABLE` / `NOT_AVAILABLE` を返す。

`idaten-adapter`

- `POST /adapter/eta`
  外部低コスト配送パートナーの ETA、混雑度、料金を返す。
- `GET /adapter/health`
  アダプター独自の `AVAILABLE` を返す。

## レスポンス設計の原則

## プロンプト契約の運用方針

- API 仕様に書くのは、LLM 向けの最終文字列そのものではなく、endpoint が保証する意味契約とフィールド責務。
- 文字列レンダリング形式は service-local 実装詳細として変更され得るが、意味が変わる場合は API 契約変更として扱う。
- downstream service が別 service の agent を呼ぶ場合、その request DTO は通常の API 契約と同様に versioning とレビュー対象にする。

各内部サービスは以下の両方を返す:

- オーケストレーター向けの決定論的な構造化データ
- UI トレース向けのエージェントサマリー文字列

`order-service` は現在ターンの決定論的なワークフローサマリーも返すため、トレースには以下が表示される:

- 現在のワークフローステップ
- 呼び出されたサービス境界
- 提案、配送、支払いの各段階で返された要約

この分離により Spring が正確性を管理しつつ、Arachne レイヤーも可視化される。