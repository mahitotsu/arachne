# Food Delivery Demo 改修プラン

## ゴール

Arachne が Spring Boot マイクロサービスに自然に溶け込めることを示す、より完成度の高い実用的なデモへ。以下の3つの柱で構成する：

1. **AIを使う場所・使わない場所の明確化** — 正当化できないエージェントを削除し、AIが本当に価値を出す場面を際立たせる
2. **AI ネイティブ サービスメッシュ** — サービス発見と連携をAIエージェントが担い、業務専門家がスキルを書けるアーキテクチャへ
3. **デモの横幅拡張** — 注文フロー以外（サポートチャット、エージェント仕様ビューワー）を追加し、Arachne の多様な活用を見せる

---

## 全体完了基準

- [ ] `mvn test` (food-delivery-demo) が全テストパス
- [ ] `make up` で全サービスが healthy 起動
- [ ] 注文フロー（サインイン → メニュー選択 → キッチン確認 → 配送選択 → 支払い確定）がエンドツーエンドで動作
- [ ] サポートチャットが動作し、注文チャットとのハンドオフが機能
- [ ] エージェント仕様ビューワーで全エージェントのケイパビリティ・プロンプト・スキルが閲覧可能
- [ ] 各サービスのURL解決が registry-service 経由（環境変数はサービス名のみ）
- [ ] スキルファイルにサービス名・エンドポイントパスが含まれない（ケイパビリティ記述のみ）

---

## フェーズ構成

---

### Phase 1: Foundation Cleanup

**目的:** AIの使い所を整理し、デモのメッセージを明確にする

#### 1-A: payment-agent 削除

**変更内容:**
- `PaymentApplicationService.prepare()` から `AgentFactory` / ツール呼び出しを削除
- `PaymentArachneConfiguration`（ツール Bean、決定論的 Model Bean）を削除
- `PaymentDeterministicModel` を削除
- 支払いプロファイル選択は `PaymentProfileRepository` を直接呼び出す
- 支払いサマリー文字列は `PaymentProfileRepository` が生成する（決定論的）

**完了基準:**
- [ ] `PaymentServiceApplication.java` に AgentFactory / Tool / Model のインポートが残っていない
- [ ] 支払いレスポンスの内容が変わらない（agent フィールドは "payment-service" のみ、"payment-agent" は削除）
- [ ] `PaymentServiceApiTest` が全テストパス
- [ ] compose 起動時に payment-service が正常動作

#### 1-B: delivery-agent 強化（動的発見 + リアルタイム API 呼び出し）

> **注:** このフェーズは Phase 2-A（registry-service）完了後に実装する。設計を先に確定し、Phase 2-A 完了後に実装に入る。

**確定アーキテクチャ:**

```
delivery-service (delivery-agent)
│
├── 内部ツール（サービス内完結）
│   ├── courier_availability_check  ← 自社スタッフの空き・ETA・料金
│   └── traffic_weather_check       ← 交通・天候状況
│
└── 外部ツール（動的・メタツール経由）
    ├── discover_eta_services        ← registry-agent に「外部ETA提供」ケイパビリティを持つ
    │                                   稼働中サービスを照会。NOT_AVAILABLE は除外済みで返る
    └── call_eta_service(url, items) ← 返された URL を実際に呼び出し ETA・混雑度・料金を取得
```

**実行フロー例:**
```
1. courier_availability_check  → 自社: スタッフ2名在席、ETA 18分、¥300
2. traffic_weather_check        → 交通: moderate、天候: clear、遅延 +3分
3. discover_eta_services        → [partner-express(URL, AVAILABLE),
                                    partner-economy(URL, AVAILABLE)]
                                   ※ partner-premium は NOT_AVAILABLE のため除外済み
4. call_eta_service(express)   → { eta: 22, congestion: "中", price: 350 }  ← リアルタイム取得
5. call_eta_service(economy)   → { eta: 35, congestion: "低", price: 180 }  ← リアルタイム取得
6. ランキング（文脈: 「急いでる」）
   1位: 自社エクスプレス 18分 ¥300 ← 推奨
   2位: パートナーエクスプレス 22分 ¥350
   3位: パートナーエコノミー 35分 ¥180
```

**シナリオ別動作:**

