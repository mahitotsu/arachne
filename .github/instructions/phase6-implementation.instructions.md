---
description: "Arachne の Phase 6 実装時に使う。streaming と steering を扱うときの指針。"
applyTo: "src/main/java/**/*.java"
---
# Phase 6 実装ガイド

現在の重点は ROADMAP の Phase 6、すなわち streaming と steering である。

## 進め方
- Phase 6 では streaming と steering 以外の論点を持ち込まない。provider 拡張、MCP、Swarm / Graph、A2A、Guardrails は今回の MVP から外れている前提で扱う。
- 先に public API と標準利用パターンを固める。既存の `AgentFactory` / `Agent` / `Model` の使い方を壊さず、追加機能は opt-in で載せる。
- 既存の Phase 1 から Phase 5 までの同期 API を維持したうえで、必要な拡張だけを追加する。
- steering は最初から大きな policy engine にしない。まず tool steering と model steering を既存 hook 契約の延長として成立させる。

## 守るべき境界
- core の流れは `Agent -> EventLoop -> Model / Tool` として読みやすく保つ。streaming や steering のために event loop 全体を分岐だらけにしない。
- streaming は既存の blocking API の代替ではなく追加経路として扱う。non-streaming 利用者に reactive 前提を押し付けない。
- steering は plugin / hook の上に載せ、skills と同様に runtime-local な拡張として扱う。session、conversation、tool 実行境界を不要に崩さない。
- tool steering は `BeforeToolCallEvent` の既存 contract を優先して設計し、guide と interrupt の挙動を明示的にする。
- model steering は `AfterModelCallEvent` の後処理だけで無理に表現せず、必要なら guidance 付き retry を event loop 側の最小変更で支える。

## 互換性ルール
- Phase 1 から Phase 5 の published behavior を、明示的な roadmap 変更なしに壊さない。
- `AgentFactory` を Spring integration の標準入口として維持する。
- `Agent.run(String)`、structured output、retry、session persistence、named agents、hooks / plugins / interrupts、skills は既定では従来どおり動くようにする。
- streaming や steering が未設定のとき、既存の Bedrock 同期経路の挙動は維持する。

## 判断ルール
- public API、Spring wiring、lifecycle、streaming contract、steering contract に関わる判断は ADR を追加または更新する。
- streaming と steering をまとめて抽象化しすぎない。現在の roadmap row を直接支える責務だけを導入する。
- 将来の multi-agent / provider 拡張を先回りで広げすぎず、まず現行の roadmap task を閉じる最小構成を選ぶ。
- README、user guide、samples に標準 idiom の変更が出る場合は、実装と同じターンで更新する。