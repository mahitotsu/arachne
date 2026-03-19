# Arachne User Guide

This guide documents the features that are available on the current main branch.

## Scope

The current implementation gives you a synchronous, Bedrock-backed agent loop with Phase 2 tool ergonomics.

Available now:

- Spring Boot auto-configuration for `Model` and `AgentFactory`
- `AgentFactory.builder().build()` for creating an agent
- `AgentFactory.builder("name").build()` for creating an agent from named defaults
- `Agent.run(String)` returning an `AgentResult`
- opt-in retry strategy for retryable model failures
- `@StrandsTool` and `@ToolParam` for annotation-driven tools
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

Not available yet:

- hook dispatch beyond no-op callsites
- streaming responses

If you want runnable examples instead of only reading the API docs, use these samples:

- [samples/phase1-chat/README.md](samples/phase1-chat/README.md)
- [samples/phase2-tools/README.md](samples/phase2-tools/README.md)
- [samples/phase3-redis-session/README.md](samples/phase3-redis-session/README.md)

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

- `provider` must be `bedrock` in Phase 1
- if `id` is omitted, Arachne uses `jp.amazon.nova-2-lite-v1:0`
- if `region` is omitted, Arachne uses `ap-northeast-1`
- `agent.system-prompt` is the default prompt used when a builder does not override it
- `agent.retry.*` enables exponential-backoff retry at the model invocation boundary
- retry is disabled by default so unconfigured `agent.run("...")` keeps the prior failure behavior
- the default retry values match the current Phase 3 target: `max-attempts=6`, `initial-delay=4s`, `max-delay=240s`
- retry applies only to provider-mapped retryable model failures; it does not retry tool execution or structured-output validation
- `agent.conversation.window-size` is used by the default `SlidingWindowConversationManager`
- `agent.session.id` enables automatic restore/persist across agents created by the builder
- `agent.session.file.directory` switches the default session storage from in-memory to JSON files
- without `agent.session.file.directory`, Arachne uses a Spring Session-backed in-memory repository by default
- when a Spring Session `SessionRepository<?>` bean is present, Arachne uses it instead of the in-memory fallback
- if both a file directory and a Spring Session repository are configured, file storage wins explicitly
- named-agent properties are override-only and do not replace the shared defaults unless you build that named agent explicitly
- these properties are best treated as shared defaults, while `arachne.strands.agents.<name>.*` describes multi-agent applications
- `SummarizingConversationManager` is currently an explicit builder-level feature, not a property-bound default

## Creating An Agent

The normal Spring idiom is to define an `Agent` bean from `AgentFactory`.

```java
@Configuration
class AgentConfiguration {

    @Bean
    Agent supportAgent(AgentFactory factory) {
        return factory.builder()
                .build();
    }
}
```

You can also override the system prompt per agent:

```java
@Bean
Agent supportAgent(AgentFactory factory) {
    return factory.builder()
            .systemPrompt("You answer in short Japanese sentences.")
            .build();
}
```

You can define multiple agents in the same application. Each one can override the shared defaults from `application.yml`.

```java
@Configuration
class MultiAgentConfiguration {

  @Bean
  Agent supportAgent(AgentFactory factory) {
    return factory.builder()
        .systemPrompt("You are a customer support agent.")
        .build();
  }

  @Bean
  Agent analystAgent(AgentFactory factory) {
    return factory.builder()
        .model(new BedrockModel("us.amazon.nova-pro-v1:0", "us-east-1"))
        .systemPrompt("You are an analyst. Answer with concise bullet points.")
        .build();
  }
}
```

So the current model is:

- `application.yml` supplies defaults
- `AgentFactory.builder()` defines each agent instance
- `AgentFactory.builder("name")` applies named defaults and still allows explicit builder overrides
- per-agent Java configuration remains the Spring bean definition point

Named-agent Spring wiring typically looks like this:

```java
@Configuration
class MultiAgentConfiguration {

  @Bean
  @Qualifier("support")
  Agent supportAgent(AgentFactory factory) {
    return factory.builder("support").build();
  }

  @Bean
  @Qualifier("analyst")
  Agent analystAgent(AgentFactory factory) {
    return factory.builder("analyst").build();
  }
}
```

Builder precedence is:

1. `builder().model(...)` if you set it explicitly
2. the auto-configured `Model` bean if one exists
3. a default `BedrockModel` created from `arachne.strands.*` properties

Named builder precedence is:

1. `builder("name").model(...)` if you set it explicitly
2. `arachne.strands.agents.<name>.model.*` if present
3. the auto-configured shared `Model` bean if one exists
4. the shared `arachne.strands.model.*` defaults

For Phase 3 foundations, the current builder defaults are:

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

@Bean
Agent supportAgent(AgentFactory factory, Model model) {
  return factory.builder()
      .conversationManager(new SummarizingConversationManager(model, 40, 12))
      .build();
}
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

## Retry Strategy

Phase 3 retry is intentionally scoped to the model invocation boundary.
That means Arachne retries provider-mapped retryable model failures before the event loop continues, but it does not implicitly retry:

- tool execution
- tool-result handling
- structured-output validation failures

The current built-in strategy is exponential backoff with Phase 3 defaults equivalent to the Python SDK target:

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

