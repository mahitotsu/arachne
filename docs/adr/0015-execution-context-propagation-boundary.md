# 0015. Execution Context Propagation Boundary

## Status

Accepted

## Context

Arachne already supports configurable parallel tool execution through `ToolExecutionMode` and a replaceable execution backend. In Spring applications, tool methods frequently run with framework-managed thread-local context such as security state, MDC values, tracing state, locale context, or similar execution-scoped metadata.

Once tool execution crosses an executor boundary, those contexts may be lost unless the submitted task is wrapped explicitly. That concern is separate from `ToolInvocationContext`, which exposes logical tool-call metadata to tool authors. The repository needs a minimal contract for executor-boundary propagation without turning it into a general runtime context API.

## Decision

Arachne exposes an opt-in `ExecutionContextPropagation` SPI for wrapping tool execution tasks across executor boundaries.

The standard policy is:

- `ExecutionContextPropagation` wraps submitted tool tasks and is responsible for capture-and-restore behavior around executor boundaries
- `ToolExecutor` applies the configured propagation only when dispatching tool work to the parallel execution backend
- Spring Boot auto-configuration composes all discovered `ExecutionContextPropagation` beans and passes the result through `AgentFactory`
- `AgentFactory.Builder` may also accept an explicit `executionContextPropagation(...)` override for runtime-local usage
- when no propagation is configured, behavior remains unchanged
- this SPI is separate from `ToolInvocationContext` and does not expose logical tool-call metadata

## Consequences

- Spring applications can opt into propagation for security, MDC, tracing, or similar execution-scoped state without replacing the event loop model
- the existing executor replacement contract remains intact and now has a focused extension point for task wrapping
- direct Java users can opt into the same wrapping behavior when they supply their own executor strategy
- the scope stays narrow: propagation applies to executor-boundary task submission, not to the full agent runtime surface

## Non-Goals

- bundling specific integrations for Spring Security, Micrometer tracing, MDC, or transactions into the core library by default
- widening `ToolInvocationContext` to carry executor or framework context
- changing sequential tool execution semantics

## Alternatives Considered

### 1. Fold execution-context handling into `ToolInvocationContext`

Rejected. Logical invocation metadata and executor-boundary propagation solve different problems and should remain separate contracts.

### 2. Make propagation implicit inside the default executor only

Rejected. Users can already replace the execution backend, so propagation needs to be tied to submitted tasks rather than a single built-in executor implementation.

### 3. Add framework-specific built-ins such as Spring Security propagation first

Rejected. The current need is a minimal boundary that users can adopt for their own execution context. Specific framework integrations can be added later if they become part of the shipped contract.
