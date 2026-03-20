---
description: "Arachne のテスト追加・変更時に使う。特に Phase 4 の hook dispatch、plugin、interrupt、Spring bridge を扱う場合のテスト方針。"
applyTo: "src/test/java/**/*.java"
---
# Phase 4 テスト方針ガイド

## この repo のテスト分担
- schema 生成、annotation parsing、tool wrapping、structured output coercion は unit test で担保する。
- bean discovery、auto-configuration、AgentFactory integration は Spring wiring test で担保する。
- model -> tool -> model の流れは、実 Bedrock に依存しない event-loop integration test で担保する。
- 実 Bedrock integration test は smoke test の範囲に限定する。
- hook event の生成、dispatch 順序、可視な副作用は unit test で担保する。
- interrupt と resume の流れは、実 Bedrock に依存しない event-loop integration test で担保する。
- Spring `ApplicationEvent` ブリッジと hook bean discovery は Spring wiring test で担保する。

## Phase 4 の最低カバレッジ
- 新しい public API ごとに最低 1 本の happy-path test を持つ。
- 各 validation rule ごとに最低 1 本の negative test を持つ。
- 各 hook event 型ごとに最低 1 本の dispatch test を持つ。
- interrupt は、停止点、返却 payload、resume 後の再開位置を明示的に検証する。
- plugin は、hook と tool をまとめて登録したときの wiring outcome を検証する。
- Spring `ApplicationEvent` ブリッジは観測専用であり、イベント購読だけでは制御フローが変わらないことを検証する。
- configuration properties や builder wiring を導入する場合は、`application.yml` 相当の binding から最終挙動まで検証する。
- Phase 3.5 で追加した runtime 分離の前提を回帰させない。singleton な Spring service から `AgentFactory` を使う並行呼び出しで、会話状態が混線しないことを必要に応じて検証する。

## テストの書き方
- テストは小さく、単一責務に保つ。
- network access より stub model と fake tool を優先する。
- まず外から見える挙動を検証する。対象は returned text、tool call、exception、emitted event、interrupt payload、resume 後の結果である。
- Spring test を使う場合は、bean の選択結果と wiring outcome を明示的に検証する。
- 実行順序が重要な hook は、呼び出し順を文字列やイベント log で固定して検証する。

## Bedrock に関する方針
- 実 Bedrock test は opt-in の smoke-level に保つ。
- 一般機能の品質担保を live AWS call に依存させない。
