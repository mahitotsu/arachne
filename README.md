# Arachne

Arachne is a Java port of the Strands Agents SDK with Spring Boot integration.

Phase 1 through Phase 3 are complete on the current main branch. You can already:

- auto-configure a Bedrock-backed `Model` in Spring Boot
- create an `Agent` from `AgentFactory`
- call `agent.run("...")` and receive a text response
- expose Spring bean methods as tools with `@StrandsTool`
- auto-discover those tools from the Spring context
- request typed structured output with `agent.run("...", MyType.class)`
- set a system prompt from configuration or per agent
- keep multi-turn conversation state in a single `Agent` instance
- bound conversation history with a sliding window manager
- summarize older conversation turns with a model-backed conversation manager
- retry retryable model calls with exponential backoff at the model boundary
- persist conversation history and agent state with in-memory, file-backed, Redis-backed, or JDBC-backed session storage
- declare named-agent defaults in `application.yml` and build them with `AgentFactory.builder("name")`

Not available yet:

- hook dispatch beyond no-op callsites
- streaming responses

The current user-facing guide is here:

- [docs/user-guide.md](docs/user-guide.md)

The runnable sample app is here:

- [samples/phase1-chat/README.md](samples/phase1-chat/README.md)
- [samples/phase2-tools/README.md](samples/phase2-tools/README.md)
- [samples/phase3-redis-session/README.md](samples/phase3-redis-session/README.md)
- [samples/phase3-jdbc-session/README.md](samples/phase3-jdbc-session/README.md)

The implementation plan and remaining work are tracked in:

- [ROADMAP.md](ROADMAP.md)

## Current Status

Phase 1 covers the synchronous Bedrock event loop. Phase 2 adds annotation-driven tools and structured output as first-class APIs. Phase 3 completes conversation management, session persistence backends, retry, and multi-agent configuration. Phase 3.5 completes the Spring integration review: the standard idiom is now factory-owned runtimes, shared application-facing `ObjectMapper` reuse, and a pluggable tool-execution backend.

Available now on the Phase 2 path:

- `@StrandsTool` and `@ToolParam`
- Spring bean scanning for annotated tools
- JSON schema generation from Java signatures and Java types
- structured output via `agent.run("...", MyType.class)`
- the Spring agent-as-tool pattern, where a `@Service` can expose a method as a tool and delegate to another `Agent`

Available now on the Phase 3 path:

- `SlidingWindowConversationManager` as the default `AgentFactory` conversation manager
- `SummarizingConversationManager` for explicit builder-based summary compaction
- opt-in model retry with exponential backoff and `MAX_ATTEMPTS=6`-style defaults
- `AgentState` for session-scoped key-value state
- `SessionManager`, `InMemorySessionManager`, and `FileSessionManager`
- Spring Session adapter for `MapSessionRepository`, Redis-backed repositories, and JDBC-backed repositories while preserving explicit Arachne session ids
- named-agent defaults under `arachne.strands.agents.<name>.*`
- dedicated configuration and conversation exceptions for Phase 3 boundaries
- `application.yml` defaults for session id, file session storage, and conversation window size
- a runnable Redis session sample backed by Docker Compose
- a runnable JDBC session sample backed by a local H2 database

## Quick Start

Assuming Arachne is on your classpath, the minimum Spring Boot setup is:

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
                id: support-demo
                file:
                    directory: .arachne/sessions
```

```java
import org.springframework.stereotype.Service;

import io.arachne.strands.spring.AgentFactory;
import io.arachne.strands.tool.annotation.StrandsTool;

@Service
class ChatService {

        private final AgentFactory factory;

        ChatService(AgentFactory factory) {
                this.factory = factory;
        }

        String reply(String prompt) {
                return factory.builder()
                                .build()
                                .run(prompt)
                                .text();
    }
}

@Service
class WeatherToolService {

    @StrandsTool(description = "Look up weather facts for a city")
    String weather(String city) {
        return "Tokyo is mild today.";
    }
}
```

If you want one in-memory multi-turn conversation inside a CLI or batch component, create the `Agent` once in that component from `AgentFactory` and keep it there rather than publishing it as a shared singleton bean.

Typed structured output is also available:

```java
record Summary(String city, String advice) {}

Summary summary = factory.builder()
    .build()
    .run("Plan a short Tokyo walk", Summary.class);
```

Session-scoped state can be seeded or read through the builder and agent API:

```java
Agent agent = factory.builder()
    .sessionId("support-123")
    .state(Map.of("tenant", "acme"))
    .build();

agent.getState().put("lastTopic", "refund");
```

Retry is available as an opt-in Phase 3 feature. The default Spring Boot properties are:

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

You can also override retry per agent in Java without changing the shared defaults:

```java
import java.time.Duration;

import io.arachne.strands.model.retry.ExponentialBackoffRetryStrategy;

Agent agent = factory.builder()
        .retryStrategy(new ExponentialBackoffRetryStrategy(6, Duration.ofSeconds(4), Duration.ofSeconds(240)))
        .build();
```

Retry is disabled unless you enable it explicitly. When enabled, it applies only to the model invocation boundary and does not retry tool execution or structured-output validation.

For multi-agent applications, define shared defaults under `arachne.strands.agent.*` and named defaults under `arachne.strands.agents.<name>.*`. Then build the agent with `factory.builder("name")` from the service, runner, or provider that owns that conversation scope.

Spring Boot auto-configuration also reuses the application `ObjectMapper` for annotation-tool binding, structured output coercion, and Spring Session payloads. Parallel tool execution is still the default, but the backend is no longer fixed: you can override the `arachneToolExecutionExecutor` bean if your application needs a different `Executor` / `TaskExecutor`.

If you need LLM-backed compaction instead of a fixed sliding window, pass `SummarizingConversationManager` explicitly through the builder:

```java
import io.arachne.strands.agent.conversation.SummarizingConversationManager;

Agent agent = factory.builder()
    .conversationManager(new SummarizingConversationManager(model, 40, 12))
    .build();
```

This keeps summarization in the conversation-management layer. It does not route through tool execution or structured-output handling.

If you want a Spring-managed shared backend, provide a `SessionRepository<?>` bean and Arachne will wrap it with its `SessionManager` adapter. Redis-backed and JDBC-backed Spring Session repositories are supported on the current branch.

If you configure `arachne.strands.agent.session.file.directory`, Arachne uses file-backed persistence explicitly. Without that file setting, it falls back to in-memory session storage unless a Spring Session repository bean is present.

## Build And Verify

```bash
mvn test
```

The Bedrock smoke test is opt-in:

```bash
mvn -Dtest=BedrockModelIntegrationTest -Darachne.integration.bedrock=true test
```