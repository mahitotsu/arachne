# Conversation Basics Sample

This sample shows the smallest intended pattern for an in-memory multi-turn conversation on the current main branch.

It is here to make three usage rules concrete:

- Spring Boot auto-configures `AgentFactory`
- one runner-owned `Agent` runtime is reused across turns
- multi-turn memory lives in that runtime and does not require publishing `Agent` as a shared Spring bean

It also includes an optional Bedrock system-prompt caching demo path that prints the usage metrics added in the current branch.

## Prerequisites

- Java 21
- Maven
- valid AWS credentials for Bedrock
- access to the configured model in the configured region

The sample depends on the local snapshot of this repository, so install the root project first:

```bash
mvn install
```

## Run The Demo Conversation

```bash
cd samples/conversation-basics
mvn spring-boot:run -Dspring-boot.run.arguments=--demo
```

The demo sends two prompts through the same runner-owned `Agent` instance:

1. it stores a fact in the conversation
2. it asks about that fact on the next turn

The output also prints `messageCount`, so you can see the in-memory conversation history grow after each turn.

Each turn also prints the accumulated usage counters from `AgentResult.metrics().usage()`:

- `metrics.inputTokens`
- `metrics.outputTokens`
- `metrics.cacheReadInputTokens`
- `metrics.cacheWriteInputTokens`

## Run The Bedrock Cache Demo

```bash
cd samples/conversation-basics
mvn spring-boot:run -Dspring-boot.run.arguments=--cache-demo
```

This mode keeps the same runner-owned `Agent` pattern, but overrides the system prompt with a long static prompt so Bedrock prompt caching has a realistic chance to activate. The sample also exposes one stable reference tool so the request shape stays stable across turns.

What to look for:

1. the first turn usually reports non-zero `metrics.cacheWriteInputTokens`
2. the second turn usually reports non-zero `metrics.cacheReadInputTokens`
3. `messageCount` still grows because the same in-memory `Agent` runtime is reused across turns

If the cache counters stay zero, the selected Bedrock model or region may not support prompt caching, or the provider may not treat the current prompt shape as cacheable.

## Run The Opt-In Bedrock Smoke Test

```bash
cd samples/conversation-basics
mvn -Dtest=ConversationBasicsBedrockIntegrationTest -Darachne.integration.bedrock=true test
```

This test disables the interactive runner, creates the sample agent through `AgentFactory`, and proves that a fact stored on turn 1 is recalled on turn 2 against live Bedrock.

## Run The Interactive REPL

```bash
cd samples/conversation-basics
mvn spring-boot:run
```

Available commands:

- `:history` shows the current in-memory message count
- `:quit` exits the application

Example interaction:

```text
you> My name is Asuka. Please remember it.
agent> Understood. Your name is Asuka.

you> What is my name?
agent> Your name is Asuka.
```

## What To Look For

- `ConversationBasicsRunner`: creates one `Agent` from `AgentFactory` and keeps it inside the runner
- `ConversationBasicsRunner`: creates one `Agent` from `AgentFactory`, supports the default conversation demo, and has a dedicated cache-demo path that prints usage metrics
- `SampleReferenceTool`: provides a stable sample tool definition so the cache demo keeps a repeatable request shape while the system prompt remains the cache target
- `ConversationBasicsApplication`: minimal Spring Boot entrypoint with no extra wiring
- `application.yml`: shared model defaults plus opt-in Bedrock cache settings, keeping the runtime lifecycle decision in Java

This is the reference pattern for CLI tools, batch runners, and other single-owner conversation flows. If you want restore across restarts or shared persistence, use one of the session samples instead.

## Configuration

The sample ships with this default configuration in `src/main/resources/application.yml`:

```yaml
arachne:
  strands:
    model:
      provider: bedrock
      id: jp.amazon.nova-2-lite-v1:0
      region: ap-northeast-1
      bedrock:
        cache:
          system-prompt: true
          tools: false
```

Override any of those values with standard Spring Boot configuration mechanisms if needed.