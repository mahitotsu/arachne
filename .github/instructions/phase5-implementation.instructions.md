---
description: "Arachne の Phase 5 実装時に使う。skills、SKILL.md parsing、prompt injection、Spring skill discovery を扱うときの指針。"
applyTo: "src/main/java/**/*.java"
---
# Phase 5 実装ガイド

現在の重点は ROADMAP の Phase 5、すなわち skills である。

## 設計の優先順位
- public API を先に設計する。対象は `Skill` データモデル、`SKILL.md` parser、`AgentSkillsPlugin`、`AgentFactory.Builder#skills(...)`、Spring classpath scan である。
- skills は Phase 4 の hook / plugin 基盤の上に載せる。prompt 注入は `BeforeInvocationEvent` または `BeforeModelCallEvent` 経由で行い、別系統の hidden middleware を増やさない。
- provider 固有の request shaping に閉じず、skill 解決・読み込み・注入の中核は provider 非依存に保つ。
- AgentSkills.io 互換は ROADMAP の要求範囲に留め、未使用フィールドや将来の registry 機能を先回り実装しない。

## 守るべき境界
- `Skill` は instruction data の表現であり、tool・session・hook registry の代替 abstraction にしない。
- `AgentSkillsPlugin` は注入ポリシーを担い、ファイルシステム探索や Spring resource discovery を直接抱え込まない。
- `SKILL.md` の parsing は core で再利用可能にしつつ、classpath scan や bean wiring は Spring integration に閉じ込める。
- `builder().skills(...)` は runtime ごとの追加として扱い、stateful な `Agent` runtime を shared singleton bean 前提へ戻さない。
- Phase 4 で確定した observation-only `ApplicationEvent` bridge と interrupt/resume 境界を壊さない。

## 互換性ルール
- 既存の Phase 1 AgentFactory 経路を壊さない。
- 既存の Phase 2 annotation tool / structured output API を壊さない。
- 既存の Phase 3 retry / conversation / session / named-agent 挙動を壊さない。
- 既存の Phase 4 hook / plugin / interrupt / Spring hook discovery の挙動を壊さない。
- skill が未設定のとき、`agent.run("...")` の挙動は従来どおり維持する。

## 判断ルール
- 新しい抽象を増やすのは、Phase 5 の roadmap task を直接満たす責務がある場合だけにする。
- markdown parser、frontmatter parser、resource scan は責務を分離し、1 クラスに詰め込みすぎない。
- remote skill registry、hot reload、network fetch、UI 向け skill 管理などは後続フェーズへ送る。
- public API を変える機能では、完了扱いにする前に必ずテストと user-facing docs を更新する。