---
description: "Arachne のテスト追加・変更時に使う。特に Phase 5 の skills、SKILL.md parsing、prompt injection、Spring skill discovery を扱う場合のテスト方針。"
applyTo: "src/test/java/**/*.java"
---
# Phase 5 テスト方針ガイド

## この repo のテスト分担
- `SKILL.md` の frontmatter parsing、本文抽出、validation は unit test で担保する。
- skill から prompt へ何が注入されるか、hook dispatch 上でどの時点に反映されるかは event-loop integration test で担保する。
- `AgentFactory.Builder#skills(...)` と plugin/hook 併用時の wiring outcome は unit test または lightweight integration test で担保する。
- `resources/skills/` の classpath scan と Spring wiring は Spring wiring test で担保する。
- 実 Bedrock integration test は smoke test の範囲に限定し、skills の一般品質担保を live AWS call に依存させない。

## Phase 5 の最低カバレッジ
- `Skill` データモデルと parser の新しい public API ごとに最低 1 本の happy-path test を持つ。
- frontmatter 欠落、必須項目不備、unsupported 形式など、validation rule ごとに最低 1 本の negative test を持つ。
- `AgentSkillsPlugin` は、skill 注入なしとの差分が外から見える形で 1 本以上の integration test を持つ。
- 複数 skill の注入順または合成順が仕様化されるなら、その順序を固定して検証する。
- Spring classpath scan は、`resources/skills/` 配下の検出結果と builder / agent の最終挙動まで検証する。
- Phase 4 で追加した hook / plugin / interrupt / Spring event bridge の前提を回帰させない。

## テストの書き方
- parser test は fixture を小さく保ち、入力 markdown と期待される `Skill` を直接対応づける。
- prompt 注入 test は内部実装より、model に渡った system prompt や invocation 前の可視な差分を検証する。
- skill discovery の Spring test では、bean や resource の検出結果だけでなく、実際に build された agent の挙動を確認する。
- network access や live provider より、stub model と fake resource を優先する。
- skill が未設定のときに既存挙動が変わらないことを、必要に応じて回帰 test で明示する。

## Bedrock に関する方針
- 実 Bedrock test は opt-in の smoke-level に保つ。
- skills の品質担保を Bedrock 固有挙動や prompt 偶然性に依存させない。