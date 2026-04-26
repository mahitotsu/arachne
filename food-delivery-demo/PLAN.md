# Food Delivery Demo 改修プラン

## ゴール

Arachne が Spring Boot マイクロサービスに自然に溶け込めることを示す、より完成度の高い実用的なデモへ。以下の3つの柱で構成する：

1. **AIを使う場所・使わない場所の明確化** — 正当化できないエージェントを削除し、AIが本当に価値を出す場面を際立たせる
2. **AI ネイティブ サービスメッシュ** — サービス発見と連携をAIエージェントが担い、業務専門家がスキルを書けるアーキテクチャへ
3. **デモの横幅拡張** — 注文フローをワークフローUIに作り直してAIの提案根拠を明示し、サポートチャット・エージェント仕様ビューワーを追加することで、Arachne の多様な活用パターン（意思決定支援型 vs 会話型）を並べて見せる

---

## 最終完了基準

> **注:** ここは各 Phase を通し切った後の最終到達条件。Phase 1-C 完了条件ではないため、未チェック項目が残っていても直ちに矛盾ではない。ただし、どの Phase で満たす項目かは各行に明記する。

- [x] `mvn test` (food-delivery-demo) が全テストパス
- [ ] `make up` で全サービスが healthy 起動（最終確認: Phase 2-A, 2-B, 4-C 完了後）
- [ ] 注文ワークフロー（初期入力 → アイテム選択 → 配送選択 → 支払い承認）がエンドツーエンドで動作し、各ステップでAIの提案根拠が画面に表示される（対応: Phase 4-C）
- [ ] 注文ページのトレースパネルが削除されている（ワークフロー自体が提案・根拠・選択を表示するため不要）（対応: Phase 4-C）
- [ ] サポートチャットが動作し、注文ワークフローとのハンドオフが機能（対応: Phase 2-B, 4-A）
- [ ] エージェント仕様ビューワーで全エージェントのケイパビリティ・プロンプト・スキルが閲覧可能（対応: Phase 4-B）
- [ ] 各サービスのURL解決が registry-service 経由（環境変数はサービス名のみ）（対応: Phase 3-A）
- [ ] スキルファイルにサービス名・エンドポイントパスが含まれない（ケイパビリティ記述のみ）（対応: Phase 3-B）

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

> **実装メモ (2026-04-26):** Phase 2-A 完了後に着手し、delivery-service 内で discover/call メタツール、文脈別 ranking、`recommendedTier` / `recommendationReason` 契約、order-service への推奨伝播まで実装済み。

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
- [x] delivery-agent が discover → 外部 API 呼び出し → ランキング の全ステップを実行する
- [x] Hermes-adapter が NOT_AVAILABLE のとき discover の返却から除外される
- [x] カスタマーメッセージに「急いで」を含むと自社エクスプレスが推奨される
- [x] カスタマーメッセージに「安く」を含むとエコノミーが推奨される
- [x] UIトレースに discover ステップと各外部 API 呼び出しが表示される
- [x] `DeliveryServiceApiTest` が全テストパス

**進捗メモ（2026-04-26）:**
- `DeliveryApplicationService` に `discover_eta_services` / `call_eta_service` を追加し、registry discover で返った AVAILABLE な外部 ETA 候補だけを実呼び出しするようにした
- 配送候補は自社エクスプレス、Hermes、Idaten を同一レスポンスへ統合し、`recommendedTier` / `recommendationReason` を追加した
- deterministic model のトレース文に discover と各外部 API 呼び出しを含め、`急いで` と `安く` の両文脈を回帰テスト化した
- order-service は delivery-service の `recommendedTier` を尊重して recommended option を表示するように更新した

---

#### 1-C: menu-agent 強化 + order→kitchen 直呼びパス廃止

**目的:** order-service から kitchen-service への直呼びパスを廃止し、menu-service を注文可能性（メニュー提案 + キッチン確認）の唯一の入口にする。現段階では、軽量な回帰テストを維持できるように、候補選択・代替採択・数量調整・合計検証の主要判断は Java の決定的ロジックで固定し、menu-agent は tool オーケストレーションと説明生成の境界を担う。

**現段階の実装前提:** Phase 1-C の完了判定は Bedrock 接続ではなく deterministic モードで行う。これは「実 LLM の推論品質」を測るテストではなく、「menu-service の shipped contract が壊れていないこと」を素早く回帰確認するための方式である。Bedrock を使った実モデル確認は opt-in の統合検証として別扱いにする。

