# Arachne

Arachne is a Java port of the Strands Agents SDK with Spring Boot integration.

Phase 1 through Phase 5 are complete on the current main branch. 現時点で次を利用できます:

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
- register runtime hooks and plugins through `AgentFactory.Builder`
- auto-discover Spring hook beans with `@ArachneHook`
- observe hook activity through the Spring `ApplicationEvent` bridge
- pause tool execution with interrupts and continue through `AgentResult.resume(...)`
- parse AgentSkills.io-style `SKILL.md` files into runtime skills
- attach skills per runtime with `AgentFactory.builder().skills(...)`
- auto-discover classpath skills from `src/main/resources/skills/*/SKILL.md`
- expose a dedicated `activate_skill` tool with a compact available-skill catalog
- delay-load full skill instructions and keep loaded skills active without duplicate injection

Not available yet:

- streaming responses

The current user-facing guide is here:

- [docs/user-guide.md](docs/user-guide.md)

The runnable sample app is here:

- [samples/phase1-chat/README.md](samples/phase1-chat/README.md)
- [samples/phase2-tools/README.md](samples/phase2-tools/README.md)
- [samples/phase3-redis-session/README.md](samples/phase3-redis-session/README.md)
- [samples/phase3-jdbc-session/README.md](samples/phase3-jdbc-session/README.md)
- [samples/phase4-hooks-interrupts/README.md](samples/phase4-hooks-interrupts/README.md)
- [samples/phase5-skills/README.md](samples/phase5-skills/README.md)

The implementation plan and remaining work are tracked in:

- [ROADMAP.md](ROADMAP.md)

Contributor workflow helpers for roadmap phases:

- `/phase-audit <phase>` checks whether a phase is actually ready to close, with explicit findings for roadmap gaps, stale docs, missing ADR work, sample drift, instruction drift, and regression risk.
- `/phase-closeout <phase>` runs the same repository-specific checklist, makes the required updates when they are clearly supported by the current repo state, and finishes with a completion report.

Quality evaluation workflow:

- `/quality-audit` runs the quality Maven profiles, refreshes the artifacts, and then produces a Japanese quality evaluation report from the fresh repository evidence.
- `.github/dependabot.yml` keeps repository-side dependency updates and advisory-backed remediation visible without making the local Maven loop heavy.

## Current Status

Phase 1 covers the synchronous Bedrock event loop. Phase 2 adds annotation-driven tools and structured output as first-class APIs. Phase 3 completes conversation management, session persistence backends, retry, and multi-agent configuration. Phase 3.5 completes the Spring integration review: the standard idiom is now factory-owned runtimes, shared application-facing `ObjectMapper` reuse, and a pluggable tool-execution backend. Phase 4 completes typed hook dispatch, plugin bundling, Spring hook discovery, the observation-only Spring event bridge, and interrupt/resume control flow before tool execution. Phase 5 completes AgentSkills.io-style skills with classpath discovery, compact catalog injection, dedicated delayed activation, and loaded-skill context management on top of the Phase 4 plugin boundary.

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

Available now on the Phase 4 path:

- typed lifecycle hook events for invocation, model calls, tool calls, and message additions
- runtime hook registration with `HookProvider` and `builder().hooks(...)`
- tool-and-hook bundling with `Plugin` and `builder().plugins(...)`
- Spring hook auto-discovery with `@ArachneHook`
- observation-only lifecycle publication through Spring `ApplicationEvent`
- `AgentResult.interrupts()` and `AgentResult.resume(...)` for human-in-the-loop pauses before tool execution

Available now on the Phase 5 path:

- `Skill` and `SkillParser` for AgentSkills.io-style `SKILL.md` documents
- `AgentSkillsPlugin` with compact available-skill catalog injection
- dedicated `activate_skill` tool for delayed loading of full instructions
- loaded-skill tracking in `AgentState`, including duplicate-load suppression across the conversation
- `AgentFactory.builder().skills(...)` for runtime-local skill attachment
- Spring classpath discovery from `src/main/resources/skills/*/SKILL.md`

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

Phase 5 skills are also available on the current branch. You can attach parsed skills directly per runtime:

```java
import io.arachne.strands.skills.Skill;

Agent agent = factory.builder()
    .skills(new Skill(
        "release-checklist",
        "Use this skill when preparing a release.",
        "Run mvn test before merging and summarize the risk before shipping."))
    .build();
```

For Spring Boot discovery, place AgentSkills.io-style files under `src/main/resources/skills/<skill-name>/SKILL.md`. Arachne loads them automatically, exposes a compact skill catalog to the model, and lets the model load only the relevant skill body through the dedicated `activate_skill` tool.

Loaded skill names are tracked in `AgentState`, so once a skill has been activated it stays active for later turns in that conversation. Arachne also avoids re-injecting the same skill body twice in the same prompt and skips redundant re-loading for already active skills.

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