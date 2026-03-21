---
description: "Guidance for Phase 6 maintenance work around streaming and steering."
applyTo: "src/main/java/**/*.java"
---
# Phase 6 Implementation Guide

Use this file when working on streaming and steering behavior.

## Working Rules
- Do not bring in concerns beyond streaming and steering when touching this area. Provider expansion, MCP, Swarm/Graph, A2A, and Guardrails remain outside the MVP scope.
- Stabilize the public API and standard usage pattern first. Preserve existing `AgentFactory`, `Agent`, and `Model` usage, and add new behavior only as opt-in functionality.
- Keep the existing synchronous APIs intact and add only the extensions that are necessary.
- Do not start with a large policy engine for steering. Establish tool steering and model steering first as minimal extensions of the existing hook contract.

## Boundaries To Preserve
- Keep the core flow readable as `Agent -> EventLoop -> Model / Tool`. Do not turn the event loop into a branch-heavy implementation for streaming or steering.
- Treat streaming as an additional path, not a replacement for the blocking API. Do not force non-streaming users into a reactive model.
- Layer steering on top of plugins and hooks, and treat it as a runtime-local extension like skills. Do not blur session, conversation, or tool-execution boundaries unnecessarily.
- Design tool steering around the existing `BeforeToolCallEvent` contract and make guide/interrupt behavior explicit.
- Do not force model steering into an unnatural `AfterModelCallEvent` post-processing shape. If needed, support guidance-driven retry through minimal event-loop changes.

## Compatibility Rules
- Do not break the shipped behavior when streaming and steering are not in use.
- Keep `AgentFactory` as the standard Spring integration entrypoint.
- Ensure `Agent.run(String)`, structured output, retry, session persistence, named agents, hooks, plugins, interrupts, and skills keep their default behavior unless explicitly changed.
- Preserve the existing synchronous Bedrock path when streaming and steering are not configured.

## Decision Rules
- Add or update an ADR when a change affects public API, Spring wiring, lifecycle, the streaming contract, or the steering contract.
- Do not over-abstract streaming and steering together. Introduce only the responsibilities that directly support the current public contract.
- Avoid widening the design prematurely for future multi-agent or provider expansion. Choose the smallest structure that preserves the shipped scope.
- If the standard idiom changes, update the README, user guide, and samples in the same turn.