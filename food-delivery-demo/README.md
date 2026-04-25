# フードデリバリーデモ

このディレクトリは、旧マーケットプレイスワークフローサンプルに代わるデモを提供します。

デモは単一ブランドのクラウドキッチン向けのチャット優先デリバリーアプリです。キッチンは1つのみ、店内飲食フローなし、ブランチ切り替えなし。フロントエンドは通常のデリバリーアプリのように見えますが、すべてのバックエンド API はサービスローカルの Arachne エージェントが前面に立っています。普通のマイクロサービストラフィックに見えるものが、マルチエージェントコラボレーションパスでもあります。

これは意図的に実行可能な `samples/` カタログには含めていません。ここでのゴールは、Arachne が Spring Boot マイクロサービスにいかに自然に溶け込めるかを示す、構成された実用的なアプリケーションスライスです。

## ランタイム構成

ローカルランタイムが起動するもの:

- `customer-ui`: カスタマー向けフローの Next.js チャット UI
- `customer-service`: デモカスタマーディレクトリ、サインイン API、JWT 発行、JWKS 公開
- `order-service`: 公開ワークフロー API、Redis バックドのセッション継続、PostgreSQL バックドの注文永続化
- `menu-service`: `menu-agent` によるメニュー検索と代替提案
- `kitchen-service`: `kitchen-agent` による在庫確認と調理時間
- `delivery-service`: `delivery-agent` による ETA 推定とクーリエ計画
- `payment-service`: 決定論的な支払い準備と課金実行
- `postgres`
- `redis`

各ダウンストリームサービスは独自のサービスローカルエージェントを持ちます。API は通常の Spring HTTP 境界のままですが、レスポンステキストと調整動作は各サービス内に埋め込まれた Arachne ランタイムから来ます。

## メインデモストーリー

1. カスタマーがデモ ID/パスワードで `customer-service` にサインインし、JWT アクセストークンを受け取る。
2. ブラウザは `customer-ui` のリライトで同一オリジンを維持: `/api/customer/*` は `customer-service` へ、`/api/backend/*` は `order-service` へ転送される。
3. UI はそのベアラートークンを `order-service` へ送信する。
4. `order-service` がステップ別ワークフロー API を通じてダウンストリームサービスを調整し、アクティブな注文セッションを Redis に保持する。
5. `menu-service`、`kitchen-service`、`delivery-service`、`payment-service` が同じアクセストークンを検証し、それぞれの Arachne エージェントを通じて回答する。
6. 唯一のキッチンがリクエストされたアイテムを提供できない場合、`kitchen-agent` は `menu-agent` に同一ブランドのフォールバックアイテムを問い合わせることができる。
7. UI はユーザー向け返答とサービス/エージェントトレースの両方を表示し、マイクロサービス構造とマルチエージェント構造の両方が明確に見える。
8. 両レーンが利用可能な場合、ユーザーはパートナースタンダード配送と自社エクスプレス配送のどちらかを選択する。
9. ユーザーが下書きを確定すると、`payment-service` が決定論的課金を実行し、`order-service` が注文を PostgreSQL に記録する。

## ローカルコマンド

このディレクトリから `make` を使用:

```bash
make up
make ps
make smoke
make down
```

フロントエンド専用コマンド:

```bash
make ui-install
make ui-build
make ui-dev
```

バックエンド検証:

```bash
make test
```

## エンドポイント

- UI: `http://localhost:3000`
- カスタマーサービス: `http://localhost:8085`
- 公開注文 API: `http://localhost:8080/api/order/suggest`
- 公開注文セッション API: `http://localhost:8080/api/order/session/{sessionId}`
- 注文履歴 API: `http://localhost:8080/api/orders/history`
- メニューサービス: `http://localhost:8081`
- キッチンサービス: `http://localhost:8082`
- 配送サービス: `http://localhost:8083`
- 支払いサービス: `http://localhost:8084`

デモサインインアカウント:

- `demo / demo-pass`
- `family / family-pass`

customer-service の主要エンドポイント:

- `POST /api/auth/sign-in`
- `GET /api/customers/me`
- `GET /oauth2/jwks`


## ドキュメント

- `docs/architecture.md`: ランタイムの役割とオーナーシップ境界
- `docs/apis.md`: 公開・内部 API サーフェス