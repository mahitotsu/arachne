# Arachne

Arachne is a Java port of the Strands Agents SDK with Spring Boot integration.

Phase 1 is complete. You can already:

- auto-configure a Bedrock-backed `Model` in Spring Boot
- create an `Agent` from `AgentFactory`
- call `agent.run("...")` and receive a text response
- set a system prompt from configuration or per agent
- keep multi-turn conversation state in a single `Agent` instance

The current user-facing guide is here:

- [docs/user-guide.md](docs/user-guide.md)

The runnable sample app is here:

- [samples/phase1-chat/README.md](samples/phase1-chat/README.md)

The implementation plan and remaining work are tracked in:

- [ROADMAP.md](ROADMAP.md)

## Current Status

Phase 1 covers the synchronous Bedrock event loop. Phase 2 is where annotation-driven tools and structured output become first-class APIs.

Today, low-level `Tool` wiring already exists in the core loop, but the following are not finished yet:

- `@StrandsTool` and `@ToolParam`
- Spring bean scanning for tools
- JSON schema generation from Java types
- structured output via `agent.run("...", MyType.class)`

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
@Configuration
class AgentConfiguration {

    @Bean
    Agent agent(AgentFactory factory) {
        return factory.builder().build();
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

## Build And Verify

```bash
mvn test
```

The Bedrock smoke test is opt-in:

```bash
mvn -Dtest=BedrockModelIntegrationTest -Darachne.integration.bedrock=true test
```