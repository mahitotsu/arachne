# カスタマー UI

この Next.js アプリはフードデリバリーデモのカスタマー向け UI です。

注文インタラクションをステップ型ワークフローで提供しつつ、以下も表示します:

- `customer-service` が提供するカスタマーサインイン状態
- `support-service` から取得するキャンペーンバナー（ホームダッシュボード上部）
- `support-service` から取得するサービス稼働状況インジケーター（ダッシュボード右カラム）
- フローティングサポートエントリボタン（`/support` への常時アクセス可能なエントリポイント）
- エージェント仕様ビューワー（`/agents`）: `registry-service` から全エージェントのケイパビリティ・プロンプト・スキルを取得してカード一覧＋モーダル表示

## ページ一覧

| パス | 概要 |
|---|---|
| `/` | サインインページ（demo/family アカウント） |
| `/home` | ホームダッシュボード（メニュー一覧・注文履歴・キャンペーン・稼働状況） |
| `/order` | 注文ワークフロー（4ステップ: 初期入力 → アイテム選択 → 配送選択 → 支払い承認） |
| `/support` | サポートチャット（`support-service` の会話型 AI） |
| `/agents` | エージェント仕様ビューワー（`registry-service` から全サービス一覧） |

## 注文ワークフロー（Phase 4-C）

`/order` ページはチャット UI を廃止し、4ステップのワークフロー UI になっています。

1. **初期入力** — 自然言語でリクエストを入力 → `POST /api/backend/order/suggest`
2. **アイテム選択** — menu-agent の提案カード一覧 + フィードバックして再提案（`refinement`） → `POST /api/backend/order/confirm-items`
3. **配送選択** — delivery-agent の選択肢カード（推奨マーク付き） → `POST /api/backend/order/confirm-delivery`
4. **支払い承認** — 注文サマリー（アイテム・配送料・合計・支払い方法） → `POST /api/backend/order/confirm-payment`

セッションはブラウザリロード後も `GET /api/backend/order/session/{sessionId}` で復元されます。

## 同一オリジンプロキシ契約

| フロントエンドパス | 転送先 |
|---|---|
| `/api/customer/*` | `CUSTOMER_SERVICE_ORIGIN/api/*` |
| `/api/backend/*` | `BACKEND_ORIGIN/api/*` |
| `/api/menu/*` | `MENU_SERVICE_ORIGIN/api/menu/*` |
| `/api/support/*` | `SUPPORT_SERVICE_ORIGIN/api/support/*` |
| `/api/registry/*` | `REGISTRY_SERVICE_ORIGIN/registry/*` |

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