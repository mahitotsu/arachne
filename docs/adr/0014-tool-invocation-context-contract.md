# 0014. Tool Invocation Context Contract

## Status

Accepted

## Context

Arachne already exposes hooks around tool execution, but tool authors themselves do not currently have a standard way to access logical invocation metadata such as the tool use id or the session-scoped `AgentState` during tool execution.

That gap matters for both public tool-authoring styles that Arachne already ships:

- programmatic `Tool` implementations sometimes need per-invocation metadata for correlation, state access, or response shaping
- annotation-driven `@StrandsTool` methods sometimes need the same metadata without forcing authors to drop down to handwritten `Tool` implementations

The next extension must stay separate from execution-context propagation. Spring Security context, MDC, tracing context, transaction context, and other thread-bound concerns belong to executor-boundary propagation work, not to the public tool-authoring contract.

## Decision

Arachne exposes a public `ToolInvocationContext` contract for logical tool-call metadata.

The standard policy is:

- `ToolInvocationContext` contains only logical invocation metadata: tool name, tool use id, raw input, and `AgentState`
- `ToolExecutor` supplies that context for normal tool execution
- `Tool` gains an overload `invoke(Object, ToolInvocationContext)` with a default implementation that preserves the previous `invoke(Object)` contract
- annotation-driven tools may declare a `ToolInvocationContext` parameter, and that parameter is injected at invocation time rather than projected into the model-visible input schema
- this contract does not include Spring or thread-local execution-context propagation

## Consequences

- manual `Tool` implementations can opt into invocation metadata without breaking existing implementations
- annotation-driven tools can access invocation metadata while preserving the existing Java-signature-driven schema model
- the input schema shown to the model remains stable and limited to model-supplied parameters rather than framework-supplied context objects
- future work on execution-context propagation can remain focused on executor and framework boundary handling instead of widening this API into a general context bag

## Non-Goals

- propagation of Spring Security, MDC, tracing, locale, request, or transaction context across executor boundaries
- exposing the full agent runtime, conversation transcript, or model implementation to tool methods
- changing the default tool execution policy or hook ordering

## Alternatives Considered

### 1. Expose `Agent` or the full event-loop state directly to tools

Rejected. It would widen the public tool contract far beyond logical invocation metadata and couple tool authors to runtime internals.

### 2. Reuse hooks as the only source of invocation metadata

Rejected. Hooks are useful for interception and observation, but they are not a good authoring contract for tool implementations themselves.

### 3. Fold execution-context propagation into the same API

Rejected. Logical tool metadata and framework execution-context propagation have different responsibilities and should remain separate design themes.
