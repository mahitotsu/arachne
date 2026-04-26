# フードデリバリーデモ API 仕様

## 公開 API

`order-service`

- `POST /api/order/suggest`
  初期入力または refinement を受け取り、提案アイテム、ETA、提案トレースを返す。
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

`kitchen-service`

- `POST /internal/kitchen/check`
  選択したメニューアイテムの ID を受け取り、在庫・調理時間・`kitchen-agent` からの代替ガイダンス、および `kitchen-agent` が `menu-agent` に代替ヘルプを求めた場合のコラボレータートレースエントリを返す。

`menu-service`

- `POST /internal/menu/substitutes`
  在庫切れアイテムとカスタマーコンテキストを受け取り、`kitchen-agent` が検証するための menu-agent フォールバック候補を返す。

`delivery-service`

- `POST /internal/delivery/quote`
  注文下書きを受け取り、`delivery-agent` から配送 ETA オプションを返す。自社エクスプレス、registry discover で見つかった AVAILABLE な外部 ETA 候補を同一レスポンスへまとめ、`recommendedTier` と `recommendationReason` で文脈別推奨も返す。

`payment-service`

- `POST /internal/payment/prepare`
  注文下書きを受け取り、支払い準備状況とオプションの決定論的課金実行を返す。

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

各内部サービスは以下の両方を返す:

- オーケストレーター向けの決定論的な構造化データ
- UI トレース向けのエージェントサマリー文字列

`order-service` は現在ターンの決定論的なワークフローサマリーも返すため、トレースには以下が表示される:

- 現在のワークフローステップ
- 呼び出されたサービス境界
- 提案、配送、支払いの各段階で返された要約

この分離により Spring が正確性を管理しつつ、Arachne レイヤーも可視化される。