| 状況 | discover 結果 | delivery-agent の判断 |
|---|---|---|
| 自社在席 + 外部全部 AVAILABLE + 急ぎ | 外部2件返す | 自社最速を推奨、外部も選択肢として提示 |
| 自社スタッフ不在 + 全外部 AVAILABLE | 外部2件返す | 「自社は現在対応不可」、外部エクスプレス推奨 |
| 自社在席 + partner-express NOT_AVAILABLE | 外部1件返す | 自社 vs エコノミーのみ比較 |
| 大雨 + 自社不在 + partner-express NOT_AVAILABLE | 外部1件返す | 「悪天候・エクスプレス停止中、ETAに遅延あり」と警告付き推奨 |

**UIトレース表示例:**
```
delivery-agent:
  ✓ 自社スタッフ確認 → 在席2名、ETA 18分
  ✓ 交通・天候確認 → moderate、+3分遅延
  ✓ discovery-agent に外部ETA照会 → 2サービス発見（1件は停止中のため除外済み）
  ✓ partner-express API 呼び出し → ETA 22分、混雑:中
  ✓ partner-economy API 呼び出し → ETA 35分、混雑:低
  → 文脈（急ぎ）から自社エクスプレスを推奨
```

**変更内容:**
- `delivery-service` の `DeliveryApplicationService` に `discover_eta_services` + `call_eta_service` メタツールを追加
- delivery-agent のシステムプロンプト: 自社ツール → discover → 各外部 API 呼び出し → 文脈ベースランキング → 推奨提示 の責務を明記
- `DeliveryQuoteResponse` に `recommendedTier` + `recommendationReason` フィールドを追加
- 既存の `CourierAvailabilityRepository` / `TrafficWeatherRepository` は内部ツールとして継続使用

**完了基準:**
- [ ] delivery-agent が discover → 外部 API 呼び出し → ランキング の全ステップを実行する
- [ ] partner-express が NOT_AVAILABLE のとき discover の返却から除外される
- [ ] カスタマーメッセージに「急いで」を含むと自社エクスプレスが推奨される
- [ ] カスタマーメッセージに「安く」を含むとエコノミーが推奨される
- [ ] UIトレースに discover ステップと各外部 API 呼び出しが表示される
- [ ] `DeliveryServiceApiTest` が全テストパス

---

### Phase 2: 新規サービス追加

#### 2-A: registry-service (port 8087)

**目的:** AI ネイティブ サービスメッシュの基盤。全サービスのケイパビリティを管理し、自然言語クエリでサービスを発見する。

**提供するAPI:**
- `POST /registry/register` — サービス起動時にケイパビリティ・エンドポイント・エージェント仕様を登録
- `POST /registry/discover` — 自然言語ケイパビリティクエリ → マッチするサービス情報（URL含む）を返す
- `GET /registry/services` — 全登録サービス一覧（エージェント仕様ビューワー用）
- `GET /registry/health` — 全サービスのヘルス状態集約（ダッシュボード用）

**登録ペイロードの構造:**
```json
{
  "serviceName": "kitchen-service",
  "endpoint": "http://kitchen-service:8080",
  "capability": "在庫確認と調理時間推定。アイテムIDを受け取り、在庫状況・調理時間・代替品交渉を含む調理可否を返す",
  "agentName": "kitchen-agent",
  "systemPrompt": "...",
  "skills": [{ "name": "kitchen-availability", "content": "..." }]
}
```

**capability-registry-agent:**
- `capability_match` ツールを持つ：登録済みサービスのケイパビリティ説明を自然言語マッチングで照合し最適サービスを返す
- 稼働状態（`AVAILABLE` / `NOT_AVAILABLE`）をフィルタリング条件として適用する
- 結果はエンドポイントURL・サービス名・稼働状態・リクエスト形式を含む
- 複数マッチがある場合は全件返す（推奨判断は呼び出し元エージェントが行う）

**外部 ETA サービス（デモ専用モック）:**

delivery-agent の動的発見シナリオのため、以下3つをレジストリに登録する。

| サービス名 | ポート | 稼働状態 | 説明 |
|---|---|---|---|
| partner-express-service | 8088 | 周期的に NOT_AVAILABLE | 中距離高速配送パートナー。混雑時に停止 |
| partner-economy-service | 8089 | 常時 AVAILABLE | 低コスト配送パートナー。ETAは長め |
| partner-premium-service | なし | 常時 NOT_AVAILABLE | ダミー登録のみ・実エンドポイントなし |

- `partner-express-service` と `partner-economy-service` は軽量な Spring Boot モックとして実装（ETA・混雑度・料金をシミュレートして返すのみ）
- `partner-premium-service` はレジストリへの登録エントリのみ（起動しない）。NOT_AVAILABLE として登録することで「登録されているが使えないサービス」をデモできる
- 3サービスとも `food-delivery-demo` の compose に含める

