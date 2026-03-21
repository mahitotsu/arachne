# 0008. Hook Registry And Plugin Boundary

## Status

Accepted

## Context

Phase 4 needed to introduce hooks, plugins, and interrupts so that the agent runtime lifecycle could be intercepted in an AOP-like way. Up through Phase 3, Arachne only had hook callsites, while actual dispatch, Spring bean discovery, and plugin bundling were still missing.

At that point, three questions had to be settled. First, at what granularity should the public hook callback API be expressed? Second, where should hooks be dispatched without turning `EventLoop` and `ToolExecutor` into middleware-heavy branching code? Third, how should Spring integration attach hook beans to a runtime?

Phase 5 skills were also planned to sit on top of hooks, which required `BeforeInvocationEvent` and `BeforeModelCallEvent` to be able to intervene in system prompts and conversation state. At the same time, the Spring `ApplicationEvent` bridge needed to remain observation-only and not be confused with control-flow hooks.

## Decision

Arachne adopts a runtime-local `HookRegistry` that dispatches typed mutable events, plus `HookProvider` and `HookRegistrar` as the callback-registration API, as the foundation of the Phase 4 hook system.

The standard policy is:

- the public hook API is expressed explicitly by event type: `BeforeInvocationEvent`, `AfterInvocationEvent`, `BeforeModelCallEvent`, `AfterModelCallEvent`, `BeforeToolCallEvent`, `AfterToolCallEvent`, and `MessageAddedEvent`
- lifecycle callsites remain limited to `DefaultAgent`, `EventLoop`, and `ToolExecutor`, while hook discovery and callback dispatch are centralized in `DispatchingHookRegistry`
- events are mutable, and the required Phase 4 control-flow intervention is performed through those event objects; concretely, hooks can adjust prompt, system prompt, model response, tool input, and tool result
- `Plugin` extends `HookProvider` and serves as a bundling unit that can also return `Tool` instances; plugins are added per runtime through the builder
- in Spring integration, `HookProvider` beans annotated with `@ArachneHook` are auto-discovered and registered into a fresh runtime-local registry on each `AgentFactory.Builder#build()`
- hook-bean discovery on the Spring side is the entrypoint for control-flow hooks and is treated as a separate concern from the observation-only `ApplicationEvent` bridge

## Consequences

- Hook logic can be implemented as a provider-independent core API without exposing Bedrock-specific types through the event API.
- `DefaultAgent` and `EventLoop` avoid becoming full of hook-specific branching because the Phase 4 responsibility is pushed into the registry.
- Plugins become the unit for shipping tools and hooks together, which makes later skills and human-in-the-loop features easier to compose.
- Spring users can keep `AgentFactory` as the standard entrypoint while intervening in runtimes simply by placing hook beans in the application context.
- Interrupts and the Spring `ApplicationEvent` bridge can both be layered on top of this boundary, but they still need separate implementation work because they are not yet complete as public APIs at this point.

## Alternatives Considered

### 1. Keep `HookRegistry` as a no-op callback holder and resolve providers directly at each callsite

Rejected. It would scatter hook-resolution logic across `DefaultAgent`, `EventLoop`, and `ToolExecutor`, breaking the Phase 4 responsibility boundary.

### 2. Introduce a generic middleware chain and collapse model, tool, and invocation into one abstraction

Rejected. It is too abstract for the actual Phase 4 needs and would make the required event types and Spring bridge responsibilities harder to see.

### 3. Register Spring hook beans directly into a singleton registry shared by all runtimes

Rejected. It would conflict with the runtime-local lifecycle direction established by ADR 0001 and ADR 0003 by pushing stateful event mutation and later interrupt state back toward a shared singleton.

### 4. Support plugins only through Spring bean discovery and not through explicit builder registration

Rejected. It would limit non-Spring usage and per-runtime composition, making plugins harder to reuse as a core API.