---
description: "Arachne の Phase 6 実装時に使う。provider 拡張、streaming、MCP、multi-agent orchestration、guardrails を扱うときの指針。"
applyTo: "src/main/java/**/*.java"
---
# Phase 6 実装ガイド

現在の重点は ROADMAP の Phase 6、すなわち拡張である。

## 進め方
- Phase 6 は論点が広いため、1 回の変更で roadmap row をまたぎすぎない。`OpenAIModel` / `AnthropicModel`、streaming、MCP、Swarm / Graph、Guardrails は、ユーザー要求が明示しない限り別々に進める。
- 先に public API と標準利用パターンを固める。既存の `AgentFactory` / `Agent` / `Model` の使い方を壊さず、追加機能は opt-in で載せる。
- 既存の Phase 1 から Phase 5 までの同期 API を維持したうえで、必要な拡張だけを追加する。

## 守るべき境界
- core の流れは `Agent -> EventLoop -> Model / Tool` として読みやすく保つ。provider 拡張のために event loop 全体を provider 固有分岐だらけにしない。
- provider 固有処理は各 provider 実装の近傍に閉じ込める。Bedrock 固有コードを他 provider の都合で崩さない。
- streaming は既存の blocking API の代替ではなく追加経路として扱う。non-streaming 利用者に reactive 前提を押し付けない。
- MCP tool support は tool integration の拡張として扱い、session、hook、conversation の既存境界を不要に巻き込まない。
- Swarm / Graph は stateful な `Agent` runtime の shared singleton 化へ戻さず、factory-owned runtime と session 境界の前提を維持する。
- Guardrails は provider 依存の差異が大きいため、generic contract が roadmap task に必要になるまでは provider 実装の近傍に留める。

## 互換性ルール
- Phase 1 から Phase 5 の published behavior を、明示的な roadmap 変更なしに壊さない。
- `AgentFactory` を Spring integration の標準入口として維持する。
- `Agent.run(String)`、structured output、retry、session persistence、named agents、hooks / plugins / interrupts、skills は既定では従来どおり動くようにする。
- 新しい provider や streaming 機能が未設定のとき、既存の Bedrock 同期経路の挙動は維持する。

## 判断ルール
- public API、Spring wiring、lifecycle、provider contract、streaming contract、orchestration model に関わる判断は ADR を追加または更新する。
- provider 拡張、streaming、orchestration、安全機能をまとめて抽象化しすぎない。現在の roadmap row を直接支える責務だけを導入する。
- 将来の全 provider 共通抽象を先回りで広げすぎず、まず現行の roadmap task を閉じる最小構成を選ぶ。
- README、user guide、samples に標準 idiom の変更が出る場合は、実装と同じターンで更新する。