**設計方針:**

- **Javaがやること（現在の shipped 判断・計算・取得）**: 全メニューカタログの提供、人数・予算・kids 制約に基づく候補選択、欠品時の代替採択、数量調整、合計金額の計算・検証、在庫・ETA の取得。軽量で反復可能な回帰テストを優先し、壊れやすい判断はまず Java の決定的ロジックに寄せる
- **menu-agent / deterministic model がやること（現在の shipped オーケストレーション・説明）**: スキル有効化、`catalog_lookup_tool` と `calculate_total_tool` の呼び出し順の固定、推薦サマリーの生成。プロンプトと tool 境界は将来の Bedrock 接続時にも流用できる形で保つ
- **ファサードパターン**: order-service は `suggest_menu_and_check` を1回呼ぶだけ。menu-service が内部で kitchen-service を呼び出して在庫・ETA を確認した上で応答する

**アーキテクチャ:**

```
order-service workflow
  → suggest_menu_and_check (menu-service/menu-agent)   ← 唯一の呼び出し先
      ├─ catalog_lookup()                  ← Javaツール: 全メニューアイテム一覧を返す
      ├─ [Java rule] 候補選択                ← 意図・予算・人数・履歴から推薦セットを決める
      ├─ [内部 REST] kitchen_check (kitchen-service/kitchen-agent)
      │     ├─ inventory_lookup            ← 在庫・調理時間
      │     └─ prep_scheduler             ← 並行ライン ETA 計算・混雑時代替提案
      ├─ [Java rule] 代替採択（欠品時）      ← 候補リストから意図に合う1件を選ぶ
      ├─ [deterministic agent] サマリー生成  ← tool 呼び出しと要約文を整形する
      └─ calculate_total(itemIds, qtys)    ← Javaツール: 合計金額を計算・検証

order-service から kitchen-service 直呼びを廃止（エージェント間協調は menu↔kitchen に集約）
```

**タグ体系（`MenuItem` に `category` + `tags` フィールドを追加）:**

`category` フィールドを `tags` とは別に持つ（代替候補リスト生成の「同カテゴリ優先」フィルタに使用）。タグは代替候補の絞り込みと、Java の選択/説明ロジックの根拠として使う。

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

欠品発生時、Javaが以下のルールで候補リストを絞り込み、現段階ではそのまま Java ルールでお客様の意図に合う1件を採択する。将来 Bedrock に寄せる場合も、この候補生成ルール自体は deterministic に維持する。

1. 同 `category` 内の在庫ありアイテムを候補とする
2. 同カテゴリに在庫なしの場合のみ全カテゴリに拡張
3. 候補リストは最大3件

**`prep_scheduler` ツール（kitchen-agent に追加）:**

- `KitchenRepository` に各アイテムの調理ライン種別（`grill` / `fry` / `assembly` / `drink`）を追加
- キュー負荷はin-memoryカウンタで管理（注文確定時 +1、一定時間後 -1）。時刻固定ではなく実際の注文量に連動
- 異なるライン種別は独立並行可能。ETA = 同グループ内最大調理時間 + キュー遅延
- **kitchen-agent の能動的提案**: キュー遅延が閾値（15分）を超えた場合、kitchen-agent は `prep_scheduler` 結果を受けて「現在 grill-line が混雑中です。assembly 系（サーモン丼など）であれば今すぐ XX分で提供できます」と代替ラインの選択肢を menu-agent に返す。menu-agent はこの情報をお客様への提案に組み込む

**リピーター対応（order-service が context を渡す）:**

- order-service が直近注文履歴を読み取り、履歴アイテムを `suggest_menu_and_check` の入力コンテキストとして渡す
- menu-agent はオーダー履歴を自分では参照しない（責務分離）

**変更内容:**

- `MenuItem` record: `category` + `List<String> tags` フィールド追加
- `MenuRepository`: 全16アイテムにカテゴリ・タグ付与、`findAll()`・`findSubstituteCandidates(unavailableId)` メソッド追加
- `MenuArachneConfiguration`: menu-agent のシステムプロンプトを「catalog_lookup で候補を確認し、deterministic な候補選定結果を説明できるようにする。提案後は calculate_total で検算する」に変更。`catalog_lookup_tool`・`calculate_total_tool` を追加
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
- customer-ui の order ページのトレースパネルコンポーネント削除は **Phase 4-C に据え置く**。Phase 1-C では order-service のステップ別API整備までを対象にし、UI 既存トレースは維持する

