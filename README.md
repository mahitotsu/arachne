# Arachne

Arachne is a Java port of the Strands Agents SDK with Spring Boot integration.

Arachne ships a Bedrock-backed agent runtime with Spring Boot integration, annotation-driven tools, structured output, retry, conversation/session management, hooks/plugins, interrupts, skills, and opt-in streaming plus steering.

## Documentation

- [docs/user-guide.md](docs/user-guide.md) for user-facing API, configuration, lifecycle, and examples
- [docs/project-status.md](docs/project-status.md) for the shipped scope, current constraints, and deliberately deferred features
- [docs/adr/README.md](docs/adr/README.md) for accepted design decisions and future ADR candidates

Runnable samples:

- [samples/phase1-chat/README.md](samples/phase1-chat/README.md)
- [samples/phase2-tools/README.md](samples/phase2-tools/README.md)
- [samples/phase3-redis-session/README.md](samples/phase3-redis-session/README.md)
- [samples/phase3-jdbc-session/README.md](samples/phase3-jdbc-session/README.md)
- [samples/phase4-hooks-interrupts/README.md](samples/phase4-hooks-interrupts/README.md)
- [samples/phase5-skills/README.md](samples/phase5-skills/README.md)
- [samples/phase6-streaming-steering/README.md](samples/phase6-streaming-steering/README.md)

Deliberately deferred features include provider expansion beyond Bedrock, bidirectional realtime/audio streaming, MCP, multi-agent protocols, Guardrails, Agent Config, Evals SDK, and remote skill registries. The current deferral boundary is documented in [docs/project-status.md](docs/project-status.md) and [docs/adr/0012-post-mvp-product-boundary.md](docs/adr/0012-post-mvp-product-boundary.md).

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

Retry is available as an opt-in feature. The default Spring Boot properties are:

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

Skills are also available on the current branch. You can attach parsed skills directly per runtime:

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

Streaming is also available on the current branch. The simplest direct-Java usage is:

```java
import io.arachne.strands.agent.AgentStreamEvent;

Agent agent = factory.builder().build();

AgentResult result = agent.stream("Plan a day trip to Kyoto", event -> {
    if (event instanceof AgentStreamEvent.TextDelta textDelta) {
        System.out.print(textDelta.delta());
    }
});
```

For runtime-local steering, attach a `SteeringHandler` on the builder:

```java
import io.arachne.strands.steering.Guide;
import io.arachne.strands.steering.SteeringHandler;
import io.arachne.strands.steering.ToolSteeringAction;

Agent agent = factory.builder()
    .steeringHandlers(new SteeringHandler() {
        @Override
        protected ToolSteeringAction steerBeforeTool(io.arachne.strands.hooks.BeforeToolCallEvent event) {
            if ("dangerousTool".equals(event.toolName())) {
                return new Guide("Use the safer cached path instead.");
            }
            return new io.arachne.strands.steering.Proceed("allow");
        }
    })
    .build();
```

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

## Contributor Workflows

- `/phase-audit <area>` audits a capability area or historical phase for documentation drift, ADR gaps, sample drift, and regression risk.
- `/phase-closeout <area>` runs the same checklist and applies no-regret updates when the repository state clearly supports them.
- `/quality-audit` refreshes the Maven quality artifacts and produces a Japanese evidence-based report.
- `.github/dependabot.yml` keeps repository-side dependency updates and advisory-backed remediation visible without making the local Maven loop heavy.

## Build And Verify

```bash
mvn test
```

The Bedrock smoke test is opt-in:

```bash
mvn -Dtest=BedrockModelIntegrationTest -Darachne.integration.bedrock=true test
```