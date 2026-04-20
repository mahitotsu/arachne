# Arachne

Arachne is a Java port of the Strands Agents SDK with Spring Boot integration.

This repository is distributed under the Apache License 2.0. It was developed
with reference to the Strands Agents Python SDK and includes original
modifications and Java/Spring Boot-specific additions. See [LICENSE](LICENSE)
and [NOTICE](NOTICE) for the applicable license and attribution details.

Arachne currently provides a Bedrock-backed agent runtime with Spring Boot auto-configuration, annotation-driven tools, structured output, retry, conversation/session management, hooks/plugins, interrupts, packaged skills, and opt-in streaming plus steering.

## Start Here

If you are evaluating or integrating Arachne, read these first:

1. [arachne/docs/user-guide.md](arachne/docs/user-guide.md)
2. [arachne/docs/project-status.md](arachne/docs/project-status.md)
3. [samples/README.md](samples/README.md)

Use those documents for the detailed API, configuration surface, current constraints, and runnable sample selection.

Treat [arachne/docs/project-status.md](arachne/docs/project-status.md) as the canonical source of truth for feature availability, shipped boundaries, and current constraints on this branch.

## Repository Layout

- `pom.xml`: root Maven reactor for the repository
- `arachne/`: main library module published as `com.mahitotsu.arachne:arachne`
- `marketplace-agent-platform/`: independent multi-module food-delivery product track showing Spring microservices with service-local Arachne agents and a Next.js chat UI
- `samples/`: runnable sample reactor and sample applications
- `arachne/docs/`: library and architecture documentation for the published Arachne module

## Quick Start

Prerequisites:

- Java 25
- Spring Boot 3.5.12
- AWS credentials resolvable by the AWS SDK default credentials chain
- access to the configured Bedrock model in the target AWS region

If you are consuming the library from this repository before publication, install it locally first:

```bash
mvn -pl arachne -am install
```

Minimum Spring Boot configuration:

```yaml
arachne:
  strands:
    model:
      provider: bedrock
      id: jp.amazon.nova-2-lite-v1:0
      region: ap-northeast-1
    agent:
      system-prompt: "You are a concise assistant."
```

Minimal usage:

```java
import org.springframework.stereotype.Service;

import com.mahitotsu.arachne.strands.spring.AgentFactory;

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
```

## What Is Available Now

This section is a convenience summary. For the authoritative shipped boundary, use [arachne/docs/project-status.md](arachne/docs/project-status.md).

- `AgentFactory.builder()` and `AgentFactory.builder("name")`
- `Agent.run(String)` and `Agent.run(String, Class<T>)`
- built-in tools: `calculator`, `current_time`, `resource_reader`, `resource_list`
- annotation-driven tools with `@StrandsTool` and `@ToolParam`
- session persistence with in-memory, file, Redis, and JDBC-backed storage
- interrupt / resume before tool execution
- packaged skills with delayed activation
- callback-based streaming and runtime-local steering

For the current shipped surface and constraints, see [arachne/docs/project-status.md](arachne/docs/project-status.md).

## Documentation

- [arachne/docs/user-guide.md](arachne/docs/user-guide.md): setup, configuration, runtime usage, tools, sessions, skills, streaming, and steering
- [arachne/docs/project-status.md](arachne/docs/project-status.md): current shipped features, sample map, and constraints
- [arachne/docs/tool-catalog.md](arachne/docs/tool-catalog.md): current tool surface and tool-design cautions
- [arachne/docs/repository-facts.md](arachne/docs/repository-facts.md): repository layout, package map, and verification commands
- [arachne/docs/adr/README.md](arachne/docs/adr/README.md): architecture decisions behind the current model

## Samples

- [samples/conversation-basics/README.md](samples/conversation-basics/README.md): smallest end-to-end runtime
- [samples/built-in-tools/README.md](samples/built-in-tools/README.md): built-in tools and resource allowlists
- [samples/stateful-backend-operations/README.md](samples/stateful-backend-operations/README.md): stateful backend mutations and workflow state
- [samples/streaming-steering/README.md](samples/streaming-steering/README.md): callback streaming and steering
- [samples/domain-separation/README.md](samples/domain-separation/README.md): composed backend reference

## Verification

```bash
mvn test
mvn -Pquality-security verify
```

Repository workflow surfaces live under `.github/prompts` and `.github/skills`, centered on `close-action`, `locality-check`, `repository-metrics`, `ship-changes`, `session-handoff`, `repository-ops`, `repo-snapshot`, and `repository-structure-health`.

Bedrock smoke verification is opt-in:

```bash
mvn -Dtest=BedrockModelIntegrationTest -Darachne.integration.bedrock=true test
```

For dependency evidence, treat `mvn -Pquality-security verify` as inventory generation only: it produces CycloneDX SBOM artifacts for the current branch. Treat GitHub Dependabot as the repository-side update and advisory monitoring source for the Maven manifest configured in [.github/dependabot.yml](.github/dependabot.yml); local checkouts can verify the configuration, but current alert state must come from GitHub UI or API evidence.

## License

Arachne is licensed under the Apache License 2.0.

This repository is a Java/Spring Boot port developed with reference to the
Strands Agents Python SDK. Attribution and lineage details are recorded in
[NOTICE](NOTICE).