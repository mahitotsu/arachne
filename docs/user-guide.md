# Arachne User Guide

This guide documents the features that are available on the current main branch.

## Scope

The current implementation gives you a Bedrock-backed agent runtime with annotation-driven tools, structured output, retry, conversation/session management, hooks/plugins, interrupts, skills, and opt-in streaming plus steering.

For the repository-level scope snapshot, deliberately deferred features, and current contract boundary, see [docs/project-status.md](docs/project-status.md).

Available now:

- Spring Boot auto-configuration for `Model` and `AgentFactory`
- `AgentFactory.builder().build()` for creating an agent
- `AgentFactory.builder("name").build()` for creating an agent from named defaults
- `Agent.run(String)` returning an `AgentResult`
- opt-in retry strategy for retryable model failures
- `@StrandsTool` and `@ToolParam` for annotation-driven tools
- `ToolInvocationContext` for logical tool-call metadata inside tool implementations
- `ExecutionContextPropagation` for opt-in executor-boundary context propagation during tool execution
- automatic tool discovery from Spring beans
- structured output with typed return values through `Agent.run(String, Class<T>)`
- Bedrock model ID and region configuration
- system prompt configuration
- multi-turn conversation state inside a single `Agent` instance
- sliding-window conversation management via `AgentFactory`
- model-backed summary compaction with `SummarizingConversationManager`
- session persistence with in-memory, file-backed, Redis-backed, and JDBC-backed storage
- session-scoped key-value state via `AgentState`
- Spring Session integration through `SessionRepository<?>`, including Redis-backed and JDBC-backed repositories
- named-agent defaults under `arachne.strands.agents.<name>.*`
- typed hook dispatch and plugin bundling
- interrupt / resume handling before tool execution
- Spring `ApplicationEvent` bridge for lifecycle observation
- AgentSkills.io-style `SKILL.md` parsing, delayed skill activation, and loaded-skill context management
- classpath discovery of packaged skills from `resources/skills/`
- callback-based streaming invocation through `Agent.stream(...)`
- tool and model steering through runtime-local `SteeringHandler` plugins

Deliberately deferred features such as additional providers, MCP, multi-agent protocols, Guardrails, and bidirectional realtime/audio streaming are tracked in [docs/project-status.md](docs/project-status.md).

If you want runnable examples instead of only reading the API docs, use these samples:

- [samples/README.md](samples/README.md)
- [samples/conversation-basics/README.md](samples/conversation-basics/README.md)
- [samples/tool-delegation/README.md](samples/tool-delegation/README.md)
- [samples/tool-execution-context/README.md](samples/tool-execution-context/README.md)
- [samples/session-redis/README.md](samples/session-redis/README.md)
- [samples/session-jdbc/README.md](samples/session-jdbc/README.md)
- [samples/approval-workflow/README.md](samples/approval-workflow/README.md)
- [samples/skill-activation/README.md](samples/skill-activation/README.md)
- [samples/streaming-steering/README.md](samples/streaming-steering/README.md)
- [samples/domain-separation/README.md](samples/domain-separation/README.md)

## Contributor Workflows

The repository ships four audit-oriented slash commands for recurring maintenance work:

- `/compat-audit <area>` checks Arachne's compatibility with Strands Agents for a target capability area and separates implemented, partial, deferred, and MVP-missing items.
- `/implementation-candidates <scope>` detects unported or deferred Strands capabilities that are plausible next implementation candidates for Arachne, compares them, and recommends an execution order.
- `/alignment-audit <area>` checks whether implementation, tests, docs, ADRs, instructions, and samples stay aligned for a target capability area.
- `/quality-audit` refreshes the current Maven quality evidence and produces a repository-specific quality report.

Use `/compat-audit` when the external comparison baseline is Strands Agents. Use `/implementation-candidates` when the goal is to turn unported or deferred features into a ranked next-work proposal. Use `/alignment-audit` when the question is whether Arachne's own repository surfaces still match each other. Use `/quality-audit` when the focus is test, coverage, static analysis, dependency, and architecture quality evidence.

## Prerequisites

- Java 21
- Spring Boot 3.5.12
- AWS credentials resolvable by the AWS SDK default credentials chain
- access to the configured Bedrock model in the target AWS region

If you are consuming the library from this repository before publication, install it locally first:

```bash
mvn install
```

## Spring Boot Setup

Arachne registers its auto-configuration through Spring Boot, so having the jar on the classpath is enough to make `Model` and `AgentFactory` available.

The values under `application.yml` are library-wide defaults. They do not define a single canonical agent for the whole application.

