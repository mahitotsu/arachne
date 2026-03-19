# Phase 3 Redis Session Sample

This sample is a runnable Redis-backed session demo for Phase 3.

It is intentionally Bedrock-free so you can verify session restore behavior without any AWS dependency.

What it demonstrates:

- Spring Session Redis is the persistence backend
- Arachne still uses its own explicit `sessionId`
- message history and `AgentState` survive application restarts
- Redis is started with `docker compose`

## Prerequisites

- Java 21
- Maven
- Docker with Docker Compose

The sample depends on the local snapshot of this repository, so install the root project first:

```bash
mvn install
```

## Start Redis

```bash
cd samples/phase3-redis-session
docker compose up -d
```

## Run The Demo

```bash
cd samples/phase3-redis-session
mvn spring-boot:run
```

Run the same command again.

The second run should show larger restore counters because the previous execution persisted state into Redis.

Expected output shape:

```text
Arachne Phase 3 Redis session sample
sessionId> phase3-redis-demo
restored.messages.before> 0
restored.runCount.before> 0
prompt> Remember that my destination is Kyoto.
reply> Turn 1 stored for prompt: Remember that my destination is Kyoto.
persisted.messages.after> 2
persisted.runCount.after> 1
next> Run the same command again to see Redis-backed restore.
```

On the next run, `restored.messages.before` and `restored.runCount.before` should both be greater than zero.

You can override the prompt if you want:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--prompt="Remember that my destination is Sapporo."
```

## Stop Redis

```bash
docker compose down
```

## Configuration

The sample uses Spring Boot for Redis connection properties and Spring Session for the repository bean.
The fixed Arachne session id is configured in `src/main/resources/application.yml`.

Because the model is a stub, the demo is deterministic and safe to rerun as many times as you want.