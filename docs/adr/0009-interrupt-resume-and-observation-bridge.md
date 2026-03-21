# 0009. Interrupt Resume And Observation Bridge

## Status

Accepted

## Context

The remaining Phase 4 work had to settle two boundaries. The first was where interrupts stop execution and which API resumes it. The second was how to implement the Spring `ApplicationEvent` bridge so hook activity can be observed without turning it into an entrypoint for control-flow mutation.

For interrupts before tool execution, Arachne's current runtime is a synchronous loop, and Phase 4 required keeping tool, model, and session responsibilities distinct. That meant it was more important to define a consistent resume boundary in conversation history than to suspend tool execution mid-flight and return to the same stack frame.

The Spring event bridge is also for observability. If it published the same mutable event objects used by hooks, listeners could influence runtime control flow. Meeting the Phase 4 observation-only requirement therefore required an explicit boundary between hook dispatch and `ApplicationEvent` publication.

## Decision

Arachne adopts the following policy for Phase 4 interrupts/resume and the Spring event bridge.

- Interrupts are raised from `BeforeToolCallEvent`, stopping the loop before actual tool invocation begins.
- The public interrupt surface is `AgentResult.interrupts()` plus `AgentResult.resume(...)`. Resume is performed against the original runtime instance.
- On resume, the external response is appended to conversation history as a `tool_result` user message associated with that interrupt, and the loop resumes from the next model call.
- In this phase, resume does not automatically re-run the original tool invocation. The human response is returned to the model, and the next action is determined by the model and hooks.
- The Spring `ApplicationEvent` bridge is handled by `ApplicationEventPublishingHookProvider`, which publishes immutable snapshots as `ArachneLifecycleApplicationEvent` rather than publishing hook events directly.
- Spring listeners remain observation-only, while runtime control-flow changes stay limited to `HookProvider` and `@ArachneHook`.

## Consequences

- Interrupts are clearly confined to the tool-execution boundary, which keeps them aligned with the model loop and session restore semantics.
- `AgentResult` becomes the public human-in-the-loop entrypoint, so higher-level plugins and skills in later phases can rely on the same resume boundary.
- Resume is expressed as a `tool_result` addition in conversation history, which avoids leaking Bedrock-specific handling into the core.
- Because Spring event listeners never receive mutable hook events, the separation between observability and control hooks remains intact.

## Alternatives Considered

### 1. Automatically resume the original tool invocation after `resume`

Rejected. It would make it unclear when tool side effects are guaranteed and would exceed the responsibility boundary of Phase 4.

### 2. Put the interrupt resume API on `Agent` and let `AgentResult` only return the list

Rejected. Pending interrupts are tied to the immediately preceding result, so placing the resume entrypoint on the result object is less error-prone.

### 3. Publish mutable hook events directly to Spring listeners

Rejected. That would turn an observability API into an effective control-flow hook, which violates the Phase 4 design rule.