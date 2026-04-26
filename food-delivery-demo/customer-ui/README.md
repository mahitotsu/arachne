# カスタマー UI

この Next.js アプリはフードデリバリーデモのカスタマー向け UI です。

注文インタラクションをチャット優先で提供しつつ、以下も表示します:

- `customer-service` が提供するカスタマーサインイン状態
- 現在の注文下書き
- 配送予定時間
- 支払い状態
- 返答の裏にあるサービスローカルトレース（order-service のワークフローステップと下流サービス要約を含む）
- `support-service` から取得するキャンペーンバナー（ホームダッシュボード上部）
- `support-service` から取得するサービス稼働状況インジケーター（ダッシュボード右カラム）
- フローティングサポートエントリボタン（`/support` への常時アクセス可能なエントリポイント）
- エージェント仕様ビューワー（`/agents`）: `registry-service` から全エージェントのケイパビリティ・プロンプト・スキルを取得してカード一覧＋モーダル表示

ローカル実行:

```bash
npm ci
npm run dev
```

注文前にデモアカウントでサインイン:

- `demo / demo-pass`
- `family / family-pass`

アプリは `CUSTOMER_SERVICE_ORIGIN` を通じて `/api/customer/*` を `customer-service` へ、`BACKEND_ORIGIN` を通じて `/api/backend/*` を `order-service` へプロキシします。

デモでは、ブラウザがアクセストークンをローカルストレージに保存し、チャットとセッションリクエストのベアラートークンとして送信します。

コンテナビルドでは、Next.js がスタンドアロン出力に正しいリライトターゲットを組み込むために、ビルド時に `CUSTOMER_SERVICE_ORIGIN` と `BACKEND_ORIGIN` の両方を設定する必要があります。compose 設定は Docker 用に `http://customer-service:8080` と `http://order-service:8080` を渡し、ローカル開発のデフォルトは `http://localhost:8085` と `http://localhost:8080` のままです。

フードデリバリーサービスは反復可能なローカル検証のため、デフォルトで `DELIVERY_MODEL_MODE=deterministic` を使用します。Bedrock を使用して実行する場合は `make up-bedrock` を使用することで、ホストの一時 AWS 認証情報が `ARACHNE_STRANDS_MODEL_ID` および `ARACHNE_STRANDS_MODEL_REGION` とともに compose にエクスポートされます。