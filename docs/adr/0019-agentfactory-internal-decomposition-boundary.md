# 0019. AgentFactory Internal Decomposition Boundary

## Status

Accepted

## Context

ADR 0003 established `ArachneAutoConfiguration` and `AgentFactory` as the standard Spring integration entrypoint. ADR 0004 deferred a public definition/runtime split and kept `AgentFactory` as the definition-like assembly point for runtime-local `Agent` instances.

That public boundary remains correct, but the current `AgentFactory` implementation now concentrates several different internal responsibilities:

- default provider creation from `ArachneProperties.ModelProperties`
- named-agent default resolution and merge logic across shared and per-agent configuration
- runtime assembly through `AgentFactory.Builder`, including tool selection, hook/plugin composition, session wiring, retry wrapping, and `EventLoop` construction

The codebase can tolerate that concentration for now, but future provider expansion, builder growth, or runtime-backend cleanup would make the current monolithic shape harder to evolve safely. At the same time, introducing new public abstractions or new Spring-facing entrypoints would work against ADR 0003 and ADR 0004.

The next step therefore is not a public API redesign. It is to define which internal responsibility buckets are stable, which ones are legitimate future extraction candidates, and what invariants must survive any cleanup.

## Decision

Arachne keeps `ArachneAutoConfiguration` plus `AgentFactory` as the only standard Spring integration entrypoint, and treats the following internal responsibility buckets inside `AgentFactory` as the current decomposition boundary:

1. Model-default resolution and provider creation
2. Named-agent default resolution
3. Runtime assembly in `AgentFactory.Builder`

The policy for each bucket is:

- model-default resolution and provider creation may be extracted later into package-private helpers or collaborators if provider support expands, but must not become a new public entrypoint or require Spring users to wire providers manually
- named-agent default resolution may be extracted later into an internal resolver focused on merge rules and defaults materialization, but the semantics must remain centered on `AgentFactory.builder()` and `AgentFactory.builder(name)`
- runtime assembly stays centered on `AgentFactory.Builder` as the public customization surface; internal cleanup may separate helper methods or package-private collaborators, but must not turn runtime creation into a second user-facing abstraction

Additional rules:

- `ArachneAutoConfiguration` remains responsible for composing shared infrastructure beans and passing them into `AgentFactory`; it should not absorb runtime-local assembly logic from the builder
- `AgentFactory` remains the place where Spring-discovered components and configuration defaults become runtime-local decisions
- cleanup should prefer package-private helpers, records, or internal collaborators over new exported types or extra documentation surface unless the public contract truly changes
- constructor overload cleanup is allowed as an internal refactoring target, but backward-compatible direct-constructor usage remains secondary to the standard Spring path

## Consequences

- the repository now has an explicit record that `AgentFactory` may be decomposed internally without weakening its role as the single standard Spring entrypoint
- provider expansion can reuse the model-creation bucket without forcing an early public provider registry or extra factory layer
- named-agent merge logic can be isolated for readability and testability without changing how applications obtain runtimes
- builder/runtime assembly can be cleaned up incrementally while preserving the mental model established by ADR 0003 and ADR 0004
- future cleanup proposals can be evaluated against a fixed rule: improve internal separation, but do not make Spring users learn a second integration entrypoint

## Alternatives Considered

### 1. Keep `AgentFactory` monolithic and defer all decomposition discussion

Rejected. The class is already large enough that future cleanup pressure is predictable, and leaving the boundary implicit would make later refactors easier to overextend.

### 2. Introduce a new public definition or runtime-assembly abstraction now

Rejected. ADR 0004 explicitly deferred a public definition/runtime split, and the current codebase does not need a broader public surface to make progress.

### 3. Move more runtime assembly into `ArachneAutoConfiguration`

Rejected. That would blur the line between shared Spring infrastructure composition and runtime-local agent assembly, making the standard usage model harder to follow.

### 4. Extract internal helpers as top-level Spring beans

Rejected. The current need is internal decomposition, not a wider bean graph. Turning internal buckets into standard beans too early would create extra extension points without a concrete user-facing benefit.