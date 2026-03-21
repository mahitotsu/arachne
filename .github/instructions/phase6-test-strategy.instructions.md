---
description: "Arachne の Phase 6 テスト追加・変更時に使う。provider 拡張、streaming、MCP、multi-agent orchestration、guardrails を扱う場合のテスト方針。"
applyTo: "src/test/java/**/*.java"
---
# Phase 6 テスト方針ガイド

## この repo のテスト分担
- provider 拡張は provider contract test と lightweight integration test を中心に担保する。live API 依存は smoke test に限定する。
- streaming は event ordering、completion、tool-use 境界、error propagation を deterministic test で担保する。
- MCP tool support は schema mapping、execution boundary、failure handling を fake / stub server か test double で担保する。
- Swarm / Graph は orchestration の遷移と state isolation を deterministic model / tool で担保する。
- Guardrails は enable / disable 時の request shaping と failure behavior を provider ごとの test で担保する。

## Phase 6 の最低カバレッジ
- 新しい public API ごとに最低 1 本の happy-path test を持つ。
- provider 固有設定、unsupported state、validation failure、transport failure など、新しく導入した failure mode ごとに最低 1 本の negative test を持つ。
- 既存の同期 `Agent.run(...)` 経路が維持されることを、必要に応じて回帰 test で明示する。
- streaming を導入する場合は、text delta、tool use、terminal event、error path の順序を固定して検証する。
- MCP を導入する場合は、remote tool が未接続・不正 schema・実行失敗の各ケースを検証する。
- Swarm / Graph を導入する場合は、複数 agent 間で message / state / session が意図せず共有されないことを検証する。
- Guardrails を導入する場合は、guardrails 無効時に既存 provider 挙動が変わらないことを検証する。

## テストの書き方
- provider test は live service より stub client を優先し、provider 特有の request / response 変換を直接検証する。
- streaming test は timing 依存を避け、収集した event 列を明示的に assert する。
- orchestration test は LLM の偶然性に依存せず、deterministic model で分岐を固定する。
- 標準利用パターンが変わる機能では、`AgentFactory` と Spring wiring を通した lightweight integration test を最低 1 本持つ。
- feature 未使用時の no-regression を重視し、Phase 1 から Phase 5 の既存挙動を壊していないことを必要に応じて明示する。

## Bedrock と live integration に関する方針
- live provider test は opt-in の smoke-level に保つ。
- provider 拡張や streaming の品質担保を、AWS や外部ネットワークの偶然性に依存させない。