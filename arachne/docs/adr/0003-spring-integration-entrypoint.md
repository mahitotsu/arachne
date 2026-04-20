# 0003. Spring Integration Entrypoint

## Status

Accepted (retrospective)

## Context

Arachne treats Spring Boot integration as one of its primary sources of value. From the MVP stage onward, the goal has been that a Spring application can wire an agent and run the tool-use loop by passing in a string. That requires a standard entrypoint that lets users consume model wiring, tool discovery, validation, session handling, and retry with minimal manual setup.

In the current implementation, `ArachneAutoConfiguration` assembles `Model`, `Validator`, annotation tool scanning, discovered tools, `SessionManager`, retry strategy, and `AgentFactory`. Users then build runtime instances through `AgentFactory`, applying named-agent defaults and builder overrides as needed.

By the end of Phase 3, configuration had converged on `ArachneProperties` and runtime assembly had converged on `AgentFactory.Builder`. Once ADR 0001 established in Phase 3.5 that shared singleton `Agent` usage is not the standard pattern, Spring integration also needed a clear statement of which objects are intended to be shared as beans.

Arachne still exposes direct constructors for non-Spring usage, but those are lower-level APIs kept for backward compatibility or narrow cases. They are not a sufficient representation of the standard wiring model in Spring environments.

## Decision

Arachne adopts `ArachneAutoConfiguration` and `AgentFactory` as the standard Spring Boot integration entrypoint, and organizes model/tool/session/retry wiring around those two components.

The standard policy is:

- in Spring usage, the first choice is starter-style auto-configuration, with explicit bean overrides when users need to replace defaults
- `AgentFactory` is the standard API for building Arachne runtimes and resolves named-agent defaults, tool selection, conversation management, session handling, and retry in one place
- the long-lived shared beans are definition and infrastructure objects such as `AgentFactory`, `Model`, `Validator`, discovered tools, and `SessionManager`, not `Agent` runtimes themselves
- Spring integration documentation, samples, and tests should describe `AgentFactory` or provider-based usage as the default path
- direct constructors such as `DefaultAgent` or `EventLoop` may remain available, but they are not treated as the representative Spring integration pattern

## Consequences

- Spring Boot applications can understand Arachne's standard wiring as a two-part structure built from `ArachneAutoConfiguration` and `AgentFactory`.
- Extension points such as named-agent settings, tool discovery, session backends, and retry policy remain concentrated around `AgentFactory`, so users do not have to repeat low-level wiring.
- This aligns with ADR 0001 and makes it explicit that runtime creation should move through factory/provider-based patterns.
- Future cleanup around reuse of Spring standard beans such as `Executor`, `ObjectMapper`, `ConversionService`, or hook registries can use the auto-configuration/factory boundary as the first reference point.
- Non-Spring direct-constructor usage remains possible, but support expectations and samples for Spring will center on `AgentFactory`, reducing the prominence of low-level APIs.

## Alternatives Considered

### 1. Treat an `Agent` bean as the standard integration entrypoint

Rejected. ADR 0001 already established that `Agent` is a stateful runtime and that shared singleton usage is not the standard pattern, so it is not an appropriate integration entrypoint.

### 2. Require users to wire `DefaultAgent`, `EventLoop`, and `ToolExecutor` manually

Rejected. It would sharply reduce the value of Spring Boot integration and scatter Arachne-specific configuration UX such as named defaults and discovered tools.

### 3. Ship a Spring Boot starter but no `AgentFactory`, and ask users to combine configuration beans manually

Rejected. It would add extension points, but also scatter the standard usage pattern and make the completion point for an agent runtime unclear.

### 4. Keep Spring integration as a thin convenience layer and expose the core API directly

Rejected. Arachne treats Spring Boot integration as a primary feature, so wiring for tool discovery, named agents, and session backends is part of the standard UX rather than a thin convenience layer.
