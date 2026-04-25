# Food Delivery Demo 改修プラン

## ゴール

Arachne が Spring Boot マイクロサービスに自然に溶け込めることを示す、より完成度の高い実用的なデモへ。以下の3つの柱で構成する：

1. **AIを使う場所・使わない場所の明確化** — 正当化できないエージェントを削除し、AIが本当に価値を出す場面を際立たせる
2. **AI ネイティブ サービスメッシュ** — サービス発見と連携をAIエージェントが担い、業務専門家がスキルを書けるアーキテクチャへ
3. **デモの横幅拡張** — 注文フローをワークフローUIに作り直してAIの提案根拠を明示し、サポートチャット・エージェント仕様ビューワーを追加することで、Arachne の多様な活用パターン（意思決定支援型 vs 会話型）を並べて見せる

---

## 全体完了基準

- [ ] `mvn test` (food-delivery-demo) が全テストパス
- [ ] `make up` で全サービスが healthy 起動
- [ ] 注文ワークフロー（初期入力 → アイテム選択 → 配送選択 → 支払い承認）がエンドツーエンドで動作し、各ステップでAIの提案根拠が画面に表示される
- [ ] 注文ページのトレースパネルが削除されている（ワークフロー自体が提案・根拠・選択を表示するため不要）
- [ ] サポートチャットが動作し、注文ワークフローとのハンドオフが機能
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
- [x] `PaymentServiceApplication.java` に AgentFactory / Tool / Model のインポートが残っていない
- [x] 支払いレスポンスの内容が変わらない（agent フィールドは "payment-service" のみ、"payment-agent" は削除）
- [x] `PaymentServiceApiTest` が全テストパス
- [x] compose 起動時に payment-service が正常動作

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
3. discover_eta_services        → [Hermes-adapter(URL, AVAILABLE),
                                    Idaten-adapter(URL, AVAILABLE)]
                                   ※ Icarus-adapter は NOT_AVAILABLE のため除外済み
4. call_eta_service(hermes)    → { eta: 22, congestion: "中", price: 350 }  ← リアルタイム取得
5. call_eta_service(idaten)    → { eta: 35, congestion: "低", price: 180 }  ← リアルタイム取得
6. ランキング（文脈: 「急いでる」）
   1位: 自社エクスプレス 18分 ¥300 ← 推奨
   2位: Hermes 22分 ¥350
   3位: Idaten 35分 ¥180
```

**シナリオ別動作:**

| 状況 | discover 結果 | delivery-agent の判断 |
|---|---|---|
| 自社在席 + 外部全部 AVAILABLE + 急ぎ | 外部2件返す | 自社最速を推奨、外部も選択肢として提示 |
| 自社スタッフ不在 + 全外部 AVAILABLE | 外部2件返す | 「自社は現在対応不可」、外部エクスプレス推奨 |
| 自社在席 + Hermes NOT_AVAILABLE | 外部1件返す | 自社 vs Idaten のみ比較 |
| 大雨 + 自社不在 + Hermes NOT_AVAILABLE | 外部1件返す | 「悪天候・Hermes 停止中、ETAに遅延あり」と警告付き推奨 |

**UIトレース表示例:**
```
delivery-agent:
  ✓ 自社スタッフ確認 → 在席2名、ETA 18分
  ✓ 交通・天候確認 → moderate、+3分遅延
  ✓ discovery-agent に外部ETA照会 → 2アダプター発見（Icarus は停止中のため除外済み）
  ✓ Hermes-adapter API 呼び出し → ETA 22分、混雑:中
  ✓ Idaten-adapter API 呼び出し → ETA 35分、混雑:低
  → 文脈（急ぎ）から自社エクスプレスを推奨