**完了基準:**
- [ ] 全サービスが起動時に自身を登録する
- [ ] `POST /registry/discover` に「外部ETAを提供するサービスは？」を送ると partner-express と partner-economy が返る（partner-premium は除外）
- [ ] `POST /registry/discover` に「メニュー代替を扱うサービスは？」を送ると menu-service が返る
- [ ] `GET /registry/services` が全サービスのエージェント仕様と稼働状態を返す（partner-premium も NOT_AVAILABLE で表示）

#### 2-B: support-service (port 8086)

**目的:** 注文フロー以外の汎用カスタマーサポート。フィードバック収集・FAQ・稼働状況・キャンペーン情報を一元管理。

**提供するAPI:**
- `POST /api/support/chat` — サポートチャット（JWT認証）
- `POST /api/support/feedback` — 注文フィードバック送信（order-service から呼ぶ）
- `GET /api/support/campaigns` — 現在有効なキャンペーン一覧（ダッシュボード用）
- `GET /api/support/status` — サービス稼働状況（ダッシュボード用）

**support-agent の知識ドメインとツール:**
- `feedback_lookup` — 過去フィードバック検索（類似問題の参照）
- `faq_lookup` — FAQナレッジ検索
- `service_status_lookup` — 現在のサービス稼働状況
- `campaign_lookup` — 現在有効なキャンペーン・マーケティング施策
- `order_history_lookup` — 認証済みカスタマーの注文履歴（クレーム対応用）

**フィードバック収集フロー:**
- 注文確定後、order-service が support-service の `/api/support/feedback` を非同期呼び出し
- support-agent がフィードバックを分類（遅延/品質/誤配送/満足）しエスカレーションフラグを設定
- 蓄積されたフィードバックは `feedback_lookup` ツールで参照可能

**ハンドオフ設計（UIハンドオフ方式）:**
- order-agent の返答に `[HANDOFF: support]` ブロック → UI がサポートチャットへ誘導（文脈をURLパラメータで渡す）
- support-agent の返答に `[HANDOFF: order]` ブロック → UI が注文チャットへ誘導

**完了基準:**
- [ ] サポートチャットがキャンペーン・FAQ・稼働状況の質問に回答できる
- [ ] 注文確定後にフィードバックリクエストが support-service に届く
- [ ] order ↔ support ハンドオフがUI上で動作
- [ ] `SupportServiceApiTest` が全テストパス

---

### Phase 3: AI ネイティブ サービスメッシュ

**目的:** 「スキルを業務専門家が書ける」アーキテクチャへ。サービス名・エンドポイントをプロンプト/スキルから排除する。

#### 3-A: URL 解決を registry-service 経由に変更

**変更内容:**
- 各サービスの環境変数からエンドポイントURLを削除。サービス名のみに変更
  - 例: `MENU_SERVICE_BASE_URL=http://menu-service:8080` → `MENU_SERVICE_NAME=menu-service`
- 各サービス起動時に registry-service にエンドポイントを登録し、他サービス呼び出し時は registry から URL を解決してキャッシュ
- compose.yml の環境変数を整理

#### 3-B: メタツール委譲パターンへの移行

**設計:**

`delegate_to_capability(capabilityDescription, payload)` メタツール：
1. capability-registry-agent に `capabilityDescription` を問い合わせ、サービスURLを取得
2. 取得したURLにペイロードをPOST
3. レスポンスを返す

**変更対象:**
- `order-service`: `menuTool`, `kitchenTool`, `deliveryTool` をメタツールに置き換え
  - システムプロンプトから「suggest_menu を呼べ」→「メニュー提案ケイパビリティを持つサービスに委ねろ」へ変更
- `kitchen-service`: `menu_substitution_lookup` ツールをメタツールに置き換え
  - スキルから「menu-agent に問い合わせろ」→「同ブランドのメニュー代替ケイパビリティを持つサービスを探せ」へ変更
- 全スキルファイルから具体的なサービス名・エンドポイントを除去

**注:** `payment_profile_lookup`（payment-service 内部）と `recent_order_lookup`（order-service 内部）はサービス内完結のためメタツール化不要。

