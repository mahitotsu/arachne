---
description: "Arachne の Phase 3 実装時に使う。品質改善、会話管理、session 永続化、設定 UX を扱うときの指針。"
applyTo: "src/main/java/**/*.java"
---
# Phase 3 実装ガイド

現在の重点は ROADMAP の Phase 3、すなわち品質・設定 UX・Session である。

## 設計の優先順位
- public API を先に設計する。対象は conversation manager、session manager、builder method、configuration properties である。
- 会話管理、session 永続化、設定解決、error type は責務を分離する。
- Bedrock 固有の事情が必須でない限り、Phase 3 のロジックは provider 非依存に保つ。
- 汎用的すぎる拡張フレームワークより、明示的な型と狭い interface を優先する。

## 守るべき境界
- conversation management は message history の整形と縮約だけを担い、model provider 固有処理を持たない。
- session persistence は保存/復元の責務に留め、agent loop の分岐を増やしすぎない。
- configuration properties は default 解決と named agent 解決を混同しない。
- retry や context overflow の扱いは model 呼び出し境界で完結させ、tool 実行や structured output の責務に漏らさない。

## 互換性ルール
- 既存の Phase 1 AgentFactory 経路を壊さない。
- 既存の Phase 2 annotation tool / structured output API を壊さない。
- Bedrock の request/response mapping を generic な core package に移さない。
- session や conversation manager を導入しても、未設定時の `agent.run("...")` の挙動は従来どおり維持する。

## 判断ルール
- クラスを増やすのは、そのクラスが Phase 3 の中で独立した責務を持つ場合だけにする。
- 将来の provider や plugin のためだけに存在する変更は先送りする。
- public API を変える機能では、完了扱いにする前に必ずテストを更新する。