```

**変更内容:**
- `delivery-service` の `DeliveryApplicationService` に `discover_eta_services` + `call_eta_service` メタツールを追加
- delivery-agent のシステムプロンプト: 自社ツール → discover → 各外部 API 呼び出し → 文脈ベースランキング → 推奨提示 の責務を明記
- `DeliveryQuoteResponse` に `recommendedTier` + `recommendationReason` フィールドを追加
- 既存の `CourierAvailabilityRepository` / `TrafficWeatherRepository` は内部ツールとして継続使用

**完了基準:**
- [ ] delivery-agent が discover → 外部 API 呼び出し → ランキング の全ステップを実行する
- [ ] Hermes-adapter が NOT_AVAILABLE のとき discover の返却から除外される
- [ ] カスタマーメッセージに「急いで」を含むと自社エクスプレスが推奨される
- [ ] カスタマーメッセージに「安く」を含むとエコノミーが推奨される
- [ ] UIトレースに discover ステップと各外部 API 呼び出しが表示される
- [ ] `DeliveryServiceApiTest` が全テストパス

---

#### 1-C: menu-agent 強化 + order→kitchen 直呼びパス廃止

**目的:** menu-agent が「候補選択・代替採択・推薦理由の生成」という多次元判断を担う真のエージェントとして機能するよう再設計する。計算の正確さが求められる部分（合計金額・ETA）はJavaツールに委ねる。あわせて order-agent から kitchen-service への直呼びパスを廃止し、menu-service を注文可能性（メニュー提案 + キッチン確認）の唯一の入口とする。

**設計方針:**

- **AIがやること（判断・選択）**: 全メニュー候補の中からお客様の意図（人数・予算・好み・履歴コンテキスト）を照らし合わせて推薦セットを選ぶ。在庫切れ時は代替候補リストの中から意図に最も合う1件を採択する。これらはJavaルールで代替できない多次元の自然言語理解が本質的に必要な判断である
- **Javaツールがやること（計算・取得）**: 全メニューカタログの提供、合計金額の計算・検証、在庫・ETA の取得。正確さが求められる部分はすべてJavaツールとして提供し、AIが呼び出す
- **ファサードパターン**: order-agent は `suggest_menu_and_check` を1回呼ぶだけ。menu-service が内部で kitchen-service を呼び出して在庫・ETA を確認した上で応答する

**アーキテクチャ:**

```
order-agent
  → suggest_menu_and_check (menu-service/menu-agent)   ← 唯一の呼び出し先
      ├─ catalog_lookup()                  ← Javaツール: 全メニューアイテム一覧を返す
      ├─ [AI] 候補選択                      ← 意図・予算・人数・履歴から推薦セットを決める
      ├─ [内部 REST] kitchen_check (kitchen-service/kitchen-agent)
      │     ├─ inventory_lookup            ← 在庫・調理時間
      │     └─ prep_scheduler             ← 並行ライン ETA 計算・混雑時代替提案
      ├─ [AI] 代替採択（欠品時）            ← 候補リストから意図に合う1件を選ぶ
      └─ calculate_total(itemIds, qtys)    ← Javaツール: 合計金額を計算・検証

