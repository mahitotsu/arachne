# ADRs

このディレクトリには、Arachne の重要なアーキテクチャ判断を ADR (Architectural Decision Record) として保存する。

## 目的

- Phase をまたいで効く判断を、実装や issue の文脈から独立して参照できるようにする。
- 採用しなかった案や保留した理由も残し、後続フェーズで同じ議論をやり直さないようにする。
- Spring 統合、agent lifecycle、tool binding、session、hook/plugin などの横断的な設計判断を追跡可能にする。
- すでに実装済みの判断も retrospective ADR として棚卸しし、暗黙の前提を明文化する。

## 対象

次のいずれかに当てはまる変更・判断は ADR を作る。

- public API や標準的な利用パターンを変える
- Spring wiring や bean lifecycle の前提を変える
- 複数フェーズに影響する core 境界を決める
- 採用案と却下案の比較が必要な論点を閉じる
- 今回は見送るが、明示的に保留として残したい論点を記録する
- すでに採用済みで、今後の設計や互換性の前提になる判断を後追いで記録する

## 最低限の書式

各 ADR は `docs/adr/NNNN-title.md` とし、最低限次を含める。

- Status
- Context
- Decision
- Consequences
- Alternatives Considered

## Current ADRs

- [0001-agent-runtime-lifecycle.md](0001-agent-runtime-lifecycle.md) — stateful な agent runtime を shared singleton bean として標準化しない
- [0002-session-manager-explicit-session-id.md](0002-session-manager-explicit-session-id.md) — `SessionManager` 境界と explicit `sessionId` 維持を session 永続化の標準方針とする
- [0003-spring-integration-entrypoint.md](0003-spring-integration-entrypoint.md) — Spring Boot auto-configuration と `AgentFactory` を標準統合入口として扱う
- [0004-agent-definition-runtime-split.md](0004-agent-definition-runtime-split.md) — definition/runtime の別型導入は保留し、当面は `AgentFactory` 主導で扱う
- [0005-binding-validation-boundaries.md](0005-binding-validation-boundaries.md) — binding と validation を別段階として扱い、Spring bean 再利用範囲を定める
- [0006-tool-execution-backend.md](0006-tool-execution-backend.md) — `ToolExecutor` の execution backend は固定実装にせず Spring から差し替え可能とする
- [0007-phase2-tool-contracts.md](0007-phase2-tool-contracts.md) — annotation-driven tools、qualifier ベースの tool scope、structured output を Phase 2 の公開契約として扱う
- [0008-hook-registry-and-plugin-boundary.md](0008-hook-registry-and-plugin-boundary.md) — typed hook event、runtime-local registry、plugin bundling、Spring hook discovery の境界を定める
- [0009-interrupt-resume-and-observation-bridge.md](0009-interrupt-resume-and-observation-bridge.md) — interrupt/resume API と observation-only Spring event bridge の境界を定める
- [0010-skills-injection-and-discovery-boundary.md](0010-skills-injection-and-discovery-boundary.md) — `Skill` / `SkillParser`、prompt injection、classpath discovery、`builder().skills(...)` の境界を定める

## 今後の候補

- 将来の provider 拡張や streaming と、skills の注入タイミングをどう整合させるか

## Retrospective ADR の扱い

後追いで作る ADR も有効であり、むしろ必要である。対象は次のような「すでにコードに入っているが、今後も前提として参照される判断」である。

- `SessionManager` 抽象と `InMemorySessionManager` / `FileSessionManager` / Spring Session adapter を採用した判断
- Redis / JDBC backend でも explicit `sessionId` を維持する判断
- Spring Boot auto-configuration と `AgentFactory` を標準の統合入口にした判断
- Phase 2 の annotation tool discovery、tool scope、structured output をどこまで後方互換対象として固定するか

retrospective ADR では、当時の背景を後から補ってよいが、少なくとも「現在のコードベースが何を採用しているか」「代替案は何だったか」「今後どこまで固定したいか」を明示する。

## 運用ルール

- 実装前に判断を閉じる必要がある論点は、実装着手前に ADR を追加または更新する。
- 実装の途中で判断が変わった場合は、コードだけでなく ADR も同じターンで更新する。
- 廃止した判断は削除せず superseded として残す。