# ADRs

This directory stores Arachne's important architectural decisions as ADRs (Architectural Decision Records).

## Purpose

- Keep cross-cutting decisions independently referenceable outside the context of a specific implementation or issue thread.
- Record rejected alternatives and deferrals so the same discussions do not have to be rediscovered later.
- Make major design decisions around Spring integration, agent lifecycle, tool binding, session handling, hooks, and plugins traceable.
- Capture already-implemented decisions retrospectively when they remain part of the project's implicit contract.

## When To Write An ADR

Create an ADR when a change or decision does any of the following:

- changes a public API or the standard usage pattern
- changes Spring wiring or bean-lifecycle assumptions
- fixes a core boundary that affects multiple capability areas
- closes a tradeoff that needs an explicit comparison between accepted and rejected options
- records an explicit deferral that should remain visible
- captures an already adopted decision that still matters for future design or compatibility

## Minimum Format

Each ADR lives at `docs/adr/NNNN-title.md` and must include at least:

- Status
- Context
- Decision
- Consequences
- Alternatives Considered

## Current ADRs

- [0001-agent-runtime-lifecycle.md](0001-agent-runtime-lifecycle.md) - do not standardize stateful agent runtimes as shared singleton beans
- [0002-session-manager-explicit-session-id.md](0002-session-manager-explicit-session-id.md) - keep `SessionManager` and explicit `sessionId` as the standard session-persistence boundary
- [0003-spring-integration-entrypoint.md](0003-spring-integration-entrypoint.md) - treat Spring Boot auto-configuration and `AgentFactory` as the standard integration entrypoint
- [0004-agent-definition-runtime-split.md](0004-agent-definition-runtime-split.md) - defer introducing separate definition/runtime public types and continue with `AgentFactory` as the assembly entrypoint
- [0005-binding-validation-boundaries.md](0005-binding-validation-boundaries.md) - separate binding from validation and define the scope of Spring bean reuse
- [0006-tool-execution-backend.md](0006-tool-execution-backend.md) - keep the tool-execution backend pluggable rather than fixed
- [0007-phase2-tool-contracts.md](0007-phase2-tool-contracts.md) - preserve annotation-driven tools, qualifier-based scope, and structured output as the Phase 2 public contract
- [0008-hook-registry-and-plugin-boundary.md](0008-hook-registry-and-plugin-boundary.md) - define the boundary for typed hook events, runtime-local registries, plugin bundling, and Spring hook discovery
- [0009-interrupt-resume-and-observation-bridge.md](0009-interrupt-resume-and-observation-bridge.md) - define the interrupt/resume API and the observation-only Spring event bridge boundary
- [0010-skills-injection-and-discovery-boundary.md](0010-skills-injection-and-discovery-boundary.md) - define the boundary for `Skill` / `SkillParser`, delayed skill activation, context management, classpath discovery, and `builder().skills(...)`
- [0011-streaming-and-steering-boundary.md](0011-streaming-and-steering-boundary.md) - define the boundary for callback-based streaming, `StreamingModel`, `SteeringHandler`, guided retry, and `builder().steeringHandlers(...)`
- [0012-post-mvp-product-boundary.md](0012-post-mvp-product-boundary.md) - define the shipped contract, deferred features, and ADR-first future extension policy after removing `ROADMAP.md`

## Future ADR Candidates

- provider expansion and how far the current callback-based streaming model should grow into asynchronous APIs
- where external protocol integrations such as MCP or A2A should sit in the architecture
- whether multi-agent Swarm or Graph orchestration belongs in the core or in a plugin/separate module
- how Guardrails and policy enforcement should fit with steering and hooks
- how remote skill registries and hot reload should integrate with the current skills contract

## Retrospective ADRs

Retrospective ADRs are valid and often necessary. They are meant for decisions that are already present in the codebase but still need to remain explicit assumptions going forward, such as:

- adopting the `SessionManager` abstraction plus `InMemorySessionManager`, `FileSessionManager`, and Spring Session adapters
- preserving explicit `sessionId` even for Redis and JDBC backends
- treating Spring Boot auto-configuration and `AgentFactory` as the standard integration entrypoint
- deciding how strongly Phase 2 annotation tool discovery, tool scoping, and structured output are part of the backward-compatibility contract

In a retrospective ADR, it is fine to reconstruct the background after the fact, but it should still state at least what the current codebase has adopted, which alternatives existed, and how strongly the project intends to preserve that decision.

## Maintenance Rules

- When a design question must be settled before implementation, add or update the ADR before the implementation starts.
- If a decision changes during implementation, update the ADR in the same turn as the code.
- Do not delete obsolete decisions. Keep them as superseded records.