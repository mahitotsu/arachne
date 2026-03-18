# Arachne User Guide

This guide documents the features that are available on the current main branch.

## Scope

The current implementation gives you a synchronous, Bedrock-backed agent loop with Phase 2 tool ergonomics.

Available now:

- Spring Boot auto-configuration for `Model` and `AgentFactory`
- `AgentFactory.builder().build()` for creating an agent
- `Agent.run(String)` returning an `AgentResult`
- `@StrandsTool` and `@ToolParam` for annotation-driven tools
- automatic tool discovery from Spring beans
- structured output with typed return values through `Agent.run(String, Class<T>)`
- Bedrock model ID and region configuration
- system prompt configuration
- multi-turn conversation state inside a single `Agent` instance

Not available yet:

- hook dispatch beyond no-op callsites
- streaming responses
- session persistence and conversation managers

If you want runnable examples instead of only reading the API docs, use these samples:

- [samples/phase1-chat/README.md](samples/phase1-chat/README.md)
- [samples/phase2-tools/README.md](samples/phase2-tools/README.md)

## Prerequisites

- Java 21
- Spring Boot 3.4.x
- AWS credentials resolvable by the AWS SDK default credentials chain
- access to the configured Bedrock model in the target AWS region

If you are consuming the library from this repository before publication, install it locally first:

```bash
mvn install
```

## Spring Boot Setup

Arachne registers its auto-configuration through Spring Boot, so having the jar on the classpath is enough to make `Model` and `AgentFactory` available.

The values under `application.yml` are library-wide defaults. They do not define a single canonical agent for the whole application.

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
```

Configuration keys currently used by Phase 1:

- `arachne.strands.model.provider`
- `arachne.strands.model.id`
- `arachne.strands.model.region`
- `arachne.strands.agent.system-prompt`

Notes:

- `provider` must be `bedrock` in Phase 1
- if `id` is omitted, Arachne uses `jp.amazon.nova-2-lite-v1:0`
- if `region` is omitted, Arachne uses `ap-northeast-1`
- `agent.system-prompt` is the default prompt used when a builder does not override it
- these properties are best treated as defaults for simple applications, not as the main way to describe many agents

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
- per-agent Java configuration is where multiple-agent applications are expressed today

Builder precedence is:

1. `builder().model(...)` if you set it explicitly
2. the auto-configured `Model` bean if one exists
3. a default `BedrockModel` created from `arachne.strands.*` properties

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