**デモシナリオ:**

| シナリオ | お客様発話 | menu-agent / deterministic model がやること | Java がやること |
|---|---|---|---|
| A: 単純注文 | 「照り焼きください」 | `catalog_lookup_tool` を通した候補確認と推薦理由の整形 | `combo-teriyaki` を選択、`kitchen_check` → ETA 14分、`calculate_total` → ¥920 |
| B: ファミリー注文 | 「4人で¥4000以内、子ども1人います」 | family skill 有効化、候補確認、推薦サマリー整形 | 人数・予算・kids 制約から予算内セットを選択、`kitchen_check` → ETA、`calculate_total` → 合計金額検証 |
| C: 欠品代替 | 「クリスピーチキンお願いします」 | 代替採択結果の説明生成 | `kitchen_check` → 欠品検出 + 同カテゴリ候補リスト生成、チキン系の `combo-teriyaki` を採択 |
| D: リピーター | 「いつものやつで」 | 履歴コンテキスト入り要約文を生成 | 履歴コンテキストを入力に反映、`kitchen_check` → 在庫確認、`calculate_total` → 合計金額 |
| E: ランチ混雑 | 「照り焼きとナゲット」（12時） | kitchen-agent から受けた混雑代替提案を要約して返す | `kitchen_check` → `prep_scheduler`（キューカウンタ高）→ 代替ライン提案を含む応答 |

**完了基準:**
- [x] order-service から kitchen-service への直呼びがない
- [x] order-service の `suggest_menu_and_check` 1呼び出しで提案アイテム・在庫状態・ETA がすべて返る
- [x] 「4人で¥4000以内、子ども1人います」で Java の決定的ロジックが推薦セットを選び、合計金額が Java ツールで検証される
- [x] 欠品時に Java の決定的ロジックが代替候補リストからお客様の意図に合う1件を採択する
- [x] ランチタイムに複数注文が重なると kitchen-agent が混雑代替ラインを提案する
- [x] `MenuServiceApiTest` と `KitchenServiceApiTest` が全テストパス
- [x] `OrderServiceApiTest` と `OrderApplicationServiceTest` が全テストパス
- [x] Phase 1-C の回帰テストは deterministic モードで反復可能に実行でき、Bedrock 接続を必須にしない

**進捗メモ（2026-04-25）:**
- menu-service にカテゴリ/タグ付き16メニュー、代替候補ランキング、`calculate_total_tool`、kitchen-service 内部連携を実装
- menu-service に family 向けバンドル選定を追加し、「4人で4000円以内、子ども1人います」で `combo-kids` を含む予算内セットと人数分ドリンクを返すようにした
- menu-service の suggest 経路で、`combo-crispy` 欠品時に `combo-teriyaki` への代替採択が返る受け入れテストを追加した
- Phase 1-C のテストは Bedrock 直結ではなく deterministic モードで実行し、Java の候補選択・合計検証・欠品代替・API 契約の回帰を高速に確認する方式とした
- kitchen-service に調理ライン種別、キュー深度、`prep_scheduler_tool`、混雑時代替ライン提案を実装
- order-service は `/api/order/chat` を廃止し、`suggest` / `confirm-items` / `confirm-delivery` / `confirm-payment` のステップ別APIへ移行。セッションに `workflowStep` を保持
- `mvn -f food-delivery-demo/pom.xml test` 通過。customer-ui の trace panel は仕様どおり Phase 4-C まで維持

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
- [x] 全サービスが起動時に自身を登録する
- [x] `POST /registry/discover` に「外部ETAを提供するサービスは？」を送ると hermes-adapter と idaten-adapter が返る（icarus-adapter は除外）
- [x] `POST /registry/discover` に「メニュー代替を扱うサービスは？」を送ると menu-service が返る
- [x] `GET /registry/services` が全サービスのエージェント仕様と稼働状態を返す（icarus-adapter も NOT_AVAILABLE で表示）

