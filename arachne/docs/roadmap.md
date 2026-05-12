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
