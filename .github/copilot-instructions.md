# Arachne Guidelines

## Scope
Arachne is a Java port of the Strands Agents Python SDK with Spring Boot integration.
Treat `refs/sdk-python` as behavioral reference material and do not edit it unless the user asks explicitly.

## Implementation Rules
- Do not treat work as complete until it satisfies the published current scope and the task's explicit conditions.
- Keep changes minimal and scoped to the active implementation theme. Avoid speculative abstractions for later work.
- Do not mix unrelated cleanup, generated `target` output, or submodule-pointer updates into a feature change.
- Preserve Phase 1 behavior unless the current task explicitly requires changing it.
- Unless the task is Bedrock-specific, prefer provider-independent core logic.

## Build And Test
- The default verification command is `mvn test`.
- Use dedicated integration tests for Bedrock smoke validation instead of ad hoc code.
- When a change affects behavior, add or update tests in the same turn.

## Architecture
- Keep the core flow readable as `Agent -> EventLoop -> Model / Tool`.
- Keep Spring wiring easy to follow from auto-configuration through `AgentFactory`.
- Keep AWS Bedrock-specific handling inside `BedrockModel` or its immediate vicinity.

## Implementation Theme Workflow
- Keep exactly one implementation-instruction file and one test-strategy instruction file in `.github/instructions/` for the currently active implementation theme.
- Before starting a new implementation theme, review and update both files.
- During that review, check consistency with `docs/project-status.md`, relevant ADRs, removal of stale constraints, test emphasis, and completion conditions.

## ADR Workflow
- Record important architectural decisions as ADRs under `docs/adr/`. This includes both new decisions and already adopted decisions that remain important assumptions.
- When a change affects public API, Spring integration, session persistence, tool binding or validation, execution backend, or another cross-cutting boundary, consider adding or updating an ADR in the same turn.
- Even when a decision is deferred, record rejected alternatives or the reason for deferral so later work can reuse that context.

## Coding Conventions
- Prefer small classes with clear responsibilities over deep abstraction trees.
- Add helper methods only when they meaningfully reduce branching or repeated property access.
- Do not add new layers or extension points that do not serve the current implementation goal.
