---
description: "Arachne の Phase 2 実装時に使う。StrandsTool アノテーション、tool schema 生成、annotation scanning、tool execution、structured output、AgentFactory 統合を扱うときの指針。"
applyTo: "src/main/java/**/*.java"
---
# Phase 2 実装ガイド

現在の重点は ROADMAP の Phase 2、すなわち annotation-driven tools と structured output である。

## 設計の優先順位
- public API を先に設計する。対象は annotation、builder method、`run` の overload である。
- schema 生成、annotation scanning、tool execution、structured output は責務を分離する。
- Bedrock 固有の事情が必須でない限り、Phase 2 のロジックは provider 非依存に保つ。
- 汎用的すぎる拡張フレームワークより、明示的な型と狭い interface を優先する。

## 守るべき境界
- annotation の解析は、bean discovery を超えて Spring runtime の詳細に依存しない。
- JSON Schema 生成は、tool 公開と structured output の両方で再利用できる形にする。
- Tool execution policy は、並列/直列、error wrapping、result aggregation といった実行責務だけを持つ。
- structured output は、別の agent loop を導入せず、通常の tool-use flow の上に構築する。

## 互換性ルール
- 既存の Phase 1 AgentFactory 経路を壊さない。
- Bedrock の request/response mapping を generic な core package に移さない。
- 不正な tool spec、不正な annotation、structured output failure については、model 非依存の失敗挙動を明示する。

## 判断ルール
- クラスを増やすのは、そのクラスが Phase 2 の中で独立した責務を持つ場合だけにする。
- 将来の provider や plugin のためだけに存在する変更は先送りする。
- public API を変える機能では、完了扱いにする前に必ずテストを更新する。
