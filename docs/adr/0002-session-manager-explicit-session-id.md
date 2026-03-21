# 0002. Session Manager And Explicit Session IDs

## Status

Accepted (retrospective)

## Context

In Phase 3, Arachne introduced `SessionManager` so that multi-turn conversations could be persisted outside the process and restored later. The core side includes `SessionManager`, `InMemorySessionManager`, and `FileSessionManager`, while Spring integration adds `SpringSessionManager` on top of Spring Session repositories.

In this design, Arachne stores conversation history, `AgentState`, and conversation-manager state together as a single agent session. The point of session persistence is not merely to piggyback on web sessions. It is to restore conversation state into a new agent runtime instance.

When Spring Session backends are used, Redis and JDBC repositories normally assume that the backend generates the session id. Arachne, however, wants conversation identity to remain explicit and user-facing on the agent side. The samples for both Redis and JDBC therefore set an explicit `sessionId` and restore the same session after restart.

That makes it necessary to pin down, as a retrospective ADR, which parts belong to the core contract and which belong to the Spring integration adapter.

## Decision

Arachne adopts `SessionManager` as the core boundary for session persistence and keeps explicit `sessionId` as part of the Arachne-side contract regardless of backend type.

The standard policy is:

- the core loads and saves agent sessions through `SessionManager` and does not depend directly on backend-specific APIs
- the logical session unit is `AgentSession`, which bundles `messages`, `AgentState`, and conversation-manager state
- for file, in-memory, Redis, and JDBC backends alike, the explicit `sessionId` chosen by the Arachne user is the conversation identity
- Spring Session integration is a storage adapter; differences in backend session-object creation and attribute serialization are absorbed on the adapter side
- Spring Boot auto-configuration keeps the standard selection path: use `FileSessionManager` when a file directory is configured, otherwise bridge an available Spring Session repository into `SpringSessionManager`

## Consequences

- Even if agent runtimes become short-lived instances, the same `sessionId` can restore the conversation state.
- The public session-persistence contract remains backend-independent, so later hook and interrupt design can treat session identity consistently.
- Redis and JDBC adapters retain some complexity on the Spring side because they absorb backend-internal type differences.
- Web sessions or HTTP sessions are not treated as identical to agent sessions. The same id may be reused when needed, but the Arachne contract is explicitly the agent session id.
- Future storage backends can first be evaluated in terms of whether they fit the `SessionManager` contract.

## Alternatives Considered

### 1. Fully rely on backend-generated session ids

Rejected. It would let backend concerns dictate agent session identity, produce inconsistent usage across Redis, JDBC, and file storage, and make it harder for users to control restore behavior into new agent instances.

### 2. Treat the Spring web session itself as the Arachne session

Rejected. Arachne also runs in CLI, batch, and nested agent invocation scenarios inside tools, so tying the session concept to the web stack would make the core contract too narrow.

### 3. Standardize only file persistence and leave Redis/JDBC to users

Rejected. Phase 3 explicitly included restore verification on Spring Session backends, and a library that provides Spring Boot integration benefits from shipping standard adapters.

### 4. Hide session persistence as an internal `Agent` implementation detail and avoid an explicit boundary

Rejected. It would make backend substitution, testing, and post-Phase-3.5 lifecycle cleanup harder, while leaving the actual contract unclear.
