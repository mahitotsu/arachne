# Arachne ガイドライン

## スコープ
Arachne は Strands Agents Python SDK の Java 移植であり、Spring Boot 統合を提供する。
refs/sdk-python は挙動の参照元として扱い、ユーザーが明示的に求めない限り編集対象にしない。

## 実装ルール
- ROADMAP 上の現在フェーズを完了してから完了扱いにする。
- 変更は最小限かつフェーズ志向に保ち、後続フェーズのための先回り抽象化を避ける。
- 無関係な cleanup、target 生成物、submodule pointer 更新を feature commit に混ぜない。
- 現在タスクが明示的に要求しない限り、Phase 1 の挙動を維持する。
- Bedrock 固有タスクでない限り、core ロジックは provider 非依存を優先する。

## ビルドとテスト
- 基本の検証コマンドは `mvn test` とする。
- Bedrock の smoke 検証は専用の integration test を使い、ad hoc なコードは増やさない。
- 挙動を変える変更では、同じターンでテストを追加または更新する。

## アーキテクチャ
- core の流れは `Agent -> EventLoop -> Model / Tool` として読みやすく保つ。
- Spring の配線は auto-configuration から AgentFactory まで追いやすい形に保つ。
- AWS Bedrock 固有の処理は BedrockModel またはその近傍に閉じ込める。

## フェーズ運用
- `.github/instructions/` には、現在フェーズ向けの implementation instruction と test-strategy instruction を 1 つずつ維持する。
- フェーズを切り替える際は、実装着手前にその 2 つを必ずレビューして更新する。
- そのレビューでは、ROADMAP との整合、古い制約の除去、テスト重点の更新、完了条件の一致を確認する。

## コーディング規約
- 深い抽象ツリーより、小さく責務の明確なクラスを優先する。
- helper メソッドは、分岐の削減や重複したプロパティ参照の整理に効く場合のみ追加する。
- 直近のフェーズ目標を持たない新しいレイヤや拡張ポイントは作らない。