For multi-agent applications, keep shared defaults under `arachne.strands.agent.*` and put agent-specific overrides under `arachne.strands.agents.<name>.*`. Named-agent settings are applied only when you call `AgentFactory.builder("name")`.

Configure the model in `application.yml`:

```yaml
arachne:
  strands:
    model:
      provider: bedrock
      id: jp.amazon.nova-2-lite-v1:0
      region: ap-northeast-1
    agent:
      system-prompt: "You are a concise assistant."
      retry:
        enabled: true
        max-attempts: 6
        initial-delay: 4s
        max-delay: 240s
      conversation:
        window-size: 40
      session:
        id: support-session
        file:
          directory: .arachne/sessions
    agents:
      support:
        system-prompt: "You are a customer support agent. Answer in short Japanese sentences."
        session:
          id: support-session
      analyst:
        model:
          id: us.amazon.nova-pro-v1:0
          region: us-east-1
        system-prompt: "You are an analyst. Answer with concise bullet points."
        use-discovered-tools: false
```

Configuration keys currently used on the current branch:

- `arachne.strands.model.provider`
- `arachne.strands.model.id`
- `arachne.strands.model.region`
- `arachne.strands.agent.system-prompt`
- `arachne.strands.agent.retry.enabled`
- `arachne.strands.agent.retry.max-attempts`
- `arachne.strands.agent.retry.initial-delay`
- `arachne.strands.agent.retry.max-delay`
- `arachne.strands.agent.conversation.window-size`
- `arachne.strands.agent.session.id`
- `arachne.strands.agent.session.file.directory`
- `arachne.strands.agents.<name>.model.provider`
- `arachne.strands.agents.<name>.model.id`
- `arachne.strands.agents.<name>.model.region`
- `arachne.strands.agents.<name>.system-prompt`
- `arachne.strands.agents.<name>.use-discovered-tools`
- `arachne.strands.agents.<name>.tool-qualifiers`
- `arachne.strands.agents.<name>.tool-execution-mode`
- `arachne.strands.agents.<name>.conversation.window-size`
- `arachne.strands.agents.<name>.session.id`
- `arachne.strands.agents.<name>.retry.enabled`
- `arachne.strands.agents.<name>.retry.max-attempts`
- `arachne.strands.agents.<name>.retry.initial-delay`
- `arachne.strands.agents.<name>.retry.max-delay`

Notes:

- `provider` must currently be `bedrock`
- if `id` is omitted, Arachne uses `jp.amazon.nova-2-lite-v1:0`
- if `region` is omitted, Arachne uses `ap-northeast-1`
- `agent.system-prompt` is the default prompt used when a builder does not override it
- `agent.retry.*` enables exponential-backoff retry at the model invocation boundary
- retry is disabled by default so unconfigured `agent.run("...")` keeps the prior failure behavior
- the default retry values are `max-attempts=6`, `initial-delay=4s`, `max-delay=240s`
- retry applies only to provider-mapped retryable model failures; it does not retry tool execution or structured-output validation
- `agent.conversation.window-size` is used by the default `SlidingWindowConversationManager`
- `agent.session.id` enables automatic restore/persist across agents created by the builder
- `agent.session.file.directory` switches the default session storage from in-memory to JSON files
- without `agent.session.file.directory`, Arachne falls back to in-memory session storage by default
- when a Spring Session `SessionRepository<?>` bean is present, Arachne uses it instead of the in-memory fallback
- if both a file directory and a Spring Session repository are configured, file storage wins explicitly
- named-agent properties are override-only and do not replace the shared defaults unless you build that named agent explicitly
- these properties are best treated as shared defaults, while `arachne.strands.agents.<name>.*` describes multi-agent applications
- `SummarizingConversationManager` is currently an explicit builder-level feature, not a property-bound default

## Creating An Agent

The standard Spring idiom is to inject `AgentFactory` and build an `Agent` runtime at the point where you need a conversation.

For stateless request handling, build a fresh runtime per call:

```java
@Service
class SupportService {

    private final AgentFactory factory;

    SupportService(AgentFactory factory) {
        this.factory = factory;
    }

    String reply(String prompt) {
        return factory.builder("support")
                .build()
                .run(prompt)
                .text();
    }
}
```

If you intentionally want one in-memory multi-turn conversation inside a component such as a CLI runner, create the runtime once inside that component and keep it there instead of publishing `Agent` as a shared Spring bean:

```java
@Component
class CliRunner {

    private final Agent agent;

    CliRunner(AgentFactory factory) {
        this.agent = factory.builder()
                .systemPrompt("You answer in short Japanese sentences.")
                .build();
    }
}
```