**進捗メモ（2026-04-25）:**
- `registry-service` を新規 Spring Boot モジュールとして追加し、`/registry/register` `/registry/discover` `/registry/services` `/registry/health` を実装
- `capability-registry-agent` と `capability_match` ツールを追加し、自然言語 discover と稼働状態フィルタを提供
- `hermes-adapter` / `idaten-adapter` を compose に追加し、`icarus-adapter` は registry-only の停止中エントリとして seed
- customer/menu/kitchen/delivery/payment/order の各サービスに起動時自己登録を追加
- `order-service` の compose 起動失敗要因だった空文字 `ARACHNE_STRANDS_MODEL_PROVIDER` を除去し、`customer-ui` を含む全 stack の healthy 起動を確認
- registry の health 集約は Spring Actuator の `UP` と adapter 独自の `AVAILABLE` の両方を解釈するように補強し、runtime で `hermes`/`idaten` discover と `/registry/health` の応答を確認

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
- [x] サポートチャットがキャンペーン・FAQ・稼働状況の質問に回答できる
- [x] 注文確定後にフィードバックリクエストが support-service に届く
- [x] order ↔ support ハンドオフがUI上で動作
- [x] `SupportServiceApiTest` が全テストパス

**進捗メモ（2026-04-26）:**
- `support-service` module を追加し、`/api/support/chat`、`/api/support/feedback`、`/api/support/campaigns`、`/api/support/status` を公開した
- support-service は起動時に registry-service へ自己登録し、稼働状況 API は `GET /registry/health` を参照する構成にした
- support-agent の deterministic foundation と `faq_lookup` / `campaign_lookup` / `service_status_lookup` / `feedback_lookup` / `order_history_lookup` ツールを追加した
- `SupportServiceApiTest` で認証付きサポートチャットと問い合わせ分類を回帰化した
- order-service が注文確定後に support-service の `/api/support/feedback` へ事後サポート受付を通知するようにした
- customer-ui に `/support` ページ（sp-* CSS, キャンペーン・稼働状況サイドバー、サポートチャット）を追加した
- `/home` ページに「サポートセンター」リンクボタンを追加した
- `next.config.ts` に `/api/support/*` プロキシを追加し、Dockerfile と compose.yml で `SUPPORT_SERVICE_ORIGIN` を配線した
- `npm run build` で型エラーなし確認済み（`/support` ルート含む全7ページが正常生成）
- Phase 2-B 完了 ✅

#### 2-C: Spring ベストプラクティスへの責務分割と境界インターフェース導入

**目的:** Phase 3-B のメタツール委譲に入る前に、巨大化した `*ServiceApplication.java` と重複した integration test 足場を分割し、Spring の典型構造に寄せながら修正難易度と回帰リスクを下げる。

**方針:**
- 一括で全サービスを作り直さず、直近で変更が集中しているサービスから段階的に揃える
- interface は全クラスに機械的に追加せず、`Application Service`、`Gateway / Client`、`Repository` などの責務境界にだけ置く
- `Controller`、DTO、record は concrete のままにし、package と依存方向で責務を分ける
- 実装クラスは可能な限り package-private とし、interface の外へ実装詳細を露出しない

**補足:** この phase は 3-A 完了後の作業で必要性が明確になったが、内容としては 3-A より前に入っているべき整理フェーズである。計画上は 3-A 前の準備として挿入し、実行上は 3-B 着手前に最低限の slice を消化する。残りサービスへの横展開は、忘れられないよう別フェーズ（3-C）として管理する。

**変更内容:**
- `order-service` / `support-service` / `menu-service` / `kitchen-service` の `RegistryServiceEndpointResolver` を `*Application.java` から独立ファイルへ切り出す
- `order-service` と `support-service` を優先し、`Controller` / `Application Service` / `Gateway or Client` / `Repository` / `Config` の責務を分ける
- `Application Service`、`Gateway / Client`、`Repository` の境界に interface を導入し、実装を interface の背後へ隠す
- `menu-service` と `kitchen-service` はまず resolver と gateway を分離し、3-B で触る責務の境界を先に整える
- API test に散在している `registryServer` / `registryDispatcher()` / `drainRequests()` / `recordedPaths()` などの補助コードを test support に寄せ、registry lookup 回帰の足場を重複実装しない形へ揃える

**段階的な適用順:**
1. `order-service`: 3-B の主戦場なので最優先で `Application Service` / downstream `Gateway` / resolver を分離
2. `support-service`: `order-service` と同じ構造へ寄せ、gateway / resolver を分離
3. `menu-service` / `kitchen-service`: resolver と gateway を分離し、3-B で必要な境界だけ先に揃える
4. `delivery-service` / `payment-service` / `customer-service`: 3-B 後の 3-C で同じ構造へ横展開し、全体の品質と修正しやすさを均一化

