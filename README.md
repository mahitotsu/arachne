# Arachne

Arachne is a Java port of the Strands Agents SDK with Spring Boot integration.

Arachne ships a Bedrock-backed agent runtime with Spring Boot integration, annotation-driven tools, structured output, retry, conversation/session management, hooks/plugins, interrupts, skills, opt-in Bedrock system/tool prompt caching, and opt-in streaming plus steering.

## Documentation

- [docs/README.md](docs/README.md) for the documentation catalog and reading order
- [docs/user-guide.md](docs/user-guide.md) for user-facing API, configuration, lifecycle, and examples
- [docs/project-status.md](docs/project-status.md) for the shipped scope, current constraints, and deliberately deferred features
- [docs/repository-facts.md](docs/repository-facts.md) for a repository-wide engineering snapshot with quantitative metrics, structure, and architectural hotspots
- [docs/tool-catalog.md](docs/tool-catalog.md) for the current tool authoring surface and the proposed first-party tool catalog direction
- [docs/adr/README.md](docs/adr/README.md) for accepted design decisions and future ADR candidates

Runnable samples:

- [samples/README.md](samples/README.md) for the sample catalog and learning tracks
- [samples/secure-downstream-tools/README.md](samples/secure-downstream-tools/README.md) for secure backend tool patterns
- [samples/stateful-backend-operations/README.md](samples/stateful-backend-operations/README.md) for idempotent backend mutations and state
- [samples/domain-separation/README.md](samples/domain-separation/README.md) for the composed backend reference

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
            bedrock:
                cache:
                    system-prompt: true
                    tools: true
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

Bedrock prompt caching is also opt-in. `arachne.strands.model.bedrock.cache.system-prompt=true` adds a Bedrock cache point after the static system prompt, and `arachne.strands.model.bedrock.cache.tools=true` adds a cache point after the emitted tool definitions. Cache usage is exposed on `AgentResult.metrics().usage()` via `cacheReadInputTokens()` and `cacheWriteInputTokens()`.

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

If a packaged skill also includes optional `scripts/`, `references/`, or `assets/` subdirectories, Arachne lists those relative resource paths in the activation payload and in the later active-skill prompt block so the model can decide when to read or use them. The dedicated `read_skill_resource` tool then reads the contents of one listed resource on demand by exact skill name and relative path.

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

This streaming path is callback-based and one-way output only. If model steering later discards a provisional streamed response and retries with guidance, subscribers receive `AgentStreamEvent.Retry` to mark that boundary.

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

- `/compat-audit <area>` audits Arachne's compatibility with Strands Agents for a target capability area and reports implemented, partial, deferred, and MVP-missing items.
- `/implementation-candidates <scope>` detects unported or deferred Strands capabilities that are plausible next implementation candidates for Arachne and compares their priority.
- `/alignment-audit <area>` audits whether Arachne's implementation, tests, docs, ADRs, instructions, and samples stay aligned for a target capability area.
- `/quality-audit` refreshes the Maven quality artifacts and produces a Japanese evidence-based quality report for the current repository state.
- `.github/dependabot.yml` keeps repository-side dependency updates and advisory-backed remediation visible without making the local Maven loop heavy.

## Build And Verify

```bash
mvn test
```

The Bedrock smoke test is opt-in:

```bash
mvn -Dtest=BedrockModelIntegrationTest -Darachne.integration.bedrock=true test
```