@Bean
Agent supportAgent(AgentFactory factory) {
  return factory.builder()
      .retryStrategy(new ExponentialBackoffRetryStrategy(6, Duration.ofSeconds(4), Duration.ofSeconds(240)))
      .build();
}
```

## Running The Agent

`Agent.run(String)` sends one user message into the event loop and returns an `AgentResult`.

```java
AgentResult result = agent.run("東京の天気を一言で説明して");

String text = result.text();
List<Message> history = result.messages();
Object stopReason = result.stopReason();
```

`AgentResult` currently gives you:

- `text`: the final assistant text returned by the loop
- `messages`: the full accumulated conversation
- `stopReason`: the final model stop reason, such as `end_turn`

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

The runnable reference for this setup is [samples/phase3-redis-session/README.md](samples/phase3-redis-session/README.md).

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

The runnable reference for this setup is [samples/phase3-jdbc-session/README.md](samples/phase3-jdbc-session/README.md).

## Annotation-Driven Tools

Phase 2 lets you expose Spring bean methods directly as tools.

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
@Bean
Agent plannerAgent(AgentFactory factory) {
  return factory.builder()
      .systemPrompt("Use tools when factual lookup helps.")
      .build();
}
```

Use `@ToolParam` when you want parameter descriptions, explicit parameter names, or optional fields in the generated schema.

The generated tool schema is derived from the Java method signature, so Phase 2 keeps the public API on the Java side instead of asking you to hand-author JSON schema.

By default, discovered annotation tools are visible to every `AgentFactory.builder().build()` call. When you need per-agent scoping, qualify the tool and filter it at builder time:

```java
@Service
class WeatherToolService {

  @StrandsTool(description = "Planner weather lookup", qualifiers = "planner")
  String plannerWeather(String city) {
    return city + " is mild today.";
  }
}

@Bean
Agent plannerAgent(AgentFactory factory) {
  return factory.builder()
      .toolQualifiers("planner")
      .build();
}

@Bean
Agent internalAgent(AgentFactory factory) {
  return factory.builder()
      .useDiscoveredTools(false)
      .build();
}
```

This gives Phase 2 a minimal agent-scoped binding model without introducing Phase 3 named-agent configuration early.

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

1. build a specialist `Agent`
2. inject that agent into a Spring `@Service`
3. annotate one service method with `@StrandsTool`
4. let another top-level agent call that tool

```java
@Bean
Agent researchAgent(AgentFactory factory) {
  return factory.builder()
      .systemPrompt("You are a weather researcher.")
      .build();
}

@Service
class WeatherResearchTool {

  private final Agent researchAgent;

  WeatherResearchTool(Agent researchAgent) {
    this.researchAgent = researchAgent;
  }

  @StrandsTool(description = "Research a city forecast with a specialist agent")
  String lookupForecast(String city) {
    return researchAgent.run("Give a one-sentence forecast for " + city).text();
  }
}

@Bean
Agent plannerAgent(AgentFactory factory) {
  return factory.builder()
      .systemPrompt("You are a trip planner. Use tools when needed.")
      .build();
}
```

This keeps the tool contract narrow and Spring-native:

- the outer agent sees a normal tool
- the inner specialist stays a normal `Agent`
- the coordination logic lives in a normal Spring service

The runnable version of this pattern is here:

- [samples/phase2-tools/README.md](samples/phase2-tools/README.md)

## Structured Output

You can ask the final answer to be returned as a Java type.

```java
record TripPlan(String city, String forecast, String advice) {}

TripPlan plan = agent.run(
    "Plan a short Tokyo outing. Return city, forecast, and one advice sentence.",
    TripPlan.class);
```

Under the hood, Arachne exposes a final structured-output tool whose schema is generated from the requested Java type. If the model does not call that tool on its own, the event loop forces a final retry that requires the structured output tool.

The current Phase 2 implementation is intentionally narrow:

- output types should be simple Java records or POJOs with JSON-shaped fields
- schema generation currently focuses on strings, booleans, numbers, enums, arrays, maps, records, and simple bean getters
- Bean Validation is applied at runtime to tool invocation and structured output when you use Jakarta Validation annotations such as `@NotBlank`
- Bean Validation is not projected back into generated JSON schema in Phase 2
- validation failures surface as runtime errors rather than a dedicated recovery policy

## Conversation Lifetime

`DefaultAgent` keeps conversation history in memory and appends to it on every `run(...)` call.

That means the same `Agent` instance behaves as a multi-turn conversation.

This is useful when you intentionally want memory across turns:

```java
agent.run("私の名前は明日香です");
AgentResult result = agent.run("私の名前を覚えていますか");
```

But it also means a singleton Spring bean will accumulate shared state across callers.

Use one of these patterns when that is not acceptable:

1. create a fresh `Agent` per conversation
2. scope the bean to the lifecycle you need
3. build agents in an application service instead of sharing one singleton globally

Phase 3 is where dedicated session management is planned.

If you want to see this behavior in a runnable application, the sample app keeps one `Agent` bean alive and exposes both a fixed two-turn demo and an interactive REPL:

- [samples/phase1-chat/README.md](samples/phase1-chat/README.md)

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

## Current Constraints

The current implementation is intentionally narrow.

- only AWS Bedrock is supported as a built-in provider
- the loop is synchronous and blocking
- responses are non-streaming
- hook methods exist but dispatch is still no-op
- there is no built-in session store or conversation trimming
- structured output currently targets simple JSON-shaped Java types rather than arbitrary object graphs

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