**進捗メモ（2026-04-26）:**
- `order-service` では `RegistryServiceEndpointResolver` を独立ファイルへ切り出し、`MenuGateway` を interface + registry 実装へ分離した
- `support-service` では `RegistryServiceEndpointResolver` と `OrderHistoryGateway` を `*Application.java` 外へ切り出し、注文履歴参照の責務境界を明示した
- `menu-service` では `KitchenCheckGateway` と `RegistryServiceEndpointResolver` を分離し、`kitchen-service` では `MenuSubstitutionGateway` と `RegistryServiceEndpointResolver` を分離した
- `order-service` では追加で `DeliveryGateway` / `PaymentGateway` / `SupportGateway` も interface + registry 実装へ切り出し、3-B で触る主要変更点が単一巨大ファイルへ戻らないようにした
- `food-delivery-demo/test-support` モジュールを追加し、`MockWebServer` の `drainRequests()` / `recordedPaths()` / `requireRequest()` / `trimTrailingSlash()` を共有化して、4 サービスの API test から重複 helper を削減した
- 回帰確認として `MenuServiceApiTest` / `KitchenServiceApiTest` / `OrderServiceApiTest` / `SupportServiceApiTest` と `mvn -f /home/akring/arachne/food-delivery-demo/pom.xml test` を再実行し、すべて通過した

**完了基準:**
- [x] `order-service` / `support-service` / `menu-service` / `kitchen-service` の registry resolver が `*Application.java` 外へ分離されている
- [x] `order-service` と `support-service` の `Application Service` / downstream `Gateway or Client` が `*Application.java` 外へ分離され、interface 境界が導入されている
- [x] `order-service` の主要な変更点が単一巨大ファイルに集中しない構成になっている
- [x] registry lookup 用の test helper 重複が減り、各 API test が service 固有の差分に集中している
- [x] 3-B 前に必要なサービスは Spring の典型的な責務分割へ寄っており、残りサービスへ同パターンを横展開できる状態になっている
- [x] `mvn -f food-delivery-demo/pom.xml test` が通る

---

### Phase 3: AI ネイティブ サービスメッシュ

**目的:** 「スキルを業務専門家が書ける」アーキテクチャへ。サービス名・エンドポイントをプロンプト/スキルから排除する。

#### 3-A: URL 解決を registry-service 経由に変更

**変更内容:**
- 各サービスの環境変数からエンドポイントURLを削除。サービス名のみに変更
  - 例: `MENU_SERVICE_BASE_URL=http://menu-service:8080` → `MENU_SERVICE_NAME=menu-service`
- 各サービス起動時に registry-service にエンドポイントを登録し、他サービス呼び出し時は registry から URL を解決してキャッシュ
- compose.yml の環境変数を整理

**進捗メモ（2026-04-26）:**
- 最初の Phase 3-A slice として、`support-service` の注文履歴参照を `ORDER_SERVICE_BASE_URL` 直指定から `ORDER_SERVICE_NAME` + `registry-service` の `GET /registry/services` 解決へ切り替えた
- `compose.yml` の `support-service` 環境変数は `ORDER_SERVICE_NAME=order-service` に更新し、`SupportServiceApiTest` で registry 経由の解決を回帰化した
- 続く slice として、`order-service` の `menu` / `delivery` / `payment` / `support` 呼び出しを `*_SERVICE_NAME` + `registry-service` の `GET /registry/services` 解決へ切り替え、固定 base URL `RestClient` Bean を除去した
- `kitchen-service` の `menu_substitution_lookup` も `MENU_SERVICE_NAME=menu-service` + `registry-service` 解決へ揃え、`OrderServiceApiTest` と `KitchenServiceApiTest` で registry lookup を回帰化した
- `menu-service` の `kitchen-service` 呼び出しも `KITCHEN_SERVICE_NAME=kitchen-service` + `registry-service` の `GET /registry/services` 解決へ切り替え、`MenuServiceApiTest` を registry lookup 前提に更新した
- `food-delivery-demo` 内の残存 `*_BASE_URL` 監査では、Phase 3-A の direct service-to-service URL として残っていた本番経路はこの `menu-service -> kitchen-service` の 1 本のみだった。残りは `DELIVERY_REGISTRY_BASE_URL` 自体、もしくは fallback / test 用 property としての保持に留まる

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
- [x] 全スキルファイルにサービス名・エンドポイントパスが含まれない
- [x] order-service の `RestClient` Bean がハードコードURLを持たない
- [x] 注文フローが registry-service 経由のURL解決で正常動作
- [x] registry-service なしでは動かないことが compose の depends_on で表現されている

