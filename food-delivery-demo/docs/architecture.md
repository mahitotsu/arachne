# フードデリバリーデモ アーキテクチャ

このプロダクトトラックは、会話的な公開面を持つサービスではサービス境界とエージェント境界を対応させつつ、公開ワークフロー API や決定論的契約面も併存させた単一ブランドのクラウドキッチン向けカスタマーデリバリーアプリをデモします。

## 設計方針

- UIを親しみやすく保つ: workflow-first の注文導線と、独立した `/support` 会話面・`/agents` 説明面を併置する
- バックエンドを標準的な Spring マイクロサービスとして認識できるように保つ
- 人工的なオーケストレーションUIではなく、実行履歴と `/agents` ビューワーを通じて隠れたエージェントメッシュを可視化する
- 正確性が重要な状態変更は Spring サービス内で決定論的に保つ
- 具体的な設定を維持する: 1つのキッチン、デリバリー専用注文、異なる運用主体を持つ2つの配送レーン

## サービス

- `order-service`
  公開ワークフロー API、Redis バックドのセッション復元、PostgreSQL バックドの確定注文。
- `support-service`
  `support-agent` を通じた FAQ、問い合わせ受付、キャンペーン一覧、稼働状況集約を管理。registry-service と order-service を将来 UI サポート導線へ接続する公開境界。
- `registry-service`
  全サービスのケイパビリティ登録、自然言語 discover、集約ヘルス、仕様一覧を管理。
- `menu-service`
  `menu-agent` を通じた同一ブランドのメニュー検索と代替提案を管理。
- `kitchen-service`
  `kitchen-agent` を通じた単一キッチンの在庫・調理時間の解釈を管理。
- `delivery-service`
  `delivery-agent` を通じた ETA 推定と配送オプション（自社エクスプレス、Hermes、Idaten を含む）の比較を管理。registry-service 経由で AVAILABLE な外部 ETA サービスを動的発見し、実 API 呼び出しまで行う。
- `payment-service`
  決定論的な支払い方法の提示と課金実行を管理。
- `hermes-adapter`
  高速配送パートナーの ETA モック。周期的に停止し、外部候補の可用性変動を表現する。
- `idaten-adapter`
  低コスト配送パートナーの ETA モック。常時利用可能な外部候補を表現する。
- `icarus-adapter`
  起動しない registry-only エントリ。停止中候補の可視化専用。
- `customer-ui`
  `/order` の 4 ステップ注文ワークフロー、`/support` のサポートチャット、`/agents` のエージェント仕様ビューワー、実行履歴パネルを表示する Next.js ウェブアプリ。

## ランタイムストーリー

1. ブラウザの `/order` が注文ステップ入力を `order-service` の公開 API へ送信する。
2. 各バックエンドサービスは起動時に `registry-service` へ自分のケイパビリティとヘルス URL を登録する。
3. `order-service` が Redis から現在の注文セッションを復元する。
4. `order-service` が現在ステップに応じて `menu-service`、`delivery-service`、`payment-service` へファンアウトする。
5. `support-service` は認証済みのサポート問い合わせに対して FAQ、キャンペーン、類似問い合わせを返し、registry-service の集約ヘルスと order-service の注文履歴を必要に応じて参照する。
6. 各ダウンストリーム API は返答前にサービスローカルの Arachne エージェント、または決定論的ロジックを実行する。
7. `menu-service` は内部で `kitchen-service` を呼び、在庫、ETA、欠品代替、混雑提案をまとめて返す。
8. `kitchen-agent` がアイテムを提供できない場合、同一ブランドのメニューから代替候補を `menu-agent` に問い合わせ、単一キッチンで実際に対応できる代替品のみを承認する。
9. `registry-service` はエージェント仕様ビューワー向け一覧と、動的 collaborator discovery 向け capability query を提供する。`delivery-service` はここから `hermes-adapter`、`idaten-adapter`、停止中の `icarus-adapter` を問い合わせる。
10. `order-service` は結果をワークフロー用の構造化レスポンスへ整形して返し、UI は session を使って execution history をユーザー向け証跡として再取得する。
11. 利用可能な候補が複数ある場合、カスタマーは自社エクスプレス、Hermes、Idaten の中から選択する。`delivery-agent` は「急いで」なら最短 ETA を、「安く」なら最安料金を優先して推奨する。
12. `/support` では `support-service` が FAQ、問い合わせ、キャンペーン、稼働状況を会話的に返し、必要に応じて注文履歴や事後フィードバック導線につなぐ。
13. `/agents` では `registry-service` の inventory と各 service の OpenAPI を使い、プロンプト、ツール、スキル、API 契約を説明面として表示する。service-to-service collaborator 解決はここではなく `POST /registry/discover` が担う。
14. 確定時に `payment-service` が課金を実行し、`order-service` が最終注文を PostgreSQL に保存する。
15. 注文保存後、`order-service` は `support-service` の `/api/support/feedback` へ事後サポート受付を通知し、問い合わせ導線を準備する。

## Arachne との親和性

- フロントエンドは1つの形に閉じない: 注文は workflow-first、サポートは会話型、`/agents` は説明面として分離できる
- サービス分解は引き続き通常の Spring Boot エンジニアリング
- マルチエージェント動作はバックエンドの接合点で自然に生まれ、バックエンド全体を巨大なチャットプロンプトに置き換えるものではない
- エージェント間コラボレーションはサービスメッシュを反映できる: `kitchen-agent` は代替ヘルプのために `menu-agent` に一時的に相談できる
- 設定は理解しやすいまま: 1つのクラウドキッチン、1つのブランドメニュー、代替ブランチルーティングなし、明確なオーナーシップを持つ2つの配送レーン
- ユーザーは回答が変わった理由を確認できる: 在庫、ETA、メニュー代替、支払い準備状況は execution history と `/agents` の両面から特定のサービスとエージェントへ辿れる

## Observation / Micrometer

- `order-service` は workflow entrypoint を `delivery.order.workflow` として観測し、`operation` / `outcome` tag でステップ別の count / latency を出す
- `order-service` からの主要 downstream 呼び出しは `delivery.order.downstream` として観測し、`target=menu-service|delivery-service|payment-service|support-service` と `operation` tag で service-to-service 呼び出しを追える
- registry 解決は `delivery.order.registry.lookup` として分離し、service discovery の待ち時間と失敗を注文 workflow 本体とは別に見られるようにする
- `menu-service` / `delivery-service` / `support-service` は Arachne agent 実行を `delivery.agent.invocation` として観測し、`service` / `agent` / `outcome` tag で service-local agent の count / latency を出す
- 同 3 サービスでは `ArachneLifecycleApplicationEvent` を listener で購読し、`delivery.agent.model.call` と `delivery.agent.tool.call` へ変換して model/tool の activity を Actuator から確認できる
- Bedrock 利用時は `AgentResult.metrics().usage()` を `delivery.agent.usage.tokens` / `delivery.agent.usage.cache` へ積み、deterministic mode でも invocation latency と event bridge 指標は残す
- いずれも observation-only な追加に留め、既存の workflow 制御や Arachne / Spring 間の責務境界は変えない