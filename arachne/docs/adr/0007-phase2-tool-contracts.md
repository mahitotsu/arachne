# 0007. Phase 2 Tool Contracts

## Status

Accepted (retrospective)

## Context

Phase 2 introduced three public APIs in Arachne: annotation-driven tools through `@StrandsTool` and `@ToolParam`, qualifier-based scoping for discovered tools, and structured output through `Agent.run(String, Class<T>)`. This lets users describe the runtime input/output contract directly with Spring bean methods and Java types instead of hand-writing JSON schema or tool specs, while keeping invocation metadata on `AgentResult`.

In the current implementation, `AnnotationToolScanner` discovers `@StrandsTool` methods from the Spring context and uses `JsonSchemaGenerator` plus `MethodTool` to build tool specs and invocation binding. `AgentFactory.Builder` also includes discovered tools by default while allowing each agent to control its visible tool surface through `toolQualifiers(...)` and `useDiscoveredTools(false)`.

For structured output, `DefaultAgent` adds a final structured-output tool per invocation, and `EventLoop` forces a retry if the model did not call that tool so it can still recover a typed result. This is not a Bedrock-specific API. It is a public contract layered on top of the existing tool loop.

Phase 3 and Phase 3.5 added named-agent defaults, binding/validation cleanup, Spring `ObjectMapper` reuse, and a pluggable tool executor, but the user-facing Phase 2 contract itself remains in place. Closeout therefore needs to preserve that contract explicitly as a retrospective ADR.

## Decision

Arachne adopts annotation-driven tool discovery, qualifier-based tool scoping, and structured output via a final tool as part of the Phase 2 public contract, and treats them as a backward-compatibility baseline going forward.

The standard policy is:

- a Spring bean method annotated with `@StrandsTool` is the standard auto-discovered tool definition mechanism
- `@ToolParam` remains the lightweight public annotation for schema-facing metadata such as parameter name, description, and requiredness
- discovered tools are visible through `AgentFactory.Builder` by default, but each agent can narrow its tool surface through `toolQualifiers(...)` and `useDiscoveredTools(false)`
- Spring `@Qualifier` is bridged into Arachne's qualifier set and participates in the same scope-control mechanism as `@StrandsTool(qualifiers = ...)`
- structured output is requested through the public API `Agent.run(String, Class<T>)`, with the standard implementation using a final structured-output tool to recover the typed result and attach it to `AgentResult`
- if the model does not call the structured-output tool on its own, the event loop may force its use via a final retry, and that behavior is part of the contract
- Bean Validation is part of the public behavior introduced in Phase 2, but projecting constraints into generated JSON schema is not part of that contract

## Consequences

- Users can express the tool contract and structured-output contract through Java annotations and types, without hand-written JSON schema or provider-specific APIs.
- In Spring integration, the standard explanation for tool discovery and agent-scoped tool selection can remain centered on `AgentFactory`.
- Because structured output rides on top of the existing tool loop, it preserves the provider-independent core flow.
- Later changes such as named agents, binding/validation cleanup, or executor backend replacement need to preserve this Phase 2 public contract.
- Generated schema intentionally remains narrow, leaving room for later work on complex object graphs, validation-to-schema projection, or provider-native structured output.

## Alternatives Considered

### 1. Treat annotation-driven tools as a convenience API and make handwritten `Tool` implementations the only standard contract

Rejected. The value of Phase 2 is precisely that Spring and Java users can define the tool surface through annotations and types. Treating that as mere convenience would diverge from the assumptions in samples, docs, and wiring tests.

### 2. Make discovered tools application-global and provide no per-agent scope control

Rejected. In Spring applications with multiple agents, it is hard to avoid unintended tool exposure unless the tool surface can be narrowed per runtime.

### 3. Make structured output depend only on provider-native APIs

Rejected. Even at the Phase 2 stage, Arachne needed a provider-independent contract that did not assume Bedrock-specific capabilities, and the tool-loop-based approach fits that core flow better.

### 4. Require Bean Validation constraints to be projected into generated schema

Rejected. Constraint projection would be useful, but Phase 2 prioritized establishing runtime validation as public behavior first and did not make schema projection part of the completion condition.