# Project Status

This document is the quickest snapshot of what Arachne provides on the current branch.

Use it to answer these questions:

- what can I use today?
- what sample demonstrates it?
- what constraints should I account for before integrating it?

For full usage details, see [docs/user-guide.md](user-guide.md).

## Available Today

### Core Runtime

- Spring Boot auto-configuration for `Model` and `AgentFactory`
- `AgentFactory.builder()` and `AgentFactory.builder("name")` for runtime-local agent creation
- Bedrock-backed `Agent.run(String)` and `Agent.run(String, Class<T>)`
- callback-based `Agent.stream(String, Consumer<AgentStreamEvent>)`
- configuration-driven defaults for model, system prompt, retry, conversation window, sessions, and built-ins

### Tools And Structured Output

- built-in read-only tools: `calculator`, `current_time`, `resource_reader`, and `resource_list`
- annotation-driven tools through `@StrandsTool` and `@ToolParam`
- manual `Tool` registration
- Spring tool discovery with qualifier-based scoping and opt-out control
- sequential or parallel tool execution
- `ToolInvocationContext` for logical tool-call metadata
- `ExecutionContextPropagation` for opt-in executor-boundary context propagation
- structured output with generated JSON schema and runtime validation
- Spring-managed `ArachneTemplateRenderer` for deterministic `T -> String` rendering after structured output completes
- `AgentResult.metrics()` usage reporting, including Bedrock cache read/write token counters

### Conversation And Sessions

- in-memory multi-turn conversations inside a single runtime
- `SlidingWindowConversationManager` as the default conversation manager
- `SummarizingConversationManager` as an explicit builder-level compaction option
- `AgentState` for session-scoped key-value state
- `SessionManager`, `InMemorySessionManager`, and `FileSessionManager`
- Spring Session integration for Redis and JDBC repositories while preserving explicit Arachne session ids
- pending interrupts now persist with session state so a rebuilt agent using the same session id can inspect and resume them

### Input Composition

- `PromptTemplate` for named-placeholder template rendering with clear missing-variable failure
- `PromptVariables` for supplying the variable map to template rendering
- `MessageBuilder` for constructing `Message.user(...)` and `Message.assistant(...)` payloads from templates or plain text
- helpers are Spring-neutral and live in `io.arachne.strands.prompt`

### Extensions And Control

- lifecycle hooks through `HookProvider` and `HookRegistrar`
- runtime-local `Plugin` bundling for hooks and tools
- Spring hook discovery with `@ArachneHook`
- observation-only Spring `ApplicationEvent` bridge
- interrupt / resume before tool execution through `AgentResult.interrupts()`, `AgentResult.resume(...)`, `Agent.resume(...)`, and `Agent.getPendingInterrupts()`
- packaged skills with delayed activation and duplicate-load suppression
- runtime-local steering handlers for tool guidance, interrupts, and model guided retry

### Samples And Verification

- runnable samples under `samples/README.md`
- default verification with `mvn test`
- opt-in Bedrock smoke verification for both blocking and streaming simple end-turn paths with `mvn -Dtest=BedrockModelIntegrationTest -Darachne.integration.bedrock=true test`

## Start With These Resources

Choose the smallest reference that matches your integration goal.

- smallest end-to-end runtime: `samples/conversation-basics`
- built-in tools and resource allowlists: `samples/built-in-tools`
- secure downstream calls: `samples/secure-downstream-tools`
- stateful backend mutations: `samples/stateful-backend-operations`
- agent delegation and typed outputs: `samples/tool-delegation`
- tool-call metadata and execution-context propagation: `samples/tool-execution-context`
- session restore: `samples/session-jdbc` or `samples/session-redis`
- approval pause/resume: `samples/approval-workflow`
- packaged skills: `samples/skill-activation`
- streaming and steering: `samples/streaming-steering`
- composed backend reference: `samples/domain-separation`

## Current Constraints

These points matter in real usage today.

- the only built-in model provider is AWS Bedrock
- the main event loop is blocking; streaming is an opt-in callback path layered on top
- streaming is output-only and does not provide bidirectional realtime or audio transport
- Bedrock prompt caching is opt-in and model-dependent
- summary compaction requires explicit `SummarizingConversationManager` wiring
- structured output is aimed at simple JSON-shaped Java records or POJOs
- packaged skills come from builder-supplied values or classpath-discovered `SKILL.md` files
- interrupt/resume for structured-output runs still requires resuming through the string-returning path first
- built-in resource tools remain read-only and operate only within allowlisted `classpath:` and `file:` locations

## Current Non-Goals

These capabilities are not part of the current shipped surface:

- additional model providers beyond Bedrock
- MCP tool support
- multi-agent Swarm or Graph orchestration
- A2A protocol support
- Guardrails integration
- Agent Config support
- Evals SDK support
- bidirectional realtime or audio streaming
- remote skill registries and hot reload

## Related Documents

- `user-guide.md` for setup and usage
- `tool-catalog.md` for the current tool surface
- `repository-facts.md` for repository layout and verification references
- `adr/README.md` for architecture decisions behind the current model