order-agent の kitchenTool を廃止（エージェント間協調は menu↔kitchen に集約）
```

**タグ体系（`MenuItem` に `category` + `tags` フィールドを追加）:**

`category` フィールドを `tags` とは別に持つ（代替候補リスト生成の「同カテゴリ優先」フィルタに使用）。タグは代替候補の絞り込みと、AIの選択理由の根拠として使う。

| category | id | tags |
|---|---|---|
| combo | combo-crispy | chicken, fry, popular |
| combo | combo-smash | burger, grill, popular |
| combo | combo-kids | kids, mild, small-portion |
| combo | combo-teriyaki | chicken, grill, japanese |
| combo | combo-spicy-tuna | fish, spicy, japanese |
| side | side-fries | vegetarian, fry |
| side | side-nuggets | chicken, fry, kids |
| side | side-onion-rings | vegetarian, fry |
| drink | drink-lemon | cold, light |
| drink | drink-latte | cold, coffee |
| drink | drink-matcha-latte | hot, matcha, japanese |
| wrap | wrap-garden | vegetarian, healthy, light |
| bowl | bowl-salmon | fish, healthy, japanese |
| bowl | bowl-veggie | vegetarian, healthy, light |
| dessert | dessert-choco | sweet, warm |
| dessert | dessert-matcha | sweet, matcha, japanese |

**代替候補リスト生成（Javaが行う・menu-agentに渡す）:**

欠品発生時、Javaが以下のルールで候補リストを絞り込んでから menu-agent に渡す。AIは渡された候補の中からお客様の意図に合う1件を選ぶ。

1. 同 `category` 内の在庫ありアイテムを候補とする
2. 同カテゴリに在庫なしの場合のみ全カテゴリに拡張
3. 候補リストは最大3件

**`prep_scheduler` ツール（kitchen-agent に追加）:**

- `KitchenRepository` に各アイテムの調理ライン種別（`grill` / `fry` / `assembly` / `drink`）を追加
- キュー負荷はin-memoryカウンタで管理（注文確定時 +1、一定時間後 -1）。時刻固定ではなく実際の注文量に連動
- 異なるライン種別は独立並行可能。ETA = 同グループ内最大調理時間 + キュー遅延
- **kitchen-agent の能動的提案**: キュー遅延が閾値（15分）を超えた場合、kitchen-agent は `prep_scheduler` 結果を受けて「現在 grill-line が混雑中です。assembly 系（サーモン丼など）であれば今すぐ XX分で提供できます」と代替ラインの選択肢を menu-agent に返す。menu-agent はこの情報をお客様への提案に組み込む

**リピーター対応（order-agent が context を渡す）:**

- order-agent が `recent_order_lookup` を呼び、履歴アイテムを `suggest_menu_and_check` の入力コンテキストとして渡す
- menu-agent はオーダー履歴を自分では参照しない（責務分離）

**変更内容:**

- `MenuItem` record: `category` + `List<String> tags` フィールド追加
- `MenuRepository`: 全16アイテムにカテゴリ・タグ付与、`findAll()`・`findSubstituteCandidates(unavailableId)` メソッド追加
- `MenuArachneConfiguration`: menu-agent のシステムプロンプトを「catalog_lookup で全候補を取得し、意図に合う推薦セットを選ぶ。欠品時は代替候補から意図に合う1件を採択する」に変更。`catalog_lookup_tool`・`calculate_total_tool` を追加
- `MenuServiceApplication`: kitchen-service への `RestClient` Bean 追加、`suggest` が内部で kitchen_check を実行
- `MenuSuggestionResponse`: `etaMinutes`・`kitchenTrace` フィールド追加
- `KitchenRepository`: 調理ライン種別マップ追加、in-memory キューカウンタ追加
- `KitchenArachneConfiguration`: `prep_scheduler_tool` 追加、kitchen-agent システムプロンプトに混雑時代替ライン提案の責務を追加
- `order-service` の `buildKitchenTool` を削除、`kitchenTool` の参照をシステムプロンプトから除去
- `order-service`: チャット形式の `/api/order/chat` を廃止し、ステップ別APIに分割
  - `POST /api/order/suggest` — 初期リクエスト + オプションの `refinement` フィールド受付。menu-agent に提案依頼し、構造化提案リスト（`itemId`, `name`, `quantity`, `unitPrice`, `reason`）を返す。`refinement` があれば前回提案を踏まえて選び直す
  - `POST /api/order/confirm-items` — アイテム選択確定。delivery-agent に配送選択肢依頼し、構造化選択肢（`tier`, `eta`, `price`, `reason`, `recommended`）を返す
  - `POST /api/order/confirm-delivery` — 配送選択確定。支払いサマリー（`items`, `deliveryFee`, `total`, `paymentMethod`）を返す
  - `POST /api/order/confirm-payment` — 注文確定
- `OrderSession`: ワークフローステップ（`initial` / `item-selection` / `delivery-selection` / `payment` / `completed`）をセッションに追加
- customer-ui の order ページのトレースパネルコンポーネントを削除（`ServiceTraceCard`、ルーティングチップ等）

**デモシナリオ:**

| シナリオ | お客様発話 | menu-agentがやること（AI） | Javaツールがやること |
|---|---|---|---|
| A: 単純注文 | 「照り焼きください」 | catalog_lookup → combo-teriyaki を選択・推薦理由を生成 | kitchen_check → ETA 14分、calculate_total → ¥920 |
| B: ファミリー注文 | 「4人で¥4000以内、子ども1人います」 | catalog_lookup → 人数・予算・kids制約から最適セットを選択 | kitchen_check → ETA、calculate_total → 合計金額検証 |
| C: 欠品代替 | 「クリスピーチキンお願いします」 | 代替候補[combo-teriyaki, combo-smash]から「チキン系」のcombo-teriyakiを採択・理由を生成 | kitchen_check → 欠品検出 + 同カテゴリ候補リスト生成 |
| D: リピーター | 「いつものやつで」 | 履歴コンテキストからアイテムを特定・「前回と同じ」確認文を生成 | kitchen_check → 在庫確認、calculate_total → 合計金額 |
| E: ランチ混雑 | 「照り焼きとナゲット」（12時） | kitchen-agentからの混雑代替提案を受けて「grill 混雑中・assembly 系も提案」をお客様に伝える | kitchen_check → prep_scheduler（キューカウンタ高）→ 代替ライン提案を含む応答 |

**完了基準:**
- [ ] order-agent が `check_kitchen` ツールを持たない
- [ ] order-agent の `suggest_menu_and_check` 1呼び出しで提案アイテム・在庫状態・ETA がすべて返る
- [ ] 「4人で¥4000以内、子ども1人います」でAIが推薦セットを選び、合計金額がJavaツールで検証される
- [ ] 欠品時にAIが代替候補リストからお客様の意図に合う1件を採択する
- [ ] ランチタイムに複数注文が重なると kitchen-agent が混雑代替ラインを提案する
- [ ] `MenuServiceApiTest` と `KitchenServiceApiTest` が全テストパス

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
| hermes-adapter | 8088 | 周期的に NOT_AVAILABLE | 高速配送パートナー Hermes 社のアダプター。混雑時に停止 |
| idaten-adapter | 8089 | 常時 AVAILABLE | 低コスト配送パートナー Idaten 社のアダプター。ETAは長め |
| icarus-adapter | なし | 常時 NOT_AVAILABLE | 架空プレミアムパートナー Icarus 社。登録のみ・実エンドポイントなし |

- `hermes-adapter` と `idaten-adapter` は軽量な Spring Boot モックとして実装（各パートナー社の API をラップし、ETA・混雑度・料金を自社統一形式で返す）
- `icarus-adapter` はレジストリへの登録エントリのみ（起動しない）。NOT_AVAILABLE として登録することで「野心はあるが飛べないサービス」をデモできる
- 3アダプターとも `food-delivery-demo` の compose に含める

**完了基準:**
- [ ] 全サービスが起動時に自身を登録する
- [ ] `POST /registry/discover` に「外部ETAを提供するサービスは？」を送ると hermes-adapter と idaten-adapter が返る（icarus-adapter は除外）
- [ ] `POST /registry/discover` に「メニュー代替を扱うサービスは？」を送ると menu-service が返る
- [ ] `GET /registry/services` が全サービスのエージェント仕様と稼働状態を返す（icarus-adapter も NOT_AVAILABLE で表示）

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

#### 4-C: 注文ワークフローUI（order/page.tsx 全面作り替え）

**目的:** 注文フローをチャット形式からステップ型ワークフローに作り直す。各ステップでAIの提案と根拠を構造化表示し、ユーザーの選択を明示する。support-chat との対比で「意思決定支援型AI」と「会話型AI」の2つのパターンを並べて見せる。

**ステップ設計:**

```
[1] 初期入力
    └─ テキスト入力欄 + 送信ボタン
       「何名様ですか？予算は？」等の入力ガイド

