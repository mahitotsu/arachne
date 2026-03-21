# 0001. Agent Runtime Lifecycle

## Status

Accepted (retrospective)

## Context

In Arachne, `DefaultAgent` is not a pure definition object. It is a runtime object that holds execution state. It keeps `messages` inside the instance, updates conversation history on every `run(...)`, restores session state during construction when sessions are enabled, and persists state after each invocation.

In Phase 2 and Phase 3, the repository included samples that exposed `Agent` as a Spring `@Bean` and injected it directly into other singleton beans. That was concise, but in web and multi-threaded environments it makes it easy for multiple requests to flow into the same runtime instance and accidentally share conversation history and `AgentState`.

Phase 3 made this property even clearer by introducing `SessionManager` plus restore/save support. Because Arachne can persist history to a session backend, multi-turn behavior no longer requires a long-lived singleton agent. Phase 4 adds hooks, plugins, and interrupts that can mutate runtime state and control flow, so keeping the shared-runtime assumption would make misuse even easier.

For that reason, the project needs to state clearly which objects are safe to share as Spring beans and which should be treated as per-use runtime instances.

## Decision

Arachne treats `Agent` as a stateful runtime object and does not standardize shared singleton Spring bean usage as the recommended pattern.

The standard policy is:

- the standard shared entrypoint is `AgentFactory` and its builder defaults
- construct short-lived `Agent` runtime instances per invocation or per session/conversation scope
- in Spring integration, prefer patterns that obtain runtimes through `AgentFactory`, `ObjectProvider`, or an equivalent provider abstraction
- continue to treat `DefaultAgent` as the current runtime that holds both definition-like configuration and execution state; whether to split definition and runtime into separate types will be decided in a later ADR
- treat existing samples or guides that inject `Agent` directly as simplified examples or review candidates, not as the standard recommendation

## Consequences

- Spring Boot auto-configuration and `AgentFactory` become the standard integration entrypoint for safely assembling runtime instances each time.
- Web and multi-threaded environments can avoid accidental conversation-state sharing more easily.
- Samples, the user guide, and wiring tests need to move gradually toward factory/provider-based usage instead of directly sharing an `Agent` bean.
- The current implementation can keep `DefaultAgent` as a mutable runtime, so Phase 3.5 does not have to force a large new abstraction.
- Whether runtime and definition should be split, how scope should be controlled, and how resume APIs should be modeled remain later decisions.

## Alternatives Considered

### 1. Standardize `Agent` as a shared singleton bean

Rejected. The current `DefaultAgent` keeps conversation history and state inside the instance, so this approach depends too heavily on synchronization and caller discipline. In Spring, it also encourages the mistaken assumption that anything injectable is safe to share.

### 2. Keep a shared singleton and make it safe with internal synchronization

Rejected. Internal locking would still not express conversation-level isolation, and it would add both performance costs and API ambiguity. It would also make session restore/save semantics less clear.

### 3. Rely on Spring prototype scope

Rejected. Prototype scope reduces some misuse, but it does not fully express Arachne's standard usage model. It is also harder to explain session-scoped creation, nested invocation from tools, and non-Spring semantics through that mechanism alone.

### 4. Split definition and runtime into separate types first

Not adopted at this stage. It is a plausible long-term direction, but Phase 3.5 is about stabilizing a misuse-resistant standard pattern first, not immediately introducing a new public abstraction.
