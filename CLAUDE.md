# Arachne Guidelines

## Scope
Arachne is a Java port of the Strands Agents Python SDK with Spring Boot integration.
Treat `refs/sdk-python` as behavioral reference material and do not edit it unless the user asks explicitly.

## Repository Operations
- `memories/repo/status.md` は現在の作業状態の正典。repo-wide な作業を始める前に必ず読む。
- エリアの entry point と bounded read set は `.claude/repository-ops/repository-reading-guide.md` を参照する。
- `/ship-changes` — 完了チェック → 検証 → コミット → プッシュ（準備不足の場合はブロック）
- `/session-handoff` — 次セッション向け引き継ぎプロンプトの生成
- ワークフローが変わったときは `CLAUDE.md`、`.claude/commands/`、`.claude/repository-ops/repository-reading-guide.md`、`memories/repo/status.md` を同じ作業単位で同期する。

## Implementation Rules
- Do not treat work as complete until it satisfies the published current scope and the task's explicit conditions.
- Keep changes minimal and scoped to the active implementation theme. Avoid speculative abstractions for later work.
- Do not mix unrelated cleanup, generated `target` output, or submodule-pointer updates into a feature change.
- Preserve Phase 1 behavior unless the current task explicitly requires changing it.
- Unless the task is Bedrock-specific, prefer provider-independent core logic.

## Build And Test
- The default verification command is `mvn test`.
- Use dedicated integration tests for Bedrock smoke validation instead of ad hoc code.
- When a change affects behavior, add or update tests in the same turn: write at least one test that specifies the new contract, and run the owning module's existing tests to confirm no regressions.
- Prefer deterministic tests over timing-sensitive or model-randomness-dependent assertions.
- For shipped opt-in capabilities, add regression coverage that proves the default path still behaves the same when the feature is unused.

## Architecture
- Keep the core flow readable as `Agent -> EventLoop -> Model / Tool`.
- Keep Spring wiring easy to follow from auto-configuration through `AgentFactory`.
- Keep AWS Bedrock-specific handling inside `BedrockModel` or its immediate vicinity.

## Shipped Capability Rules
- Preserve shipped behavior unless the current task explicitly changes the contract and updates the docs and ADRs accordingly.
- Keep opt-in capabilities opt-in. Do not force synchronous users into streaming, reactive, or policy-heavy paths.
- When touching streaming, preserve fixed event ordering and keep streaming as an additional path layered on top of the existing blocking runtime.
- When touching steering, keep it as a minimal extension of the existing hook/plugin boundary. Avoid widening it into a general policy engine unless the task explicitly requires that change.
- When touching tool or model steering, keep boundaries explicit: tool steering should stay centered on the tool-call boundary, and model steering should not be forced into unnatural post-processing shapes.
- When touching runtime-local extensions such as skills, hooks, interrupts, plugins, streaming, or steering, avoid blurring session, conversation, and tool-execution boundaries.

## Implementation Theme Workflow
- Keep durable repository-wide rules in this file.
- Use subdirectory `CLAUDE.md` files for scoped workflows, including active implementation themes and language- or layer-specific guidance that should not apply repository-wide:
  - `arachne/src/main/CLAUDE.md` for Java production code across Arachne, food-delivery, and samples
  - `arachne/src/test/CLAUDE.md` for Java test code
  - `food-delivery-demo/customer-ui/CLAUDE.md` for the Next.js + TypeScript frontend
  - `.claude/CLAUDE.md` for Claude Code workflow and command maintenance
- Keep scoped file content aligned to their real coverage. Do not leave stale constraints from previous themes.
- Before starting a new implementation theme, review whether the existing scoped files are sufficient. If not, update or replace them for the new theme and remove stale constraints from the previous one.
- During that review, check consistency with `arachne/docs/project-status.md`, relevant ADRs, test emphasis, and completion conditions.

## ADR Workflow
- Record important architectural decisions as ADRs under `arachne/docs/adr/`. This includes both new decisions and already adopted decisions that remain important assumptions.
- When a change affects public API, Spring integration, session persistence, tool binding or validation, execution backend, or another cross-cutting boundary, consider adding or updating an ADR in the same turn.
- Even when a decision is deferred, record rejected alternatives or the reason for deferral so later work can reuse that context.

## Exploration Discipline
- For normal implementation and test work, begin from the smallest trusted surface that can identify the target area.
- Start with `arachne/docs/project-status.md`, this file, and the relevant scoped `CLAUDE.md` before widening to implementation, tests, samples, or ADRs.
- In multi-module Java work, identify the owning Maven module, package, or service first. Read its nearest implementation and matching tests before tracing adjacent modules.
- Use the nearest `pom.xml`, module-local `src/main`, and module-local `src/test` trees as the default first-pass scope unless the task clearly crosses a published contract boundary.
- Use broad guides such as `arachne/docs/user-guide.md` and `arachne/docs/README.md` as maps or follow-up references, not as default front-to-back first-pass reading.
- Expand context only when the current surface cannot answer a concrete implementation, contract, or verification question.

## Coding Conventions
- Prefer small classes with clear responsibilities over deep abstraction trees.
- Add helper methods only when they meaningfully reduce branching or repeated property access.
- Do not add new layers or extension points that do not serve the current implementation goal.
