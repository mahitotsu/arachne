# 0011. Streaming And Steering Boundary

## Status

Accepted

## Context

Phase 6 needed to add two capabilities without breaking the existing synchronous `Agent.run(...)` contract: a streaming invocation API that could be observed incrementally, and context-aware steering.

The work had to settle four questions.

- How to add streaming to the existing blocking runtime without making the public API reactive-first
- How much of Bedrock provider streaming to expose through the core API
- Which hook/event boundaries should carry tool steering and model steering
- How to represent discard/retry when streaming and model-steering retry collide

By Phases 4 and 5, the hook and plugin boundary was already part of the public contract. Phase 6 therefore needed a minimal extension of that runtime-local boundary rather than a new middleware layer or policy engine.

## Decision

Arachne adopts the following policy for Phase 6 streaming and steering.

- The agent-level streaming API is `Agent.stream(String, Consumer<AgentStreamEvent>)`. The existing `Agent.run(...)` path remains in place, and streaming is an opt-in additional path.
- The public stream-event contract lives in `AgentStreamEvent` and includes at least `TextDelta`, `ToolUseRequested`, `ToolResultObserved`, `Retry`, and `Complete`.
- The optional provider-side capability is extracted as `StreamingModel`. Ordinary `Model` implementations can continue using `converse(...)`, and models without native streaming support can fall back by replaying `converse(...)` events.
- Bedrock-specific `converseStream`, async client behavior, and event mapping remain localized around `BedrockModel`, while the core event loop only sees `ModelEvent`.
- Steering is exposed through `SteeringHandler`, which is a kind of `Plugin` and is added as a runtime-local opt-in extension through the builder.
- Tool steering rides on `BeforeToolCallEvent`. `Proceed` does nothing, `Guide` skips tool execution and returns guidance as an error `ToolResult`, and `Interrupt` connects to the existing interrupt contract.
- Model steering rides on `AfterModelCallEvent`. `Guide` discards the current model response instead of appending it to conversation history, appends guidance as a new user message, and retries the next model turn.
- When model steering requests a retry during streaming, previously emitted provisional deltas are not rewound. Instead, `AgentStreamEvent.Retry` is emitted so subscribers can recognize the discard/retry boundary.
- In Spring integration, steering is enabled through `AgentFactory.Builder#steeringHandlers(...)`, while streaming is enabled by calling `stream(...)` on an agent built from the factory.

## Consequences

- `Agent.run(...)` and the existing synchronous usage pattern remain the default behavior.
- Because streaming is added as a callback-based API, it does not force users onto a reactive stack or a separate runtime model.
- Bedrock provider streaming stays confined to `StreamingModel` implementations, while the core continues to operate on provider-neutral `ModelEvent` values.
- Steering lives on top of hooks and plugins, so it composes as a runtime-local extension just like skills.
- Model-steering retry is represented cleanly on the synchronous path through response discard, while streaming subscribers need to understand the `Retry` event.

## Alternatives Considered

### 1. Make a reactive or `Publisher`-based streaming API the public contract

Rejected. The goal of Phase 6 is to add streaming as an opt-in path while preserving the existing synchronous runtime, and forcing a reactive-first contract would be too heavy.

### 2. Introduce a separate policy engine or middleware chain for steering

Rejected. It is too abstract for a responsibility that already fits inside the existing `HookRegistry` and `Plugin` boundary.

### 3. Buffer streaming deltas internally during model-steering retry and withhold them until accepted

Rejected. It would reduce the value of an incremental subscription API and would also hide pre-tool text deltas. The `Retry` event is a smaller and clearer way to mark provisional-response discard.

### 4. Add tool guidance to conversation history as a separate event type instead of expressing it as a tool result

Rejected. In the current Bedrock/core contract, the standard shape for returning from the tool boundary back to the model is `tool_result`, and Phase 6 keeps the design smaller by reusing that path.