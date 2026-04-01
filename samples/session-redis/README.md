# Redis Session Sample

This sample is a runnable Redis-backed session restore demo.

It is intentionally Bedrock-free so you can verify session restore behavior without any AWS dependency.

What it demonstrates:

- Spring Session Redis is the persistence backend
- Arachne still uses its own explicit `sessionId`
- each application run creates a fresh `Agent` runtime from `AgentFactory`
- message history and `AgentState` survive application restarts
- Redis is started with `docker compose`

## Prerequisites

- Java 25
- Maven
- Docker with Docker Compose

The sample depends on the local `com.mahitotsu.arachne:arachne` snapshot, so install the library module first. The sample itself now uses the `com.mahitotsu.arachne.samples` namespace until the core library coordinates are migrated in a later step:

```bash
mvn -pl arachne -am install
```

## Start Redis

```bash
cd samples/session-redis
docker compose up -d
```

## Run The Demo

```bash
cd samples/session-redis
mvn spring-boot:run
```

Run the same command again.

The second run should show larger restore counters because the previous execution persisted state into Redis.

Expected output shape:

```text
Arachne Redis session sample
sessionId> session-redis-demo
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
The runner builds a fresh `Agent` runtime from `AgentFactory`, and the fixed Arachne session id in `src/main/resources/application.yml` drives restore across launches.

Because the model is a stub, the demo is deterministic and safe to rerun as many times as you want.