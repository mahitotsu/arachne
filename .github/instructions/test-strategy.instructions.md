---
description: "Arachne のテスト追加・変更時に使う。特に Phase 3 の conversation management、session persistence、設定解決、error behavior を扱う場合のテスト方針。"
applyTo: "src/test/java/**/*.java"
---
# テスト方針ガイド

## この repo のテスト分担
- schema 生成、annotation parsing、tool wrapping、structured output coercion は unit test で担保する。
- bean discovery、auto-configuration、AgentFactory integration は Spring wiring test で担保する。
- model -> tool -> model の流れは、実 Bedrock に依存しない event-loop integration test で担保する。
- 実 Bedrock integration test は smoke test の範囲に限定する。
- conversation manager の trimming / restore は unit test で担保する。
- session persistence は storage 実装ごとの unit test と AgentFactory / auto-config の wiring test に分ける。

## Phase 3 の最低カバレッジ
- 新しい public API ごとに最低 1 本の happy-path test を持つ。
- 各 validation rule ごとに最低 1 本の negative test を持つ。
- annotation-driven behavior は、配線そのものを検証する場合を除き、フルアプリ起動なしで検証する。
- structured output は成功経路だけでなく、refusal や failure の経路も検証する。
- conversation manager は message history の境界条件を明示的に検証する。
- session restore は新しい agent instance で復元されることを検証する。
- configuration properties は `application.yml` 相当の property binding から builder の結果まで検証する。

## テストの書き方
- テストは小さく、単一責務に保つ。
- network access より stub model と fake tool を優先する。
- まず外から見える挙動を検証する。対象は returned text、tool call、exception、message history である。
- Spring test を使う場合は、bean の選択結果と wiring outcome を明示的に検証する。
- file-based session test は一時ディレクトリを使い、副作用をテスト内に閉じ込める。

## Bedrock に関する方針
- 実 Bedrock test は opt-in の smoke-level に保つ。
- 一般機能の品質担保を live AWS call に依存させない。