[2] アイテム選択
    └─ menu-agent の提案カードを複数表示
       各カード: アイテム名・価格・数量・[推薦理由] バッジ
       → 選択 or「フィードバックして再提案」入力欄
       再提案: POST /api/order/suggest に refinement を付けて再送

[3] 配送選択
    └─ delivery-agent の選択肢カードを表示
       各カード: 配送方法・ETA・料金・[推薦理由] バッジ・[推奨] マーク
       → 1件を選択して次へ

[4] 支払い承認
    └─ 注文サマリー（アイテム一覧・配送費・合計）
       支払い方法表示
       → 「注文確定」ボタン
```

**削除するコンポーネント:**
- `ServiceTraceCard`（サービストレースカード）
- ルーティング判断チップ（`RoutingDecision` 表示）
- `traceMemory` パネル
- チャット入力欄・メッセージバブル（order ページのみ。support ページは維持）

**セッション管理:**
- ステップ状態 (`step`) は order-service のセッションに保持
- 各ステップのレスポンスをフロントエンドの state に保持し、前ステップへの戻りはセッションリセットで対応

**完了基準:**
- [ ] 4ステップのワークフローがエンドツーエンドで動作する
- [ ] アイテム提案カードに推薦理由が表示される
- [ ] 配送選択肢カードに推薦理由・推奨マークが表示される
- [ ] フィードバック入力 → 再提案が動作する
- [ ] order ページにトレースパネルが残っていない
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
| hermes-adapter | 8088 | Phase 2-A 新規（Hermes 社アダプター、周期的 NOT_AVAILABLE） |
| idaten-adapter | 8089 | Phase 2-A 新規（Idaten 社アダプター、常時 AVAILABLE） |
| icarus-adapter | なし | Phase 2-A 新規（Icarus 社・レジストリ登録のみ・常時 NOT_AVAILABLE） |

---

## 実装順序

Phase 1-B は registry-service（Phase 2-A）に依存するため、また Phase 1-C はどのフェーズにも依存しないため、順序を以下に変更する：

```
1-A → 1-C → 2-A → 1-B → 2-B → 3-A → 3-B → 4-A → 4-B → 4-C
```

| # | フェーズ | 依存 | 理由 |
|---|---|---|---|
| 1 | Phase 1-A: payment-agent 削除 | なし | 単体閉結・最小スコープ |
| 2 | Phase 1-C: menu-agent 強化 + order→kitchen パス廃止 | なし | order/menu/kitchen の3サービスに閉じる |
| 3 | Phase 2-A: registry-service + 外部 ETA モック追加 | なし | 後続全フェーズの基盤 |
| 4 | Phase 1-B: delivery-agent 強化 | 2-A | discover/call メタツールが registry に依存 |
| 5 | Phase 2-B: support-service 追加 | 2-A | registry への登録が必要 |
| 6 | Phase 3-A: URL解決 registry 経由 | 2-A | registry が稼働している前提 |
| 7 | Phase 3-B: メタツール委譲 | 3-A | URL解決基盤が必要 |
| 8 | Phase 4-A: ダッシュボード拡張 | 2-B | support-service API が必要 |
| 9 | Phase 4-B: エージェント仕様ビューワー | 2-A | registry API が必要 |
| 10 | Phase 4-C: 注文ワークフローUI | 1-C | ステップ別API（1-C で追加）が必要 |

## 進捗状況

| フェーズ | ステータス |
|---|---|
| Phase 1-A: payment-agent 削除 | ✅ 完了（commit 7c40053） |
| Phase 1-C: menu-agent 強化 + order→kitchen パス廃止 | 🔲 未着手 |
| Phase 2-A: registry-service + 外部 ETA モック追加 | 🔲 未着手 |
| Phase 1-B: delivery-agent 強化（動的発見 + 外部 API 呼び出し） | 🔲 未着手 |
| Phase 2-B: support-service 追加 | 🔲 未着手 |
| Phase 3-A: URL解決 registry 経由 | 🔲 未着手 |
| Phase 3-B: メタツール委譲 | 🔲 未着手 |
| Phase 4-A: ダッシュボード拡張 | 🔲 未着手 |
| Phase 4-B: エージェント仕様ビューワー | 🔲 未着手 |
| Phase 4-C: 注文ワークフローUI | 🔲 未着手 |

## Next Action

**Phase 1-C（menu-agent 強化 + order→kitchen パス廃止）を着手。**

実装サブステップ（この順に進める）:
1. `MenuItem` に `category` + `List<String> tags` フィールド追加、全16アイテムに付与
2. `MenuRepository.findSubstituteCandidates(unavailableId)` 追加（同カテゴリ優先 → タグ重複ランキング → 最大3件）
3. `MenuArachneConfiguration` に `catalog_lookup_tool` + `calculate_total_tool` 追加、menu-agent システムプロンプト書き換え
4. `KitchenRepository` に調理ライン種別マップ + in-memory キューカウンタ追加、`prep_scheduler_tool` 追加、kitchen-agent システムプロンプト更新
5. `MenuServiceApplication` に `suggest_menu_and_check` ファサード追加（kitchen-service への内部 REST 呼び出し）
6. `order-service` から `buildKitchenTool` を削除、システムプロンプトの `check_kitchen` 参照を除去
7. `/api/order/chat` を4ステップエンドポイントに分割、`OrderSession` にワークフローステップ追加
8. 全3サービスのテストを更新・追加し `mvn -f food-delivery-demo/pom.xml test` 確認