**進捗メモ（2026-04-26）:**
- `order-service` は `menu` / `delivery` / `payment` / `support` の downstream gateway を service-name 解決から capability query + registry `requestPath` 解決へ移行し、trace 文言も capability ベースに更新
- `kitchen-service` は `menu_substitution_lookup` を `欠品時の代替候補提示` capability ベース委譲へ切り替え、tool description と system prompt から menu 固定前提を除去
- `OrderServiceApiTest` と `KitchenServiceApiTest` は「別名サービス + registry 提供 requestPath」で通る回帰を追加
- `mvn -f /home/akring/arachne/food-delivery-demo/pom.xml test` 通過

#### 3-C: 残りサービスへの構造標準化の横展開

**目的:** 2-C で確立した Spring ベストプラクティスの責務分割と境界インターフェースのパターンを、`delivery-service` / `payment-service` / `customer-service` にも適用し、サービス間で品質と修正しやすさを揃える。

**変更内容:**
- `delivery-service` / `payment-service` / `customer-service` を `Controller` / `Application Service` / `Gateway or Client` / `Repository` / `Config` の責務へ分割する
- `Application Service`、`Gateway / Client`、`Repository` の境界に interface を導入し、実装を interface の背後へ隠す
- 2-C で作成した test support パターンを残りサービスへ横展開し、integration test の重複を減らす
- service ごとの package 構成と visibility を揃え、以後の機能追加で巨大 `*Application.java` に戻らない状態を作る

**完了基準:**
- [x] `delivery-service` / `payment-service` / `customer-service` が 2-C と同じ責務構造へ移行している
- [x] 境界インターフェースと実装の分離が残りサービスにも適用されている
- [x] 残りサービスの API test が service 固有の差分に集中したまま維持されている
- [x] `food-delivery-demo` 全体で責務分割と visibility の方針が揃っている
- [x] `mvn -f food-delivery-demo/pom.xml test` が通る

**進捗メモ（2026-04-27）:**
- `payment-service` から `PaymentController` / `PaymentApplicationService` / `PaymentProfileRepository` / `PaymentRegistryConfiguration` / 型定義を分離し、`PaymentServiceApplication.java` を起動 + security のみへ縮小
- `PaymentServiceApiTest` を再実行し、既存の認証拒否と支払い確定レスポンス契約が維持されることを確認
- `delivery-service` は `Controller` / `Application Service` / `RegistryConfiguration` / `ArachneConfiguration` / 型定義を分離し、自社レポジトリと外部 ETA client 境界に interface を導入したうえで `DeliveryServiceApiTest` を通過
- `customer-service` は auth/profile/JWKS の controller、security/bootstrap 設定、認証 service、JDBC repository、署名鍵管理、型定義を分離し、repository と signing key 境界に interface を導入したうえで `CustomerServiceApiTest` を通過
- `mvn -f /home/akring/arachne/food-delivery-demo/pom.xml test` を再実行し、全 12 モジュール SUCCESS を確認。Phase 3-C 完了

---

### Phase 4: UI 追加・変更

#### 4-A: ダッシュボード拡張

**追加内容:**
- サポートチャットウィジェット（ダッシュボード右側 or 専用タブ）
- support-service から取得するキャンペーン情報バナー
- support-service から取得するサービス稼働状況インジケーター

**完了基準:**
- [x] `/home` ダッシュボードにキャンペーンバナーが表示される（`GET /api/support/campaigns` から取得）
- [x] バナーは dismiss ボタンで非表示にできる
- [x] hero-right エリアにサービス稼働状況カードが表示される（`GET /api/support/status` から取得）
- [x] ダッシュボードにフローティングサポートエントリボタンが表示され `/support` へ遷移できる
- [x] `npm run build` が型エラーなしで通る

