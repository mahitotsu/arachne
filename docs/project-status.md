# Project Status

This document replaces `ROADMAP.md` as the repository-level snapshot of what Arachne ships today and what remains deliberately deferred.

## Supported Scope

### Core Runtime And Spring Integration

- Spring Boot auto-configuration for `Model` and `AgentFactory`
- `AgentFactory.builder()` and `AgentFactory.builder("name")` for runtime-local agent creation
- configuration-driven defaults for model id, region, system prompt, retry, conversation window, and session id
- Bedrock-backed `Agent.run(String)` and `Agent.run(String, Class<T>)`
- callback-based `Agent.stream(String, Consumer<AgentStreamEvent>)`

### Tools And Structured Output

- annotation-driven tools through `@StrandsTool` and `@ToolParam`
- logical tool invocation metadata through `ToolInvocationContext`
- opt-in execution-context propagation through `ExecutionContextPropagation`
- Spring-context tool discovery with qualifier-based scoping and opt-out control
- manual `Tool` registration and configurable parallel or sequential execution
- generated JSON schema from Java signatures and Java output types
- runtime validation for tool input and structured output
- agent-as-tool wiring through normal Spring services

### Conversation And Session Management

- in-memory multi-turn conversation inside a single runtime
- `SlidingWindowConversationManager` as the default conversation manager
- `SummarizingConversationManager` as an explicit builder-level compaction option
- `AgentState` for session-scoped key-value state
- `SessionManager`, `InMemorySessionManager`, and `FileSessionManager`
- Spring Session adapters for in-memory, Redis, and JDBC repositories while preserving explicit Arachne session ids

### Extensions And Control Flow

- lifecycle hooks through `HookProvider` and `HookRegistrar`
- runtime-local `Plugin` bundling for hooks and tools
- Spring hook discovery through `@ArachneHook`
- observation-only Spring `ApplicationEvent` bridge
- interrupt / resume before tool execution through `AgentResult.interrupts()` and `AgentResult.resume(...)`
- AgentSkills.io-style skills with delayed activation and duplicate-load suppression
- runtime-local steering handlers for tool guidance, tool interrupts, and model guided retry

### Verification And Samples

- repository verification with `mvn test`
- opt-in Bedrock smoke verification with `mvn -Dtest=BedrockModelIntegrationTest -Darachne.integration.bedrock=true test`
 - runnable samples for chat, tools, tool context, Redis session, JDBC session, hooks/interrupts, skills, and streaming/steering under `samples/`

## Current Constraints

- the only built-in provider is AWS Bedrock
- the main event loop remains blocking; streaming is an opt-in callback path layered on top of that runtime
- callback-based streaming is output-only; it is not bidirectional realtime or audio streaming
- summary compaction requires explicit `SummarizingConversationManager` wiring rather than property-only enablement
- structured output currently targets simple JSON-shaped Java records or POJOs rather than arbitrary object graphs
- skills currently come from builder-supplied values or classpath-discovered `SKILL.md` files, with optional `scripts/`, `references/`, and `assets/` path listing for packaged skills
- interrupt/resume feeds human responses back through the existing tool-result path; interrupted structured-output runs must currently resume through the string-returning path first

## Deliberately Deferred Features

These items are not implemented on the current main branch and are not part of the shipped MVP contract:

- additional model providers beyond Bedrock
- bidirectional realtime or audio streaming
- MCP tool support
- multi-agent Swarm orchestration
- multi-agent Graph orchestration
- A2A protocol support
- Guardrails integration
- Agent Config support
- Evals SDK support
- remote skill registries and hot reload

Future work in these areas should start with an ADR or ADR update before implementation. See `docs/adr/0012-post-mvp-product-boundary.md`.

## Canonical Documentation

- `README.md` for the top-level entry point and quick-start path
- `docs/user-guide.md` for user-facing API, configuration, lifecycle, and sample guidance
- `docs/adr/README.md` for accepted architecture decisions and open ADR candidates
- sample READMEs under `samples/` for runnable reference flows