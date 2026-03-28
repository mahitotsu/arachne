# Arachne Tool Catalog

This document explains the tool surface that Arachne provides now.

Use it when you need to answer these questions:

- what tools does Arachne ship out of the box?
- how do I expose application tools?
- how are built-ins, discovered tools, and runtime-local tools combined?
- what limits should I account for when designing tools?

For setup and end-to-end usage, see [docs/user-guide.md](user-guide.md).

## Built-In Tools Available Today

The current built-in pack is intentionally small and read-only.

- `calculator`
- `current_time`
- `resource_reader`
- `resource_list`

Built-in groups:

- `read-only`
- `utility`
- `resource`

Group membership:

- `calculator`: `read-only`, `utility`
- `current_time`: `read-only`, `utility`
- `resource_reader`: `read-only`, `resource`
- `resource_list`: `read-only`, `resource`

## What You Can Author

Arachne currently supports these tool authoring paths:

- annotation-driven tools through `@StrandsTool` and `@ToolParam`
- handwritten `Tool` implementations
- runtime-local tool registration on the builder
- Spring-discovered tools with qualifier-based scoping
- plugin-contributed tools, including skill activation and skill resource loading
- agent-as-tool delegation through normal Spring services

## How Tool Composition Works

The current composition model is:

1. start from built-in inheritance unless you disable it
2. add discovered application tools unless you opt out
3. add any builder-supplied or plugin-contributed tools for that runtime

That means:

- built-ins are separate from application-discovered tools
- `use-discovered-tools=false` disables discovered application tools, not built-ins
- `inheritBuiltInTools(false)` disables the built-in baseline for that runtime
- named-agent built-in selections narrow or extend the built-in surface without changing the underlying tool contract

## Tool Runtime Features Available Today

- sequential or parallel execution
- logical tool-call metadata through `ToolInvocationContext`
- executor-boundary propagation through `ExecutionContextPropagation`
- generated JSON schema from Java signatures
- runtime validation for tool input and structured output
- interrupt-aware tool execution through hooks and `resume(...)`

## Operational Cautions

These points affect tool design and deployment today.

- `calculator` is deterministic, read-only, and limited to arithmetic expressions plus `abs`, `round`, `min`, and `max`
- built-in resource tools are read-only
- classpath resource access is allowed from `classpath:/` by default
- file-system resource access is denied until you configure explicit allowlisted roots
- tool retries are not automatic just because model retry is enabled
- `ToolInvocationContext` is for logical invocation metadata, not for thread-local or executor propagation concerns
- if you need executor-boundary propagation, use `ExecutionContextPropagation`
- built-ins and application tools should stay explicit; avoid generic "call anything" adapters that blur application boundaries

## Best Sample References

- built-ins and allowlists: `../samples/built-in-tools/README.md`
- annotation-driven tools and delegation: `../samples/tool-delegation/README.md`
- invocation metadata vs executor propagation: `../samples/tool-execution-context/README.md`
- secure downstream access patterns: `../samples/secure-downstream-tools/README.md`
- interrupts and plugin-contributed tools: `../samples/approval-workflow/README.md`
- packaged skills and skill tools: `../samples/skill-activation/README.md`

## Current Boundary

The current tool surface is focused on backend application integration.

It does not currently add first-party support for:

- MCP-based tool integration
- multi-agent orchestration helpers
- unrestricted file or service invocation adapters
- generic reflection-driven Spring bean execution

For the contract decisions behind this surface, see:

- `adr/0007-phase2-tool-contracts.md`
- `adr/0014-tool-invocation-context-contract.md`
- `adr/0015-execution-context-propagation-boundary.md`
- `adr/0017-built-in-tool-exposure-and-inheritance.md`