You can still define multiple named agents in the same application. Each one can override the shared defaults from `application.yml`; the key point is that the runtime is built from `AgentFactory`, not shared as the default bean-level integration point.

```java
@Service
class MultiAgentService {

  private final AgentFactory factory;

  MultiAgentService(AgentFactory factory) {
    this.factory = factory;
  }

  String supportReply(String prompt) {
    return factory.builder("support")
        .systemPrompt("You are a customer support agent.")
        .build()
        .run(prompt)
        .text();
  }

  String analystReply(String prompt) {
    return factory.builder("analyst")
        .model(new BedrockModel("us.amazon.nova-pro-v1:0", "us-east-1"))
        .systemPrompt("You are an analyst. Answer with concise bullet points.")
        .build()
        .run(prompt)
        .text();
  }
}
```

So the current model is:

- `application.yml` supplies defaults
- `AgentFactory.builder()` creates one runtime instance for one conversation scope
- `AgentFactory.builder("name")` applies named defaults and still allows explicit builder overrides
- if you need a longer-lived in-memory conversation, keep that runtime in the owning component rather than exposing it as a shared singleton `Agent` bean

Builder precedence is:

1. `builder().model(...)` if you set it explicitly
2. the auto-configured `Model` bean if one exists
3. a default `BedrockModel` created from `arachne.strands.*` properties

Named builder precedence is:

1. `builder("name").model(...)` if you set it explicitly
2. `arachne.strands.agents.<name>.model.*` if present
3. the auto-configured shared `Model` bean if one exists
4. the shared `arachne.strands.model.*` defaults

The current builder defaults for retry are:

1. `builder().retryStrategy(...)` if you set it explicitly
2. the auto-configured retry strategy built from `arachne.strands.agent.retry.*`
3. no retry wrapper when retry is not enabled

For named agents:

1. `builder("name").retryStrategy(...)` if you set it explicitly
2. `arachne.strands.agents.<name>.retry.*` if present
3. the auto-configured shared retry strategy built from `arachne.strands.agent.retry.*`
4. no retry wrapper when retry is not enabled

1. `builder().conversationManager(...)` if you set it explicitly
2. a new `SlidingWindowConversationManager` created from `arachne.strands.agent.conversation.window-size`

For named agents:

1. `builder("name").conversationManager(...)` if you set it explicitly
2. a new `SlidingWindowConversationManager` created from `arachne.strands.agents.<name>.conversation.window-size`
3. the shared `arachne.strands.agent.conversation.window-size`

If you need summary compaction instead of a pure sliding window, provide the conversation manager explicitly:

```java
import io.arachne.strands.agent.conversation.SummarizingConversationManager;

Agent supportAgent = factory.builder()
  .conversationManager(new SummarizingConversationManager(model, 40, 12))
  .build();
```

`SummarizingConversationManager` replaces older turns with one assistant summary message and keeps the most recent turns verbatim. Its persisted state stores the running summary text and the summarization counters, so session restore continues from the existing summary instead of starting over.

1. `builder().sessionManager(...)` if you set it explicitly
2. the auto-configured `SessionManager` bean

1. `builder().sessionId(...)` if you set it explicitly
2. `arachne.strands.agent.session.id`

For named agents:

1. `builder("name").sessionId(...)` if you set it explicitly
2. `arachne.strands.agents.<name>.session.id`
3. `arachne.strands.agent.session.id`

For discovered tools and execution policy:

1. explicit builder calls such as `toolQualifiers(...)`, `useDiscoveredTools(...)`, and `toolExecutionMode(...)`
2. `arachne.strands.agents.<name>.tool-qualifiers`, `use-discovered-tools`, and `tool-execution-mode`
3. shared defaults of all discovered tools enabled with parallel execution

For skills:

1. explicit builder calls through `skills(...)`
2. Spring classpath-discovered skills from `resources/skills/*/SKILL.md`
3. no skills when neither source is present

## Retry Strategy

Retry is intentionally scoped to the model invocation boundary.
That means Arachne retries provider-mapped retryable model failures before the event loop continues, but it does not implicitly retry:

- tool execution
- tool-result handling
- structured-output validation failures

The current built-in strategy is exponential backoff with these defaults:

- `max-attempts=6`
- `initial-delay=4s`
- `max-delay=240s`

For Bedrock, Arachne currently maps throttling and temporary availability failures into retryable model exceptions.

You can enable retry from configuration:

```yaml
arachne:
  strands:
    agent:
      retry:
        enabled: true
        max-attempts: 6
        initial-delay: 4s
        max-delay: 240s
```

