# フードデリバリーデモ

このディレクトリは、旧マーケットプレイスワークフローサンプルに代わるデモを提供します。

デモは単一ブランドのクラウドキッチン向けデリバリーアプリです。注文体験は `/order` の 4 ステップ workflow-first UI、問い合わせは `/support` の会話面、エージェント説明は `/agents` の registry-backed viewer が担います。キッチンは1つのみ、店内飲食フローなし、ブランチ切り替えなし。フロントエンドは通常のデリバリーアプリのように見えますが、バックエンドは一様な agent-fronted API 群ではありません。`order-service` は公開ワークフロー API の front door、`payment-service` は決定論的な契約面、`menu-service`・`delivery-service`・`support-service` は会話的または agent-fronted な公開面を担います。普通のマイクロサービストラフィックに見えるものの一部が、マルチエージェントコラボレーションパスでもあります。

これは意図的に実行可能な `samples/` カタログには含めていません。ここでのゴールは、Arachne が Spring Boot マイクロサービスにいかに自然に溶け込めるかを示す、構成された実用的なアプリケーションスライスです。

## ランタイム構成

ローカルランタイムが起動するもの:

- `customer-ui`: カスタマー向けの Next.js UI。`/order` は 4 ステップ注文ワークフロー、`/support` は会話型サポート、`/agents` は registry-backed なエージェント仕様ビューワー
- `customer-service`: デモカスタマーディレクトリ、サインイン API、JWT 発行、JWKS 公開
- `support-service`: FAQ、問い合わせ受付、キャンペーン一覧、registry 連携の稼働状況集約
- `order-service`: 公開ワークフロー API、Redis バックドのセッション継続、PostgreSQL バックドの注文永続化
- `registry-service`: サービスのケイパビリティ登録、自然言語 discover、稼働状況集約
- `menu-service`: `menu-agent` によるメニュー検索と代替提案
- `kitchen-service`: `kitchen-agent` による在庫確認と調理時間
- `delivery-service`: `delivery-agent` による ETA 推定とクーリエ計画
- `payment-service`: 決定論的な支払い準備と課金実行
- `hermes-adapter`: 外部 ETA モック、高速配送パートナー、周期的に停止
- `idaten-adapter`: 外部 ETA モック、低コスト配送パートナー、常時稼働
- `postgres`
- `redis`

`icarus-adapter` は起動しない registry-only エントリです。常時 `NOT_AVAILABLE` として登録され、停止中の候補表示をデモします。

会話的な役割を持つダウンストリームサービスは独自のサービスローカルエージェントを持ちます。API は通常の Spring HTTP 境界のままですが、`menu-service`・`kitchen-service`・`delivery-service`・`support-service` ではレスポンステキストや調整動作の一部が各サービス内に埋め込まれた Arachne ランタイムから来ます。一方で `payment-service` は agent runtime を前面に出さず、決定論的ロジックで支払い準備と課金を扱います。
さらに各バックエンドサービスは起動時に `registry-service` へ自分のケイパビリティを登録し、registry は `POST /registry/discover` による service-to-service collaborator resolution と `GET /registry/services` による viewer / inventory の両面を支えます。

## メインデモストーリー

1. カスタマーがデモ ID/パスワードで `customer-service` にサインインし、JWT アクセストークンを受け取る。
2. ブラウザは `customer-ui` のリライトで同一オリジンを維持: `/api/customer/*` は `customer-service` へ、`/api/backend/*` は `order-service` へ転送される。
3. UI はそのベアラートークンを `order-service` へ送信する。
4. `order-service` がステップ別ワークフロー API を通じてダウンストリームサービスを調整し、アクティブな注文セッションを Redis に保持する。
5. `menu-service`、`kitchen-service`、`delivery-service` は同じアクセストークンを検証したうえで service-local agent を通じた提案や調整を返し、`payment-service` は決定論的ロジックで支払い準備と課金を処理する。
6. `support-service` は FAQ、キャンペーン、問い合わせ事例を返し、必要に応じて registry-service の稼働状況と order-service の注文履歴を参照する。
7. 唯一のキッチンがリクエストされたアイテムを提供できない場合、`kitchen-agent` は `menu-agent` に同一ブランドのフォールバックアイテムを問い合わせることができる。
8. UI の `/order` はステップ別の構造化レスポンスを表示し、step 1 以降は execution history をユーザー向け proof surface として表示する。
9. UI の `/support` は `support-service` の FAQ、問い合わせ、キャンペーン、稼働状況を会話面として表示し、注文後のサポート導線も受け持つ。
10. UI の `/agents` は `GET /registry/services` と各 service の OpenAPI を使って、ケイパビリティ、システムプロンプト、ツール、スキル、API 契約を説明面として表示する。
11. 配送見積もりでは `delivery-agent` が自社エクスプレスに加え、registry-service で動的発見した `Hermes` / `Idaten` の外部 ETA 候補を比較し、文脈に応じて推奨を返す。
12. ユーザーが下書きを確定すると、`payment-service` が決定論的課金を実行し、`order-service` が注文を PostgreSQL に記録したうえで `support-service` に事後フィードバック受付を通知する。

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

## OpenAPI 公開

各 Spring サービスは code-first の OpenAPI を個別に公開します。

- OpenAPI JSON: 各 service の `/v3/api-docs`
- Swagger UI: 各 service の `/swagger-ui.html`
- JWT 保護された service でも OpenAPI 閲覧用パスは匿名アクセス可能
- agent へ渡すユーザープロンプト契約は、該当 operation の `x-ai-prompt-contract` extension で確認可能

主要な参照先:

