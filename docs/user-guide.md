# Arachne User Guide

This guide documents the features that are actually available after Phase 1.

## Scope

Phase 1 gives you a synchronous, Bedrock-backed agent loop that you can wire into a Spring Boot application.

Available now:

- Spring Boot auto-configuration for `Model` and `AgentFactory`
- `AgentFactory.builder().build()` for creating an agent
- `Agent.run(String)` returning an `AgentResult`
- Bedrock model ID and region configuration
- system prompt configuration
- multi-turn conversation state inside a single `Agent` instance

Not available yet:

- annotation-driven tools with `@StrandsTool`
- automatic tool discovery from Spring beans
- structured output with typed return values
- hook dispatch beyond no-op callsites
- streaming responses
- session persistence and conversation managers

If you want to confirm the current multi-turn behavior in a real app instead of only reading the API docs, use the sample here:

- [samples/phase1-chat/README.md](samples/phase1-chat/README.md)

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

## Manual Tools: What Exists Today

The core event loop already knows how to pass tool specs to the model, execute requested tools, and feed tool results back into the next model turn.

You can pass programmatic `Tool` implementations through `builder().tools(...)` today.

This is still lower-level than the intended Phase 2 API. The missing pieces are what make tools ergonomic for normal Spring users:

- `@StrandsTool`
- schema generation from Java types
- Spring bean scanning
- structured output integration

For that reason, Phase 1 documentation treats manual tools as an advanced, provisional API rather than the main path.

## Current Constraints

The current implementation is intentionally narrow.

- only AWS Bedrock is supported as a built-in provider
- the loop is synchronous and blocking
- responses are non-streaming
- hook methods exist but dispatch is still no-op
- there is no built-in session store or conversation trimming
- there is no typed structured output API yet

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