Or override it per agent in Java:

```java
import java.time.Duration;

import io.arachne.strands.model.retry.ExponentialBackoffRetryStrategy;

@Service
class SupportService {

  private final AgentFactory factory;

  SupportService(AgentFactory factory) {
    this.factory = factory;
  }

  String reply(String prompt) {
    return factory.builder()
        .retryStrategy(new ExponentialBackoffRetryStrategy(6, Duration.ofSeconds(4), Duration.ofSeconds(240)))
        .build()
        .run(prompt)
        .text();
  }
}
```

## Running The Agent

`Agent.run(String)` sends one user message into the event loop and returns an `AgentResult`.

```java
AgentResult result = agent.run("Describe today's weather in Tokyo in one sentence.");

String text = result.text();
List<Message> history = result.messages();
Object stopReason = result.stopReason();
```

`AgentResult` currently gives you:

- `text`: the final assistant text returned by the loop
- `messages`: the full accumulated conversation
- `stopReason`: the final model stop reason, such as `end_turn`
- `interrupts`: pending interrupt payloads when tool execution was paused before running

When `stopReason` is `interrupt`, resume the same runtime through the returned result:

```java
AgentResult result = agent.run("Continue with the reservation.");

if (result.interrupted()) {
  AgentResult resumed = result.resume(
      new InterruptResponse(result.interrupts().getFirst().id(), Map.of("approved", true)));
  String finalText = resumed.text();
}
```

Structured-output runs still require a completed invocation. If a hook interrupts a structured-output call, Arachne currently throws and asks you to resume through the string-based `AgentResult` path instead.

The `Agent` instance also exposes session-scoped state:

```java
agent.getState().put("tenant", "acme");
Object tenant = agent.getState().get("tenant");
```

When a session id is configured, both message history and `AgentState` are restored into newly built agent instances that use the same session id.

## Spring Session Backends

For clustered or Spring-managed session storage, expose a `SessionRepository<?>` bean.
Arachne keeps its own `SessionManager` abstraction for agent state, but delegates persistence to Spring Session through an adapter.

```java
@Configuration
class SessionConfiguration {

  @Bean
  SessionRepository<MapSession> arachneSessionRepository() {
    return new MapSessionRepository(new ConcurrentHashMap<>());
  }
}
```

This integration point now supports `MapSessionRepository`-style storage plus Redis-backed and JDBC-backed Spring Session repositories while preserving Arachne's explicit `sessionId` contract.

For Redis, the intended setup is:

1. add Redis connectivity through Spring Boot
2. enable a Spring Session Redis repository
3. keep using `arachne.strands.agent.session.id` or `builder().sessionId(...)` as the stable Arachne session key

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
  session:
    redis:
      namespace: arachne:sessions

arachne:
  strands:
    agent:
      session:
        id: support-session
```

```java
@SpringBootApplication
@EnableRedisIndexedHttpSession(redisNamespace = "arachne:sessions")
class SupportApplication {
}
```

This keeps session persistence in Spring Session while Arachne continues to load and save `Message` history, `AgentState`, and conversation-manager state through its own `SessionManager` abstraction.

The runnable reference for this setup is [samples/session-redis/README.md](samples/session-redis/README.md).

For JDBC, the intended setup is:

1. add JDBC connectivity through Spring Boot
2. enable a Spring Session JDBC repository
3. keep using `arachne.strands.agent.session.id` or `builder().sessionId(...)` as the stable Arachne session key

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/arachne
    username: arachne
    password: secret

arachne:
  strands:
    agent:
      session:
        id: support-session
```

```java
@SpringBootApplication
@EnableJdbcHttpSession
class SupportApplication {
}
```

This keeps session persistence in Spring Session while Arachne continues to load and save `Message` history, `AgentState`, and conversation-manager state through its own `SessionManager` abstraction.

The runnable reference for this setup is [samples/session-jdbc/README.md](samples/session-jdbc/README.md).

## Annotation-Driven Tools

Annotation-driven tools let you expose Spring bean methods directly as tools.

```java
@Service
class WeatherToolService {

  @StrandsTool(description = "Look up weather facts for a city")
  String weather(
      @ToolParam(description = "City name to research") String city,
      @ToolParam(required = false) String context) {
    return city + " is mild today.";
  }
}
```

Any Spring bean method annotated with `@StrandsTool` is discovered automatically by `AgentFactory` through auto-configuration.

That means a plain agent bean can pick up those tools without calling `builder().tools(...)` explicitly:

```java
@Service
class PlannerService {

  private final AgentFactory factory;

  PlannerService(AgentFactory factory) {
    this.factory = factory;
  }

  String reply(String prompt) {
    return factory.builder()
        .systemPrompt("Use tools when factual lookup helps.")
        .build()
        .run(prompt)
        .text();
  }
}
```

Use `@ToolParam` when you want parameter descriptions, explicit parameter names, or optional fields in the generated schema.

The generated tool schema is derived from the Java method signature, so the public API stays on the Java side instead of asking you to hand-author JSON schema.

If a tool implementation needs logical invocation metadata, you can opt into `ToolInvocationContext`.

For annotation-driven tools, declare it as a method parameter. Arachne injects it at runtime and does not expose it in the model-visible JSON schema.

```java
@Service
class WeatherToolService {

  @StrandsTool(description = "Look up weather facts for a city")
  String weather(
      @ToolParam(description = "City name to research") String city,
      ToolInvocationContext context) {
    context.state().put("lastWeatherCity", city);
    return context.toolUseId() + ": " + city + " is mild today.";
  }
}
```

For handwritten `Tool` implementations, override the context-aware overload when you need the same metadata:

```java
class AuditTool implements Tool {

  @Override
  public ToolSpec spec() {
    return new ToolSpec("audit", "Record an audit entry", JsonNodeFactory.instance.objectNode());
  }

  @Override
  public ToolResult invoke(Object input) {
    return ToolResult.success(null, "ok");
  }

  @Override
  public ToolResult invoke(Object input, ToolInvocationContext context) {
    context.state().put("lastAuditToolUseId", context.toolUseId());
    return ToolResult.success(null, "ok");
  }
}
```

`ToolInvocationContext` is intentionally narrow. It carries logical tool-call metadata and `AgentState`, but it does not carry Spring Security, MDC, tracing, transaction, or other execution-context concerns.

By default, discovered annotation tools are visible to every `AgentFactory.builder().build()` call. When you need per-agent scoping, qualify the tool and filter it at builder time:

```java
@Service
class WeatherToolService {

  @StrandsTool(description = "Planner weather lookup", qualifiers = "planner")
  String plannerWeather(String city) {
    return city + " is mild today.";
  }
}

@Service
class PlannerService {

  private final AgentFactory factory;

  PlannerService(AgentFactory factory) {
    this.factory = factory;
  }

  String plannerReply(String prompt) {
    return factory.builder()
        .toolQualifiers("planner")
        .build()
        .run(prompt)
        .text();
  }

  String internalReply(String prompt) {
    return factory.builder()
        .useDiscoveredTools(false)
        .build()
        .run(prompt)
        .text();
  }
}
```

This gives Arachne a minimal agent-scoped binding model without requiring named-agent configuration.

Spring `@Qualifier` on the tool bean class or the annotated tool method is also bridged into the same qualifier set. That means these two styles are equivalent from Arachne's point of view:

```java
@Service
@Qualifier("planner")
class WeatherToolService {

  @StrandsTool
  String weather(String city) {
    return city + " is mild today.";
  }
}
```

```java
@Service
class WeatherToolService {

  @StrandsTool(qualifiers = "planner")
  String weather(String city) {
    return city + " is mild today.";
  }
}
```

If both are present, Arachne merges them.

## Agent-As-Tool Pattern

The intended Spring idiom is to keep tool exposure and model orchestration separate.

One practical pattern is:

1. inject `AgentFactory`
2. build a specialist runtime inside a Spring `@Service`
3. annotate one service method with `@StrandsTool`
4. let another top-level agent call that tool

```java
@Service
class WeatherResearchTool {

  private final AgentFactory factory;

  WeatherResearchTool(AgentFactory factory) {
    this.factory = factory;
  }

  @StrandsTool(description = "Research a city forecast with a specialist agent")
  String lookupForecast(String city) {
    Agent researchAgent = factory.builder()
        .systemPrompt("You are a weather researcher.")
        .build();
    return researchAgent.run("Give a one-sentence forecast for " + city).text();
  }
}

@Service
class PlannerService {

  private final AgentFactory factory;

  PlannerService(AgentFactory factory) {
    this.factory = factory;
  }

  String reply(String prompt) {
    return factory.builder()
        .systemPrompt("You are a trip planner. Use tools when needed.")
        .build()
        .run(prompt)
        .text();
  }
}
```

This keeps the tool contract narrow and Spring-native:

- the outer agent sees a normal tool
- the inner specialist stays a normal `Agent`
- the coordination logic lives in a normal Spring service

The runnable version of this pattern is here:

- [samples/tool-delegation/README.md](samples/tool-delegation/README.md)

