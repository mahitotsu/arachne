# フードデリバリーデモ API 仕様

## 公開 API

`order-service`

- `POST /api/chat`
  チャットターンを受け取り、更新された会話・注文下書き・ルーティング決定・エージェントトレースを返す。
- `GET /api/session/{sessionId}`
  ブラウザリフレッシュ用に現在のチャット履歴と下書きを復元する。

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
  注文下書きを受け取り、`delivery-agent` から配送 ETA オプションを返す。

`payment-service`

- `POST /internal/payment/prepare`
  注文下書きを受け取り、支払い準備状況とオプションの決定論的課金実行を返す。

## レスポンス設計の原則

各内部サービスは以下の両方を返す:

- オーケストレーター向けの決定論的な構造化データ
- UI トレース向けのエージェントサマリー文字列

`order-service` は現在ターンの決定論的なルーティングサマリーも返すため、トレースには以下が表示される:

- 解釈されたカスタマーの意図
- 選択されたワークフロースキル
- そのターンに使用されたワークフローエントリーステップ

この分離により Spring が正確性を管理しつつ、Arachne レイヤーも可視化される。