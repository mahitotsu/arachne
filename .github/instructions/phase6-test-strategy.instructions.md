---
description: "Test strategy guidance for Phase 6 maintenance work around streaming and steering."
applyTo: "src/test/java/**/*.java"
---
# Phase 6 Test Strategy Guide

## Test Responsibilities In This Repo
- Cover streaming with deterministic tests for event ordering, completion, tool-use boundaries, and error propagation.
- Cover steering with deterministic tests that separate tool steering from model steering and verify each action's control flow.

## Minimum Coverage For Phase 6
- Add at least one happy-path test for each new public API.
- Add at least one negative test for each newly introduced failure mode, such as validation failure, unsupported state, or retry behavior.
- Add regression coverage where needed to show that the existing synchronous `Agent.run(...)` path still holds.
- When streaming is involved, verify a fixed ordering for text deltas, tool use, terminal events, and error paths.
- When tool steering is involved, verify that `proceed`, `guide`, and `interrupt` each produce the expected tool execution result and conversation history.
- When model steering is involved, verify that guided retry discards the previous response and carries the guidance into the next model turn.

## How To Write The Tests
- Avoid timing-sensitive assertions in streaming tests. Collect the event sequence and assert it explicitly.
- Avoid LLM randomness in steering tests. Fix each branch with deterministic models or fake hook inputs.
- When a feature changes the standard usage pattern, add at least one lightweight integration test through `AgentFactory` and Spring wiring.
- Emphasize no-regression coverage for the behavior that should remain unchanged when the new feature is unused.

## Bedrock And Live Integration Policy
- Keep live Bedrock tests opt-in and smoke-level only.
- Do not make streaming or steering quality depend on AWS behavior or external network conditions.