On the current main branch, that runnable sample uses named-agent defaults added later to keep the Java wiring small. The core pattern it demonstrates is the same one shown above: a `@StrandsTool`-annotated Spring service method delegates to another short-lived agent runtime, and the caller can still request structured output from the top-level agent.

## Structured Output

You can ask the final answer to be returned as a Java type.

```java
record TripPlan(String city, String forecast, String advice) {}

TripPlan plan = agent.run(
    "Plan a short Tokyo outing. Return city, forecast, and one advice sentence.",
    TripPlan.class);
```

Under the hood, Arachne exposes a final structured-output tool whose schema is generated from the requested Java type. If the model does not call that tool on its own, the event loop forces a final retry that requires the structured output tool.

The current structured-output implementation is intentionally narrow:

- output types should be simple Java records or POJOs with JSON-shaped fields
- schema generation currently focuses on strings, booleans, numbers, enums, arrays, maps, records, and simple bean getters
- Bean Validation is applied at runtime to tool invocation and structured output when you use Jakarta Validation annotations such as `@NotBlank`
- Bean Validation is not projected back into generated JSON schema
- validation failures surface as runtime errors rather than a dedicated recovery policy
- when Spring manages an `ObjectMapper`, Arachne reuses it for annotation-tool binding, structured output coercion, and Spring Session payloads

## Conversation Lifetime

`DefaultAgent` keeps conversation history in memory and appends to it on every `run(...)` call.

That means the same `Agent` instance behaves as a multi-turn conversation.

This is useful when you intentionally want memory across turns:

```java
agent.run("My name is Asuka.");
AgentResult result = agent.run("Do you remember my name?");
```

But it also means a shared `Agent` runtime will accumulate state across callers.

Two features change how you should think about that lifecycle:

- a conversation manager can trim or summarize old turns before they hit the model context window
- a configured session id can restore persisted `Message` history, `AgentState`, and conversation-manager state into newly built agent instances

Use one of these patterns when that is not acceptable:

1. create a fresh `Agent` per conversation
2. configure `builder().sessionId(...)` or `arachne.strands.agent.session.id` when you want conversation continuity across agent instances
3. scope the bean to the lifecycle you need
4. build agents in an application service instead of sharing one singleton globally

Without a configured session id, conversation state still lives only inside the current `Agent` instance.

If you want to see this behavior in a runnable application, the sample app keeps one runner-owned `Agent` runtime alive and exposes both a fixed two-turn demo and an interactive REPL:

- [samples/conversation-basics/README.md](samples/conversation-basics/README.md)

For persisted session restore examples, see these session samples:

- [samples/session-redis/README.md](samples/session-redis/README.md)
- [samples/session-jdbc/README.md](samples/session-jdbc/README.md)

## Customizing The Model

There are two supported ways to change the model source.

Use configuration only:

```yaml
arachne:
  strands:
    model:
      id: us.amazon.nova-pro-v1:0
      region: us-east-1
```

Or provide your own `Model` bean, which overrides the default Bedrock model:

```java
@Configuration
class ModelConfiguration {

    @Bean
    Model customModel() {
        return new BedrockModel("jp.amazon.nova-2-lite-v1:0", "ap-northeast-1");
    }
}
```

For ad hoc overrides, you can also set the model directly on the builder:

```java
Agent agent = agentFactory.builder()
        .model(new BedrockModel("jp.amazon.nova-2-lite-v1:0", "ap-northeast-1"))
        .build();
```

## Manual Tools And Execution Policy

The lower-level `Tool` API still exists, and `builder().tools(...)` can be mixed with discovered annotation tools when needed.

`AgentFactory.Builder` also supports execution policy selection:

```java
Agent agent = factory.builder()
  .toolExecutionMode(ToolExecutionMode.SEQUENTIAL)
  .build();
```

The default is parallel tool execution. Switch to sequential execution only when your tools depend on ordered side effects.

When you need to align tool execution with application infrastructure, Spring Boot auto-configuration exposes the parallel backend through the `arachneToolExecutionExecutor` bean. Override that bean with an `Executor` or `TaskExecutor` if you do not want the default virtual-thread executor.

If your tools depend on execution-scoped thread-local state such as security context, MDC, or tracing state, opt into `ExecutionContextPropagation` as a separate concern from `ToolInvocationContext`.

For direct Java usage, set it on the builder:

```java
ExecutionContextPropagation propagation = task -> {
  String requestId = RequestContext.currentRequestId();
  return () -> RequestContext.withRequestId(requestId, task);
};

Agent agent = factory.builder()
  .toolExecutionExecutor(appExecutor)
  .executionContextPropagation(propagation)
  .build();
```

