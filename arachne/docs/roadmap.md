# Arachne Roadmap

This document tracks the active implementation roadmap and serves as the canonical source of current phase state.

Read this before starting any cross-session or repo-wide work.

## Active Phase

**Phase 1: Micrometer / Actuator Integration** — not started

## Phases

### Phase 1: Micrometer / Actuator Integration

Goal: Make Arachne observable in standard Spring Boot production setups. Spring Boot developers expect agent calls to appear in the same dashboards as the rest of their application.

Completion criteria:

- `AgentResult.metrics()` data exposed via Micrometer `MeterRegistry`
- Agent call latency, token counts, and retry counts available as metrics
- Spring Boot Actuator integration verified end-to-end
- At least one sample demonstrating metrics setup
- ADR documenting the Micrometer boundary
- `mvn test` passes with regression coverage for the default (no-metrics) path

### Phase 1.5: Extended Reasoning and Citations

Goal: Surface Bedrock-native extended reasoning and citation blocks in Arachne so that Claude 4-series users can access model thinking traces and document citations without leaving the Spring Boot integration. The Bedrock API already supports both; Arachne needs only the Java type mapping and event-loop pass-through.

Completion criteria:

- `reasoningContent` content block type added to `Message` / content block model
- EventLoop passes reasoning blocks through without stripping them
- `AgentResult` exposes reasoning blocks alongside the text response
- `CitationsContentBlock` and `Citation` types added for Bedrock document-citation use cases
- At least one sample or integration test demonstrating extended reasoning output
- `mvn test` passes with regression coverage confirming existing non-reasoning paths are unaffected

---

### Phase 1.6: Hook Enhancements (AfterToolCallEvent retry and AfterInvocationEvent resume)

Goal: Bring two hook-level capabilities that exist in the Python SDK into parity. Both are additive and non-breaking for existing hook consumers.

- `AfterToolCallEvent.retry` — allow a hook to discard the current tool result and re-invoke the tool, mirroring the existing model-retry pattern on `AfterModelCallEvent`.
- `AfterInvocationEvent.resume` — allow a hook to supply the next agent input immediately after an invocation completes, enabling autonomous looping patterns without external orchestration.

Completion criteria:

- `AfterToolCallEvent` exposes a `retry()` / `requestRetry()` method; the EventLoop re-invokes the tool when set
- `AfterInvocationEvent` exposes a `resume(Object)` method; the agent re-invokes itself with the supplied input when set
- Both capabilities are opt-in and do not affect hooks that do not set them
- Tests cover the retry path, the resume path, and the unchanged default paths
- `mvn test` passes with no regressions

---

### Phase 2: MCP Support

Goal: Allow Arachne agents to invoke MCP tools. Leverage Spring AI's MCP client infrastructure rather than implementing MCP from scratch, so Spring Boot developers get a consistent dependency model.

Completion criteria:

- MCP client integration using Spring AI MCP as the transport layer
- MCP-sourced tools registerable alongside `@StrandsTool`-annotated tools
- At least one sample demonstrating MCP tool usage
- ADR documenting the Spring AI MCP dependency boundary and opt-in wiring
- `mvn test` passes with regression coverage for the no-MCP path

### Phase 3: A2A Protocol Support

Goal: Enable agent-to-agent communication via the A2A protocol. Expose Arachne agents as A2A servers and allow them to delegate to remote A2A agents.

Completion criteria:

- A2A client for calling remote agents from within a tool or hook
- A2A server endpoint for exposing an Arachne agent over the A2A protocol
- At least one sample demonstrating A2A agent delegation
- ADR documenting the A2A boundary and session/conversation threading model
- `mvn test` passes with regression coverage for the non-A2A path

## Completed Phases

None yet.
