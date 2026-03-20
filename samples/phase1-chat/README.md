# Phase 1 Chat Sample

This is a minimal Spring Boot application that demonstrates the Phase 1-style chat loop on the current main branch.

It exists to make the current implementation concrete:

- the app wires `AgentFactory` through Spring Boot auto-configuration
- one `Agent` bean is reused across turns
- you can confirm multi-turn memory with either a fixed demo or an interactive REPL

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
cd samples/phase1-chat
mvn spring-boot:run -Dspring-boot.run.arguments=--demo
```

The demo sends two prompts through the same `Agent` instance:

1. it stores a fact in the conversation
2. it asks about that fact on the next turn

The output also prints `messageCount`, so you can see the conversation history grow after each turn.

## Run The Interactive Chat

```bash
cd samples/phase1-chat
mvn spring-boot:run
```

Available commands:

- `:history` shows the current in-memory message count
- `:quit` exits the application

Example interaction:

```text
you> 私の名前は明日香です。覚えてください。
agent> 承知しました。明日香さんですね。

you> 私の名前は何ですか？
agent> 明日香です。
```

Because this sample keeps a single `Agent` bean alive, it demonstrates the baseline in-memory multi-turn behavior directly.

If you want restore across restarts or external session storage, use the Phase 3 session samples instead.

## Configuration

The sample ships with this default configuration in `src/main/resources/application.yml`:

```yaml
arachne:
  strands:
    model:
      provider: bedrock
      id: jp.amazon.nova-2-lite-v1:0
      region: ap-northeast-1
```

Override any of those values with standard Spring Boot configuration mechanisms if needed.