For Spring Boot usage, register one or more `ExecutionContextPropagation` beans. Arachne composes them and applies them to submitted parallel tool tasks:

```java
@Configuration
class ToolExecutionContextConfiguration {

  @Bean
  ExecutionContextPropagation requestIdPropagation() {
    return task -> {
      String requestId = RequestContext.currentRequestId();
      return () -> RequestContext.withRequestId(requestId, task);
    };
  }
}
```

This SPI is intentionally narrow. It wraps executor-boundary task submission only, and it stays separate from `ToolInvocationContext`, which carries tool name, tool use id, raw input, and `AgentState`.

## Hooks, Plugins, And Interrupts

Arachne provides typed lifecycle hooks around invocation, model calls, tool calls, and message additions.

For direct Java usage, register hooks on the builder:

```java
Agent agent = factory.builder()
  .hooks(registrar -> registrar.beforeModelCall(event -> {
    if (event.systemPrompt() == null) {
      event.setSystemPrompt("You are a concise assistant.");
    }
  }))
  .build();
```

If you want to bundle tools and hooks together, use `Plugin`:

```java
Plugin approvalPlugin = new Plugin() {
  @Override
  public void registerHooks(HookRegistrar registrar) {
    registrar.beforeToolCall(event -> event.interrupt("approval", "Operator approval required"));
  }

  @Override
  public List<Tool> tools() {
    return List.of(new ApprovalTool());
  }
};

Agent agent = factory.builder()
  .plugins(approvalPlugin)
  .build();
```

For Spring-managed hooks, annotate the bean class with `@ArachneHook` and implement `HookProvider`:

```java
@Component
@ArachneHook
class AuditHook implements HookProvider {

  @Override
  public void registerHooks(HookRegistrar registrar) {
    registrar.afterInvocation(event -> {
      System.out.println(event.stopReason() + ": " + event.text());
    });
  }
}
```

Spring Boot also publishes observation-only lifecycle notifications as `ArachneLifecycleApplicationEvent`. These events carry immutable snapshots for listeners; subscribing to them does not change the agent control flow.

## Streaming And Steering

Arachne keeps the existing blocking `Agent.run(...)` path and adds opt-in streaming plus runtime-local steering.

For streaming, call `Agent.stream(...)` and subscribe to `AgentStreamEvent` values as they arrive:

```java
import io.arachne.strands.agent.AgentStreamEvent;

Agent agent = factory.builder().build();

AgentResult result = agent.stream("Summarize this incident", event -> {
  switch (event) {
    case AgentStreamEvent.TextDelta textDelta -> System.out.print(textDelta.delta());
    case AgentStreamEvent.ToolUseRequested toolUseRequested ->
        System.out.println("\n[tool] " + toolUseRequested.toolName());
    case AgentStreamEvent.ToolResultObserved toolResultObserved ->
        System.out.println("\n[result] " + toolResultObserved.result().status());
    case AgentStreamEvent.Retry retry ->
        System.out.println("\n[retry] " + retry.guidance());
    case AgentStreamEvent.Complete complete ->
        System.out.println("\n[done] " + complete.result().text());
  }
});
```

`Retry` is emitted when model steering discards the provisional response and asks the model to try again with added guidance. In the normal non-streaming `run(...)` path, that provisional response never reaches the caller.

Provider support is optional. Models that implement `StreamingModel` can push `ModelEvent` values as they arrive. `BedrockModel` uses Bedrock `converseStream` for this path. Models without provider streaming support still work through the same API by replaying their ordinary `converse(...)` events through the callback.

For steering, register a `SteeringHandler` on the builder. Steering handlers are plugins, so they stay runtime-local like other hook/plugin-based extensions:

```java
import io.arachne.strands.steering.Guide;
import io.arachne.strands.steering.ModelSteeringAction;
import io.arachne.strands.steering.Proceed;
import io.arachne.strands.steering.SteeringHandler;
import io.arachne.strands.steering.ToolSteeringAction;

Agent agent = factory.builder()
  .steeringHandlers(new SteeringHandler() {
    @Override
    protected ToolSteeringAction steerBeforeTool(io.arachne.strands.hooks.BeforeToolCallEvent event) {
      if ("delete_records".equals(event.toolName())) {
        return new io.arachne.strands.steering.Interrupt("Operator approval required before deletion.");
      }
      return new Proceed("allow");
    }

    @Override
    protected ModelSteeringAction steerAfterModel(io.arachne.strands.hooks.AfterModelCallEvent event) {
      if (event.response().content().isEmpty()) {
        return new Guide("Provide a concrete answer.");
      }
      return new Proceed("accept");
    }
  })
  .build();
```

