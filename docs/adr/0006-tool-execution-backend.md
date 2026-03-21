# 0006. Tool Execution Backend

## Status

Accepted

## Context

Phase 2 made it possible for Arachne to execute tool calls in parallel or sequentially through `ToolExecutionMode`. In the current implementation, however, the parallel backend inside `ToolExecutor` is fixed to `Executors.newVirtualThreadPerTaskExecutor()`, and `AgentFactory.Builder` directly creates `new ToolExecutor(toolExecutionMode)` before passing it to `EventLoop`.

That implementation is simple and reasonable as a Java 21 default, but from the Phase 3.5 perspective it leaves two problems. First, the execution policy of `PARALLEL` versus `SEQUENTIAL` is embedded in the same class as the choice of concrete execution backend. Second, despite providing Spring Boot integration, the design leaves no room to align the tool-execution scheduler or executor with standard Spring infrastructure.

Phase 4 adds hooks, interrupts, and human-in-the-loop control, which makes the tool-execution substrate a likely target for observation, control, and replacement. Some users will want to keep virtual threads, while others will want a shared application-wide `TaskExecutor` or a bounded thread pool. Phase 3.5 does not need a new complex async API yet, but it does need to state that the execution backend should not be treated as a fixed implementation.

## Decision

Arachne keeps `ToolExecutionMode` as the execution-policy API for sequential versus parallel execution, while treating the parallel backend as non-fixed and replaceable through `Executor` or `TaskExecutor` in Spring integration.

The standard policy is:

- `ToolExecutionMode` remains the public API for expressing execution policy
- `ToolExecutor` is responsible for orchestrating tool calls according to policy, not for always constructing its own fixed backend implementation
- Spring integration should allow the backend used for parallel tool execution to be supplied or replaced through `Executor` or `TaskExecutor`
- a virtual-thread-based backend is still acceptable as the default fallback, but not as the only standard implementation
- the event loop remains synchronous, and Phase 3.5 does not introduce reactive or asynchronous public APIs yet

## Consequences

- Spring Boot applications can align the tool-execution substrate with the rest of their execution infrastructure more easily.
- The meaning of `PARALLEL` and `SEQUENTIAL` remains intact while allowing concurrency policy to be tuned through the executor choice.
- `ToolExecutor` and `AgentFactory` need gradual cleanup so they can accept backend injection.
- The current lightweight virtual-thread default can remain while still leaving room for later hook, interrupt, and observability requirements.
- Because the event loop itself stays synchronous, this change remains separate from the later streaming path.

## Alternatives Considered

### 1. Keep the virtual-thread backend fixed inside `ToolExecutor`

Rejected. It is fine for small-scale use, but it does not take advantage of Spring integration and makes it difficult to satisfy applications that need control over the execution substrate.

### 2. Remove `ToolExecutionMode` entirely and leave everything to executor configuration

Rejected. As a public API, it is clearer to expose the policy choice of sequential versus parallel execution explicitly. Backend choice and policy should not be collapsed into the same thing.

### 3. Replace the current design immediately with an asynchronous or reactive tool-execution API

Rejected. Phase 3.5 is about improving backend replaceability, not making the entire event loop asynchronous. Reactive public APIs are a separate concern.

### 4. Add only a Spring-specific implementation and leave the core backend policy undefined

Rejected. Treating only Spring integration specially would blur the division of responsibilities between the core and Spring layers. The better principle is that the backend is pluggable in general, with Spring as the standard replacement path.