- order-service: `http://localhost:8080/v3/api-docs`
- menu-service: `http://localhost:8081/v3/api-docs`
- kitchen-service: `http://localhost:8082/v3/api-docs`
- delivery-service: `http://localhost:8083/v3/api-docs`
- payment-service: `http://localhost:8084/v3/api-docs`
- customer-service: `http://localhost:8085/v3/api-docs`
- support-service: `http://localhost:8086/v3/api-docs`
- registry-service: `http://localhost:8087/v3/api-docs`
- hermes-adapter: `http://localhost:8088/v3/api-docs`
- idaten-adapter: `http://localhost:8089/v3/api-docs`

Observation / Micrometer の確認:

```bash
curl -X POST http://localhost:8085/api/auth/sign-in \
	-H 'Content-Type: application/json' \
	-d '{"username":"demo","password":"demo-pass"}'

curl http://localhost:8080/actuator/metrics/delivery.order.workflow \
	-H 'Authorization: Bearer <access-token>'

curl 'http://localhost:8080/actuator/metrics/delivery.order.downstream?tag=target:menu-service&tag=operation:suggest&tag=outcome:success' \
	-H 'Authorization: Bearer <access-token>'

curl 'http://localhost:8080/actuator/metrics/delivery.order.registry.lookup' \
	-H 'Authorization: Bearer <access-token>'

curl 'http://localhost:8081/actuator/metrics/delivery.agent.invocation?tag=service:menu-service&tag=agent:menu-agent&tag=outcome:success' \
	-H 'Authorization: Bearer <access-token>'

curl 'http://localhost:8083/actuator/metrics/delivery.agent.tool.call?tag=service:delivery-service&tag=agent:delivery-agent&tag=tool:discover_eta_services&tag=outcome:success' \
	-H 'Authorization: Bearer <access-token>'

curl 'http://localhost:8086/actuator/metrics/delivery.agent.tool.call?tag=service:support-service&tag=agent:support-agent&tag=tool:campaign_lookup&tag=outcome:success' \
	-H 'Authorization: Bearer <access-token>'
```

現時点に見える主要 metric 名:

- `delivery.order.workflow`: 注文 workflow entrypoint (`suggest`, `confirm-items`, `confirm-delivery`, `confirm-payment`, `session`, `recent-order-history`) の count / latency
- `delivery.order.downstream`: `menu-service` / `delivery-service` / `payment-service` / `support-service` への主要 downstream HTTP 呼び出しの count / latency
- `delivery.order.registry.lookup`: `registry-service` への endpoint 解決呼び出しの count / latency
- `delivery.agent.invocation`: `menu-service` / `delivery-service` / `support-service` の service-local agent 実行 count / latency
- `delivery.agent.model.call`: `ArachneLifecycleApplicationEvent` の model call event をもとにした model 応答 count
- `delivery.agent.tool.call`: `ArachneLifecycleApplicationEvent` の tool call event をもとにした tool 実行 count
- `delivery.agent.usage.tokens`: Bedrock 利用時の token usage (`type=input|output`)
- `delivery.agent.usage.cache`: Bedrock 利用時の prompt cache usage (`type=read|write`)

`delivery.order.*` は `target`, `operation`, `outcome` tag に限定している。`delivery.agent.invocation` は `service`, `agent`, `outcome`、`delivery.agent.tool.call` はさらに `tool`、usage metrics は `type` を持つ。`delivery.order.registry.lookup` は runtime の起動順や resolver cache の状態で直近 series が変わるので、まず base metric を見て `availableTags` を確認し、必要ならその時点の tag で絞る。各 service の `/actuator/metrics` は認証必須なので、上記のように sign-in で取った bearer token を付けて確認する。

## エンドポイント

- UI: `http://localhost:3000`
- 注文ワークフロー UI: `http://localhost:3000/order`
- サポート UI: `http://localhost:3000/support`
- エージェント仕様ビューワー: `http://localhost:3000/agents`
- カスタマーサービス: `http://localhost:8085`
- 公開注文 API: `http://localhost:8080/api/order/suggest`
- サポートサービス: `http://localhost:8086`
- レジストリサービス: `http://localhost:8087`
- 公開注文セッション API: `http://localhost:8080/api/order/session/{sessionId}`
- 注文履歴 API: `http://localhost:8080/api/orders/history`
- order-service metrics: `http://localhost:8080/actuator/metrics`
- メニューサービス: `http://localhost:8081`
- menu-service metrics: `http://localhost:8081/actuator/metrics`
- キッチンサービス: `http://localhost:8082`
- 配送サービス: `http://localhost:8083`
- delivery-service metrics: `http://localhost:8083/actuator/metrics`
- 支払いサービス: `http://localhost:8084`
- Hermes アダプター: `http://localhost:8088`
- Idaten アダプター: `http://localhost:8089`
- support-service metrics: `http://localhost:8086/actuator/metrics`

デモサインインアカウント:

- `demo / demo-pass`
- `family / family-pass`

customer-service の主要エンドポイント:

- `POST /api/auth/sign-in`
- `GET /api/customers/me`
- `GET /oauth2/jwks`

registry-service の主要エンドポイント:

- `POST /registry/register`
- `POST /registry/discover`（サービス間 collaborator 解決用。capability-query から endpoint / requestPath を返す）
- `GET /registry/services`（エージェント仕様ビューワーや OpenAPI 参照向けの一覧 API）
- `GET /registry/health`

support-service の主要エンドポイント:

- `POST /api/support/chat`
- `POST /api/support/feedback`
- `GET /api/support/campaigns`
- `GET /api/support/status`


## ドキュメント

- `docs/architecture.md`: ランタイムの役割とオーナーシップ境界
- `docs/apis.md`: 公開・内部 API サーフェス