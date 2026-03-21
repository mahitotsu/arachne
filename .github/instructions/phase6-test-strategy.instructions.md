---
description: "Arachne の Phase 6 テスト追加・変更時に使う。streaming と steering を扱う場合のテスト方針。"
applyTo: "src/test/java/**/*.java"
---
# Phase 6 テスト方針ガイド

## この repo のテスト分担
- streaming は event ordering、completion、tool-use 境界、error propagation を deterministic test で担保する。
- steering は tool steering と model steering を分けて、action ごとの制御フローを deterministic test で担保する。

## Phase 6 の最低カバレッジ
- 新しい public API ごとに最低 1 本の happy-path test を持つ。
- validation failure、unsupported state、retry path など、新しく導入した failure mode ごとに最低 1 本の negative test を持つ。
- 既存の同期 `Agent.run(...)` 経路が維持されることを、必要に応じて回帰 test で明示する。
- streaming を導入する場合は、text delta、tool use、terminal event、error path の順序を固定して検証する。
- tool steering を導入する場合は、proceed / guide / interrupt の各 action で tool 実行結果と会話履歴が期待どおりになることを検証する。
- model steering を導入する場合は、guided retry 時に応答が破棄され、guidance が次の model turn に反映されることを検証する。

## テストの書き方
- streaming test は timing 依存を避け、収集した event 列を明示的に assert する。
- steering test は LLM の偶然性に依存せず、deterministic model / fake hook inputs で分岐を固定する。
- 標準利用パターンが変わる機能では、`AgentFactory` と Spring wiring を通した lightweight integration test を最低 1 本持つ。
- feature 未使用時の no-regression を重視し、Phase 1 から Phase 5 の既存挙動を壊していないことを必要に応じて明示する。

## Bedrock と live integration に関する方針
- live Bedrock test は opt-in の smoke-level に保つ。
- streaming や steering の品質担保を、AWS や外部ネットワークの偶然性に依存させない。