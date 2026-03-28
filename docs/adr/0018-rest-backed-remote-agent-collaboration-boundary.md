# 0018. REST-Backed Remote Agent Collaboration Boundary

## Status

Accepted

## Context

Arachne's current shipped surface is centered on Spring Boot integration, runtime-local agent construction through `AgentFactory`, annotation-driven tools, structured output, session continuity, and explicit execution-context propagation for tool execution. The current sample set reinforces that shape: backend integration is demonstrated through capability-oriented tools, agent-as-tool delegation inside one application, secure downstream API access, and deterministic service boundaries.

At the same time, post-MVP product planning keeps remote protocol areas such as A2A deliberately deferred. Arachne needs an explicit near-term boundary for microservice-oriented agent collaboration so contributors do not make incompatible assumptions about how remote coordination should be introduced.

The repository now has two realistic directions for remote collaboration:

- expose or consume remote capabilities through ordinary service APIs and wrap them as Arachne tools
- add first-party protocol support such as A2A and make protocol-native agent interoperability part of the standard integration model

For current Arachne use cases, the first option aligns better with the shipped contract:

- Spring applications already model backend integration through tool methods and service clients
- `ExecutionContextPropagation` cleanly addresses executor-boundary propagation of security, MDC, tracing, or request-scoped state during tool execution
- secure downstream patterns are easier to keep deterministic when authorization, retries, idempotency, and audit remain in application service code rather than in a new agent protocol layer
- the current composed sample explicitly excludes deferred distributed capabilities such as A2A, MCP, and remote orchestration

The repository also evaluated A2A as a possible next-step integration area. That evaluation found that the protocol itself is becoming materially more relevant, but adopting it now would still widen Arachne's public boundary in several ways at once: remote discovery, task lifecycle semantics, streaming/subscription contracts, transport/runtime integration, and Spring-facing configuration and security patterns. The current Java SDK ecosystem reduces protocol implementation cost, but does not eliminate the need to define how those concerns fit Arachne's Spring-first architecture.

## Decision

Arachne adopts REST-backed tool integration as the standard near-term boundary for remote agent collaboration across microservices.

The standard policy is:

- when one service needs another service's AI-backed capability, the default integration shape is an application-defined client or service adapter wrapped as a capability-oriented Arachne tool
- remote collaboration should remain expressed in backend application contracts first, not in a first-party agent interoperability protocol
- agent-as-tool remains the preferred model for coordinator-to-specialist delegation, whether the specialist runtime lives in the same process or behind a service boundary
- security context, caller identity, correlation ids, tracing state, and similar execution-scoped concerns should be propagated through existing application and Spring mechanisms, using `ExecutionContextPropagation` when tool execution crosses executor boundaries
- state-changing remote operations should remain owned by deterministic downstream service contracts with explicit transaction, authorization, idempotency, and audit policies
- A2A remains deliberately deferred and is not part of the current shipped integration contract
- future work may add A2A or another external protocol, but only as a separately proposed boundary with its own ADR

This decision does not reject A2A in principle. It fixes the default architectural path for current Arachne integration work: use explicit service APIs plus tools first, and introduce protocol-level agent interoperability only when there is a demonstrated need that ordinary service contracts cannot satisfy cleanly.

## Consequences

- The current samples, docs, and Spring integration story stay coherent around backend tools, secure downstream calls, and runtime-local agent assembly.
- Contributors have a concrete answer for microservice collaboration today without implicitly expanding the shipped surface into remote orchestration or protocol support.
- Security-sensitive behavior stays easier to reason about because tokens, caller context, and transport details remain in service code instead of being exposed as model-facing protocol concerns.
- Arachne can still support sophisticated collaboration patterns, but they are expected to ride on explicit application contracts unless and until a later ADR introduces a first-party protocol boundary.
- Future protocol work such as A2A will need to justify added complexity against this baseline rather than being treated as the default remote-integration direction.

## Non-Goals

- defining a generic "call any remote agent" abstraction in the current tool surface
- adding first-party A2A client or server support in the core library
- standardizing remote agent discovery, protocol-native task subscriptions, or cross-runtime push-notification semantics as part of the current shipped contract
- preventing applications from experimenting with A2A outside the core shipped boundary

## Re-evaluation Triggers

This boundary should be revisited if one or more of the following become a concrete project need:

- interoperability with agents owned by other teams or organizations where service-specific REST contracts are not practical
- a need for protocol-standard agent discovery and capability advertisement rather than application-specific endpoint knowledge
- repeated demand for long-running remote task semantics, protocol-standard subscriptions, or push-notification workflows across heterogeneous agent runtimes
- a clear Spring-friendly integration story for A2A that fits Arachne's existing lifecycle, session, security, and execution-context boundaries

## Alternatives Considered

### 1. Adopt A2A now as the default remote collaboration model

Rejected for now. This would expand the public boundary across protocol semantics, transport integration, security handling, and Spring-facing usage patterns before the repository has evidence that ordinary service contracts are insufficient for current microservice use cases.

### 2. Keep A2A deferred but record no positive default for remote collaboration

Rejected. That would leave a vacuum around an important integration topic and force the same comparison to be rediscovered in future implementation threads.

### 3. Add a generic remote-agent abstraction without committing to REST or A2A

Rejected. The current tool catalog intentionally avoids generic "call anything" adapters that blur backend boundaries. A vague abstraction would weaken the explicit application-contract posture that current Arachne samples and ADRs reinforce.

### 4. Treat remote collaboration as a sample-only concern rather than an architectural decision

Rejected. This decision affects Spring integration guidance, tool-boundary recommendations, security posture, and the interpretation of deferred protocol work, so it should remain independently referenceable.
