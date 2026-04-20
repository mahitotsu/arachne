# 0004. Agent Definition And Runtime Split

## Status

Accepted

## Context

ADR 0001 established that `Agent` should be treated as a stateful runtime object and not as a standard shared singleton Spring bean. ADR 0003 established `ArachneAutoConfiguration` and `AgentFactory` as the standard Spring integration entrypoint. The next question is whether Arachne should keep the current shape, where `DefaultAgent` holds both definition-like configuration and execution state, or whether it should explicitly split definition and runtime instances into separate types.

The split model is attractive. If the definition were shared as immutable configuration and only the runtime were created as a short-lived instance, the lifecycle story would become clearer. It would also create room to handle definition-side composition and invocation-side state separately for future interrupt/resume, execution backends, and hook/plugin composition.

At the current stage, however, the codebase does not yet require that split. Arachne's standard usage pattern has already converged on `AgentFactory.Builder`, and configuration such as model, tools, conversation manager, session manager, and retry is already assembled effectively on the factory side. The purpose of Phase 3.5 is to establish a misuse-resistant pattern for Spring usage, not to add abstractions preemptively.

Introducing a public definition/runtime split now would also trigger a chain of additional decisions: new public type names, builder responsibilities, how nested invocation from tools should obtain runtimes, whether session belongs to the definition or runtime side, and where resume APIs should hang. Freezing all of that immediately could constrain the later Phase 4 hook and interrupt design.

## Decision

Arachne does not introduce agent definition and runtime instance as separate public abstractions at this time. For now, `AgentFactory` serves as the definition-like assembly entrypoint, and the `Agent` instances built from it remain runtime objects.

The standard policy is:

- `AgentFactory` and its builder defaults take the role of definition-like configuration
- `Agent` remains a runtime object that holds conversation state, session restore/save behavior, and per-invocation changes
- lifecycle safety is achieved first through the standard factory/provider usage pattern rather than through a new definition type
- the decision to split definition and runtime into separate public types is deferred and should be revisited once interrupt, resume, execution backend, and hook/plugin requirements are clearer
- internal implementation may still organize definition-like concepts as needed, but the project does not standardize a new public abstraction or extra layer at this stage

## Consequences

- Within the scope of Phase 3.5, the lifecycle direction can move forward without greatly expanding the API surface.
- Users can keep a consistent mental model: in Spring, create runtimes from `AgentFactory`, without having to learn a separate definition/runtime split right away.
- The option to split definition and runtime remains open, but it becomes a later design problem informed by hook, interrupt, and resume requirements.
- Because `DefaultAgent` continues to hold both definition-like configuration and mutable state, internal responsibility cleanup and documentation still need to compensate for that structure.
- The next higher-priority questions become binding/validation boundaries and reuse of standard Spring beans.

## Alternatives Considered

### 1. Immediately introduce separate public types such as `AgentDefinition` and `AgentRuntime`

Not adopted at this stage. It is a legitimate long-term option, but it would broaden the change scope beyond the goal of Phase 3.5 and force too many related API decisions at once.

### 2. Add only a Spring-specific definition type

Rejected. It would widen the semantic gap between the core and Spring integration and make non-Spring usage harder to align. If a split is needed, it should be treated as a core semantic decision.

### 3. Avoid documenting the definition/runtime question and revisit it only when needed

Rejected. This question naturally follows ADR 0001 and ADR 0003, so failing to record the explicit decision not to split yet would make the same discussion recur later.

### 4. Separate only the internal implementation first and leave the change unexplained externally

Rejected. Internal cleanup may still happen, but the substance of the issue is about public usage patterns and terminology. Because the choice affects later design, it should not be hidden inside an implicit refactor.
