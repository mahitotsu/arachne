# Phase 3 JDBC Session Sample

This sample is a runnable JDBC-backed session demo for Phase 3.

It is intentionally Bedrock-free so you can verify session restore behavior without any AWS dependency.

What it demonstrates:

- Spring Session JDBC is the persistence backend
- Arachne still uses its own explicit `sessionId`
- message history and `AgentState` survive application restarts
- H2 is used as a local file-backed database, so no Docker is required

## Prerequisites

- Java 21
- Maven

The sample depends on the local snapshot of this repository, so install the root project first:

```bash
mvn install
```

## Run The Demo

```bash
cd samples/phase3-jdbc-session
mvn spring-boot:run
```

Run the same command again.

The second run should show larger restore counters because the previous execution persisted state into the local JDBC database.

Expected output shape:

```text
Arachne Phase 3 JDBC session sample
sessionId> phase3-jdbc-demo
restored.messages.before> 0
restored.runCount.before> 0
prompt> Remember that my destination is Kyoto.
reply> Turn 1 stored for prompt: Remember that my destination is Kyoto.
persisted.messages.after> 2
persisted.runCount.after> 1
next> Run the same command again to see JDBC-backed restore.
```

On the next run, `restored.messages.before` and `restored.runCount.before` should both be greater than zero.

You can override the prompt if you want:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--prompt="Remember that my destination is Sapporo."
```

## Reset The Local Database

```bash
rm -rf .arachne/jdbc
```

## Configuration

The sample uses Spring Boot JDBC configuration and Spring Session JDBC.
The fixed Arachne session id is configured in `src/main/resources/application.yml`.

Because the model is a stub, the demo is deterministic and safe to rerun as many times as you want.