The steering contract is:

- tool steering: `Proceed` allows execution, `Guide` skips the tool and returns guidance as a tool-result error payload, `Interrupt` reuses the existing interrupt/resume path
- model steering: `Proceed` accepts the response, `Guide` discards the response, appends a guidance user message, and retries the next model turn
- Spring integration: use `AgentFactory.builder().steeringHandlers(...)` for explicit opt-in; the built runtime still supports `run(...)` and `stream(...)`

## Skills

Arachne supports AgentSkills.io-style skill ingestion and delayed activation. A skill is a `SKILL.md` document with YAML frontmatter and a markdown body.

For direct Java usage, attach skills on the builder:

```java
import io.arachne.strands.skills.Skill;

Agent agent = factory.builder()
  .skills(new Skill(
      "release-checklist",
      "Use this skill when preparing a release.",
      "Run mvn test before merging and summarize the remaining risk."))
  .build();
```

If you already have a `SKILL.md` document, parse it first and reuse the parsed `Skill` object:

```java
import io.arachne.strands.skills.Skill;
import io.arachne.strands.skills.SkillParser;

SkillParser parser = new SkillParser();
Skill skill = parser.parse("""
    ---
    name: release-checklist
    description: Use this skill when preparing a release.
    ---
    Run mvn test before merging and summarize the remaining risk.
    """);

Agent agent = factory.builder()
  .skills(skill)
  .build();
```

In Spring Boot applications, packaged skills are discovered automatically from `src/main/resources/skills/<skill-name>/SKILL.md`.

Example layout:

```text
src/main/resources/
  skills/
    release-checklist/
      SKILL.md
      scripts/
        release-check.sh
      references/
        release-template.md
      assets/
        release-banner.txt
```

Example `SKILL.md`:

```md
---
name: release-checklist
description: Use this skill when preparing a release.
allowed-tools:
  - git_status
  - git_log
compatibility: java-21
---
Run mvn test before merging.
Summarize the highest remaining risk before recommending release.
```

At runtime, Arachne injects a compact available-skill catalog into the system prompt and exposes a dedicated `activate_skill` tool. The model sees only the catalog up front, then requests the full body for a specific skill by exact name when that skill becomes relevant.

The activation result returns the full skill content to the model together with any packaged `scripts/`, `references/`, and `assets/` file paths that were discovered under the skill directory. If the model needs the contents of one of those listed files, it can call `read_skill_resource` with the exact skill name and relative path. After a skill has been activated, Arachne records the loaded skill name in `AgentState` and keeps that skill active for later turns by re-injecting its instructions plus the discovered resource list into the system prompt. The immediate post-activation model turn does not re-inject the same body again, because the tool result already contains that content.

Redundant activation is also suppressed. If the model tries to activate a skill that is already active in the same conversation, Arachne short-circuits the request and returns the existing skill payload without adding another duplicate active-skill injection block.

If you want a runnable, Bedrock-free example of this flow, use:

- [samples/skill-activation/README.md](samples/skill-activation/README.md)

If you want a runnable, Bedrock-free example of the combined streaming and steering flow, use:

- [samples/streaming-steering/README.md](samples/streaming-steering/README.md)

## Current Constraints

The current implementation is intentionally narrow.

- only AWS Bedrock is supported as a built-in provider
- the loop is synchronous and blocking
- interrupt/resume currently feeds human responses back into the conversation as tool results rather than re-entering the original tool invocation automatically
- sliding-window trimming is property-driven, but summary compaction still requires an explicit `SummarizingConversationManager` on the builder
- skills currently come from builder-supplied values or classpath-discovered `SKILL.md` files; remote registries and hot reload are not implemented
- structured output currently targets simple JSON-shaped Java types rather than arbitrary object graphs
- callback-based streaming is one-way output only; bidirectional realtime/audio streaming is not implemented

Larger deferred feature areas are summarized in [docs/project-status.md](docs/project-status.md) and [docs/adr/0012-post-mvp-product-boundary.md](docs/adr/0012-post-mvp-product-boundary.md).

## Verification

Run the repository test suite with:

```bash
mvn test
```

Run the opt-in Bedrock smoke test with:

```bash
mvn -Dtest=BedrockModelIntegrationTest -Darachne.integration.bedrock=true test
```

Optional overrides for the smoke test:

- `-Darachne.integration.bedrock.region=...`
- `-Darachne.integration.bedrock.model-id=...`