**完了基準:**
- [ ] 全スキルファイルにサービス名・エンドポイントパスが含まれない
- [ ] order-service の `RestClient` Bean がハードコードURLを持たない
- [ ] 注文フローが registry-service 経由のURL解決で正常動作
- [ ] registry-service なしでは動かないことが compose の depends_on で表現されている

---

### Phase 4: UI 追加・変更

#### 4-A: ダッシュボード拡張

**追加内容:**
- サポートチャットウィジェット（ダッシュボード右側 or 専用タブ）
- support-service から取得するキャンペーン情報バナー
- support-service から取得するサービス稼働状況インジケーター

#### 4-B: エージェント仕様ビューワーページ

**URL:** `/agents`

**表示内容（registry-service の `GET /registry/services` から取得）:**
- 登録済み全サービスのカード一覧
- 各カード: サービス名・エージェント名・ケイパビリティ説明
- カードをクリック → システムプロンプト・スキル一覧をモーダルで表示
- ヘルス状態のバッジ（registry-service の `GET /registry/health` から）

**完了基準:**
- [ ] `/agents` ページが全登録サービスを表示
- [ ] プロンプト・スキル閲覧が動作
- [ ] ダッシュボードにキャンペーンと稼働状況が表示
- [ ] サポートチャットウィジェットが動作
- [ ] Next.js 全ページがビルドクリーン

---

## サービスポートマップ（最終形）

| サービス | ポート | 変更 |
|---|---|---|
| customer-ui | 3000 | UI追加 |
| customer-service | 8085 | 変更なし |
| order-service | 8080 | Phase 3 メタツール化 |
| menu-service | 8081 | Phase 3 スキル更新 |
| kitchen-service | 8082 | Phase 3 スキル更新 |
| delivery-service | 8083 | Phase 1-B 強化（動的発見 + 外部 API 呼び出し） |
| payment-service | 8084 | Phase 1-A agent削除 |
| support-service | 8086 | Phase 2-B 新規 |
| registry-service | 8087 | Phase 2-A 新規 |
| partner-express-service | 8088 | Phase 2-A 新規（デモ用モック、周期的 NOT_AVAILABLE） |
| partner-economy-service | 8089 | Phase 2-A 新規（デモ用モック、常時 AVAILABLE） |
| partner-premium-service | なし | Phase 2-A 新規（レジストリ登録のみ・常時 NOT_AVAILABLE） |

---

## 実装順序

Phase 1-B は registry-service（Phase 2-A）に依存するため、順序を以下に変更する：

```
1-A → 2-A → 1-B → 2-B → 3-A → 3-B → 4-A → 4-B
```

| # | フェーズ | 依存 | 理由 |
|---|---|---|---|
| 1 | Phase 1-A: payment-agent 削除 | なし | 単体閉結・最小スコープ |
| 2 | Phase 2-A: registry-service + 外部 ETA モック追加 | なし | 後続全フェーズの基盤 |
| 3 | Phase 1-B: delivery-agent 強化 | 2-A | discover/call メタツールが registry に依存 |
| 4 | Phase 2-B: support-service 追加 | 2-A | registry への登録が必要 |
| 5 | Phase 3-A: URL解決 registry 経由 | 2-A | registry が稼働している前提 |
| 6 | Phase 3-B: メタツール委譲 | 3-A | URL解決基盤が必要 |
| 7 | Phase 4-A: ダッシュボード拡張 | 2-B | support-service API が必要 |
| 8 | Phase 4-B: エージェント仕様ビューワー | 2-A | registry API が必要 |

## 進捗状況

| フェーズ | ステータス |
|---|---|
| Phase 1-A: payment-agent 削除 | 🔲 未着手 |
| Phase 2-A: registry-service + 外部 ETA モック追加 | 🔲 未着手 |
| Phase 1-B: delivery-agent 強化（動的発見 + 外部 API 呼び出し） | 🔲 未着手 |
| Phase 2-B: support-service 追加 | 🔲 未着手 |
| Phase 3-A: URL解決 registry 経由 | 🔲 未着手 |
| Phase 3-B: メタツール委譲 | 🔲 未着手 |
| Phase 4-A: ダッシュボード拡張 | 🔲 未着手 |
| Phase 4-B: エージェント仕様ビューワー | 🔲 未着手 |

## Next Action

**Phase 1-A（payment-agent 削除）から着手。**

- 変更スコープが payment-service 単体に閉じている
- 既存テストですぐに検証できる
- デモのメッセージ（「AIを使う場所を選んでいる」）を最初に確立できる
- registry-service に依存しないため今すぐ実装可能
