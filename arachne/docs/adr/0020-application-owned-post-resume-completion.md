# 0020. Application Owned Post Resume Completion

## Status

Accepted

## Context

ADR 0009 defines the core interrupt and resume contract for Arachne. An interrupt stops before tool execution, a later resume appends a `tool_result` response to conversation history, and the next model turn decides what happens next.

That core contract is intentionally general, but approval-style product flows sometimes need a tighter business boundary. In the marketplace product track, finance control approval pauses the workflow before settlement-changing work. The paused state must survive replica handoff, but the eventual settlement and notification path must still be deterministic and must not depend on whether a live model cleanly converges after the approval response is injected.

Live Bedrock verification exposed that this distinction matters in practice. The native interrupt boundary worked correctly for pause and replica-safe restore, but letting approval completion depend on another live model turn created a termination risk on the refund path even though the approval decision itself was already explicit and structured.

## Decision

Arachne keeps the ADR 0009 interrupt and resume contract as the core framework boundary.

On top of that boundary, application code may treat the resumed approval payload as deterministic business input and complete the post-resume state transition in Spring-owned logic instead of requiring another model turn to finish the flow.

For approval-style flows, this means:

- use the native interrupt boundary to pause before the side-effecting action
- persist enough session state to survive restore and replica handoff
- accept the human approval response through the existing resume entrypoint
- let the application service or runtime adapter own the completion message and subsequent deterministic business transition when correctness must not depend on model convergence

This does not change the core Arachne resume semantics. It narrows ownership for post-resume business completion in application code when the resumed input is already a typed approval decision.

## Consequences

- The framework interrupt boundary stays unchanged and Bedrock-specific behavior does not leak into the core contract.
- Approval pause and restore still demonstrate session-backed native interrupt handling.
- Product flows can keep deterministic ownership of settlement, notification dispatch, and other side-effecting transitions after approval is recorded.
- Live-model non-termination after resume no longer blocks approval completion when the post-resume input is already structured and authoritative.

## Alternatives Considered

### 1. Always require another model turn after every resume

Rejected. It keeps the product outcome dependent on model termination even when the resumed payload is already a typed approval decision and the remaining work is deterministic.

### 2. Replace the native interrupt boundary with a product-specific side channel

Rejected. It would bypass the existing Arachne pause and restore contract and make approval flows less representative of the framework boundary we want to demonstrate.

### 3. Push settlement and notification ownership back into model output after resume

Rejected. It would blur the service boundary and weaken deterministic ownership of durable business state.