**進捗メモ（2026-04-27）:**
- `home/page.tsx` に `CampaignSummary` / `ServiceHealthSummary` / `SupportStatusResponse` 型を追加し、`load()` で `/api/support/campaigns` と `/api/support/status` を非同期取得する処理を追加した
- nav と hero の間にキャンペーンバナー（gold アクセント・dismiss ボタン付き）を表示するようにした
- hero-right に稼働状況カード（`sp-status-dot--*` 再利用・サービス毎の UP/DOWN バッジ）を追加した
- ページ右下に固定のフローティングサポートエントリボタン（`/support` リンク）を追加した
- `globals.css` に `h-campaign-banner-*` / `h-svc-status-*` / `h-float-support*` CSS クラスを追加した
- `customer-ui/README.md` を更新し Phase 4-A の新機能を記載した
- `npm ci && npm run build` で 7/7 ページ正常生成、型エラーなし確認済み
- Phase 4-A 完了 ✅

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

Phase 1-B は registry-service（Phase 2-A）に依存するため、また Phase 1-C はどのフェーズにも依存しないため、順序を以下に変更する。なお 2-C は 3-B 前に必要な最小 slice を先に実施し、残りサービスへの横展開は独立した未着手フェーズ 3-C として管理する：

```
1-A → 1-C → 2-A → 1-B → 2-B → 2-C → 3-A → 3-B → 3-C → 4-A → 4-B → 4-C
```

| # | フェーズ | 依存 | 理由 |
|---|---|---|---|
| 1 | Phase 1-A: payment-agent 削除 | なし | 単体閉結・最小スコープ |
| 2 | Phase 1-C: menu-agent 強化 + order→kitchen パス廃止 | なし | order/menu/kitchen の3サービスに閉じる |
| 3 | Phase 2-A: registry-service + 外部 ETA モック追加 | なし | 後続全フェーズの基盤 |
| 4 | Phase 1-B: delivery-agent 強化 | 2-A | discover/call メタツールが registry に依存 |
| 5 | Phase 2-B: support-service 追加 | 2-A | registry への登録が必要 |
| 6 | Phase 2-C: Spring ベストプラクティスへの責務分割と境界インターフェース導入 | 2-B | 3-B 前に巨大ファイルと test 重複を減らし、変更境界を明確にする |
| 7 | Phase 3-A: URL解決 registry 経由 | 2-A | registry が稼働している前提 |
| 8 | Phase 3-B: メタツール委譲 | 3-A, 2-C | URL解決基盤と service 内部整理の両方が必要 |
| 9 | Phase 3-C: 残りサービスへの構造標準化の横展開 | 3-B, 2-C | delivery/payment/customer に同じ責務構造を適用し、品質を均一化する |
| 10 | Phase 4-A: ダッシュボード拡張 | 2-B | support-service API が必要 |
| 11 | Phase 4-B: エージェント仕様ビューワー | 2-A | registry API が必要 |
| 12 | Phase 4-C: 注文ワークフローUI | 1-C | ステップ別API（1-C で追加）が必要 |

## 進捗状況

| フェーズ | ステータス |
|---|---|
| Phase 1-A: payment-agent 削除 | ✅ 完了（commit 7c40053） |
| Phase 1-C: menu-agent 強化 + order→kitchen パス廃止 | ✅ 完了 |
| Phase 2-A: registry-service + 外部 ETA モック追加 | ✅ 完了 |
| Phase 1-B: delivery-agent 強化（動的発見 + 外部 API 呼び出し） | ✅ 完了 |
| Phase 2-B: support-service 追加 | ✅ 完了 |
| Phase 2-C: Spring ベストプラクティスへの責務分割と境界インターフェース導入 | ✅ 完了 |
| Phase 3-A: URL解決 registry 経由 | ✅ 完了 |
| Phase 3-B: メタツール委譲 | ✅ 完了 |
| Phase 3-C: 残りサービスへの構造標準化の横展開 | ✅ 完了 |
| Phase 4-A: ダッシュボード拡張 | ✅ 完了 |
| Phase 4-B: エージェント仕様ビューワー | 🔲 未着手 |
| Phase 4-C: 注文ワークフローUI | 🔲 未着手 |

## Next Action

**Phase 4-A（ダッシュボード拡張）に進む。**

次アクション:
1. ダッシュボードに support-service のキャンペーン情報バナーとサービス稼働状況インジケーターを追加する
2. サポートチャットウィジェットの配置と導線を設計し、既存のホーム画面情報設計と衝突しない形にまとめる
3. 4-B のエージェント仕様ビューワーに向けて、registry-service から必要な表示項目の最小取得面を確定する
