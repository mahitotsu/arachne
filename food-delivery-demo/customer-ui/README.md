# カスタマー UI

この Next.js アプリはフードデリバリーデモのカスタマー向け UI です。

注文インタラクションをステップ型ワークフローで提供しつつ、以下も表示します:

- `customer-service` が提供するカスタマーサインイン状態
- `support-service` から取得するキャンペーンバナー（ホームダッシュボード上部）
- ホームの主要 CTA 直下に置かれたサポートセンター導線（`/support`）
- サポートページで表示するサービス稼働状況インジケーター
- エージェント仕様ビューワー（`/agents`）: `registry-service` の一覧 API から AI エージェントのシステムプロンプト・利用ツール・スキルを取得してカード一覧＋モーダル表示。`order-service` の `order-intake-agent` は intent front door、`menu-service` の `menu-agent` は catalog grounding として表示されます。モーダルは「概要」「ツール・スキル」「API仕様」の3タブに整理されており、「API仕様」タブは当該サービスの OpenAPI (`/v3/api-docs`) を遅延ロードし、各エンドポイントのメソッドバッジ・summary・`x-ai-prompt-contract`（必須入力 / 任意入力 / サービス動作）を表示します

`/agents` で使う `GET /registry/services` は仕様一覧・参照用です。サービス間 collaborator 解決は backend services 側で `POST /registry/discover` を使って行います。

## ページ一覧

| パス | 概要 |
|---|---|
| `/` | サインインページ（demo/family アカウント） |
| `/home` | ホームダッシュボード（メニュー一覧・注文履歴・キャンペーン・サポート導線） |
| `/order` | 注文ワークフロー（4ステップ: 初期入力 → アイテム選択 → 配送選択 → 支払い承認）。セッション確立後は step 1 以降で **実行履歴パネル** を表示 |
| `/support` | サポートチャット（`support-service` の会話型 AI） |
| `/agents` | エージェント仕様ビューワー（`registry-service` から AI エージェント一覧） |

## 注文ワークフロー（Phase 4-C）

`/order` ページはチャット UI を廃止し、4ステップのワークフロー UI になっています。

1. **初期入力** — 自然言語でリクエストを入力 → `POST /api/backend/order/suggest`
2. **アイテム選択** — menu-agent の提案カード一覧（direct item request では明示指定を最優先で grounded し、exact match がない候補は menu 側の代替提案として表示） + 削除/復元 + フィードバックして再提案（`refinement`） → `POST /api/backend/order/confirm-items`
3. **配送選択** — delivery-agent の選択肢カード（推奨マーク付き） → `POST /api/backend/order/confirm-delivery`
4. **支払い承認** — 注文サマリー（アイテム・配送料・合計・支払い方法） + 承認前なら前ステップへ戻る導線 → `POST /api/backend/order/confirm-payment`

注文セッションが確立すると、step 1 以降の `/order` 下部に実行履歴パネルが表示されます。パネルは order/menu/delivery/payment/support の 5 サービスへ BFF 経由で並行取得した履歴を時系列で統合表示し、ステップ遷移時の自動再取得と手動「↺ 更新」に対応します。`order-intake-agent` による意図正規化と `menu-agent` による catalog grounding は component ラベルと補足ノートで見分けられ、direct item 優先 grounding と no-exact-match の menu-side alternatives もこの段で説明されます。

認証トークン、注文セッション、サポート会話セッション、注文スナップショットは customer-ui のサーバー側セッションに保持されます。ブラウザ側には `HttpOnly` Cookie の BFF セッション ID のみを置き、注文復元は `GET /api/backend/order/session` で行います。

## 同一オリジン BFF 契約

| フロントエンドパス | 転送先 |
|---|---|
| `/api/customer/*` | `CUSTOMER_SERVICE_ORIGIN/api/*` |
| `/api/backend/*` | `BACKEND_ORIGIN/api/*` |
| `/api/menu/*` | `MENU_SERVICE_ORIGIN/api/menu/*` |
| `/api/support/*` | `SUPPORT_SERVICE_ORIGIN/api/support/*` |
| `/api/registry/*` | `REGISTRY_SERVICE_ORIGIN/registry/*` |
| `/api/execution-history` | BFF が 5 サービスの実行履歴エンドポイントへ並行ファンアウトし、統合タイムラインを返す |
| `/api/openapi/[serviceName]` | `registry-service` の一覧 API から対象サービスの endpoint を取得したうえで `{endpoint}/v3/api-docs` に転送 |

`/api/auth/session` は customer-ui 自身が持つ認証確認・ログアウト用エンドポイントです。サインイン時に customer-ui が upstream のアクセストークンを受け取り、サーバー側セッションへ保存します。

## ローカル実行

```bash
npm ci
npm run dev
```

注文前にデモアカウントでサインイン:

- `demo / demo-pass`
- `family / family-pass`

コンテナビルドでは、ビルド時に各 `*_ORIGIN` 環境変数を設定する必要があります。compose 設定は Docker 用に各サービスのコンテナ名 URL を渡し、ローカル開発のデフォルトは `localhost` の各ポートになります。

フードデリバリーサービスは反復可能なローカル検証のため、デフォルトで `DELIVERY_MODEL_MODE=deterministic` を使用します。Bedrock を使用して実行する場合は `make up-bedrock` を使用することで、ホストの一時 AWS 認証情報が `ARACHNE_STRANDS_MODEL_ID` および `ARACHNE_STRANDS_MODEL_REGION` とともに compose にエクスポートされます。