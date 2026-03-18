# Arachne

Arachne is a Java port of the Strands Agents SDK with Spring Boot integration.

Phase 2 is now underway in the main branch. You can already:

- auto-configure a Bedrock-backed `Model` in Spring Boot
- create an `Agent` from `AgentFactory`
- call `agent.run("...")` and receive a text response
- expose Spring bean methods as tools with `@StrandsTool`
- auto-discover those tools from the Spring context
- request typed structured output with `agent.run("...", MyType.class)`
- set a system prompt from configuration or per agent
- keep multi-turn conversation state in a single `Agent` instance

The current user-facing guide is here:

- [docs/user-guide.md](docs/user-guide.md)

The runnable sample app is here:

- [samples/phase1-chat/README.md](samples/phase1-chat/README.md)
- [samples/phase2-tools/README.md](samples/phase2-tools/README.md)

The implementation plan and remaining work are tracked in:

- [ROADMAP.md](ROADMAP.md)

## Current Status

Phase 1 covers the synchronous Bedrock event loop. Phase 2 adds annotation-driven tools and structured output as first-class APIs.

Available now on the Phase 2 path:

- `@StrandsTool` and `@ToolParam`
- Spring bean scanning for annotated tools
- JSON schema generation from Java signatures and Java types
- structured output via `agent.run("...", MyType.class)`
- the Spring agent-as-tool pattern, where a `@Service` can expose a method as a tool and delegate to another `Agent`

## Quick Start

Assuming Arachne is on your classpath, the minimum Spring Boot setup is:

```yaml
arachne:
  strands:
    model:
      provider: bedrock
      id: jp.amazon.nova-2-lite-v1:0
      region: ap-northeast-1
```

```java
import io.arachne.strands.tool.annotation.StrandsTool;

@Configuration
class AgentConfiguration {

    @Bean
    Agent agent(AgentFactory factory) {
        return factory.builder().build();
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

```java
@Service
class ChatService {

    private final Agent agent;

    ChatService(Agent agent) {
        this.agent = agent;
    }

    String reply(String prompt) {
        return agent.run(prompt).text();
    }
}
```

Typed structured output is also available:

```java
record Summary(String city, String advice) {}

Summary summary = agent.run("Plan a short Tokyo walk", Summary.class);
```

## Build And Verify

```bash
mvn test
```

The Bedrock smoke test is opt-in:

```bash
mvn -Dtest=BedrockModelIntegrationTest -Darachne.integration.bedrock=true test
```