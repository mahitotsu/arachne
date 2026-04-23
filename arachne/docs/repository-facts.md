# Repository Facts

This document is a practical map of the current repository.

Use it when you need to know:

- where the main library code lives
- which sample demonstrates a capability
- which commands verify the current branch
- where to look for implementation or architecture details

## Repository At A Glance

- artifact: `com.mahitotsu.arachne:arachne:0.1.0-SNAPSHOT`
- Java baseline: 25
- Spring Boot baseline: 3.5.12
- built-in model provider: AWS Bedrock
- default verification command: `mvn test`

## Top-Level Modules

- `pom.xml`: repository reactor
- `arachne/pom.xml`: main library module
- `food-delivery-demo/pom.xml`: independent multi-module food-delivery demo with service-local agents behind Spring microservices
- `samples/pom.xml`: runnable sample reactor

## Main Directories

- `arachne/src/main/java`: main library implementation
- `arachne/src/test/java`: library test suite
- `arachne/docs`: library usage, status, repository map, and ADRs
- `food-delivery-demo`: root-level food-delivery product track with service modules, a Next.js chat console, and composed runtime wiring
- `samples`: runnable reference applications for shipped capabilities
- `refs/sdk-python`: behavioral reference material for the Python SDK lineage
- `target`: local build output and generated reports

## Main Package Areas

Top-level packages under `arachne/src/main/java/com/mahitotsu/arachne/strands`:

- `agent`
- `event`
- `eventloop`
- `hooks`
- `model`
- `schema`
- `session`
- `skills`
- `spring`
- `steering`
- `tool`
- `types`

## Where To Look For Common Topics

- agent creation and Spring wiring: `arachne/src/main/java/com/mahitotsu/arachne/strands/spring`
- runtime loop and orchestration: `arachne/src/main/java/com/mahitotsu/arachne/strands/agent` and `arachne/src/main/java/com/mahitotsu/arachne/strands/eventloop`
- model integration and Bedrock behavior: `arachne/src/main/java/com/mahitotsu/arachne/strands/model`
- tool contracts and execution: `arachne/src/main/java/com/mahitotsu/arachne/strands/tool`
- sessions and persistence: `arachne/src/main/java/com/mahitotsu/arachne/strands/session`
- skills: `arachne/src/main/java/com/mahitotsu/arachne/strands/skills`
- steering: `arachne/src/main/java/com/mahitotsu/arachne/strands/steering`
- architectural decisions: `arachne/docs/adr`

## Sample Map

Each sample demonstrates a current capability area.

| Sample | Primary focus |
| --- | --- |
| `conversation-basics` | smallest end-to-end agent runtime |
| `built-in-tools` | built-in inheritance and resource allowlists |
| `secure-downstream-tools` | secure downstream API access |
| `stateful-backend-operations` | idempotent backend mutations and workflow state |
| `tool-delegation` | agent delegation and typed outputs |
| `tool-execution-context` | invocation metadata vs executor propagation |
| `session-redis` | Spring Session Redis restore |
| `session-jdbc` | Spring Session JDBC restore |
| `approval-workflow` | interrupt / resume and approval flow |
| `skill-activation` | packaged skill discovery and activation |
| `streaming-steering` | callback streaming and steering |
| `domain-separation` | composed backend reference |

## Verification Commands

Use these commands against the current branch:

```bash
mvn test
mvn -Pquality-report verify
mvn -Pquality-security verify
```

For sample-side verification, refresh the library snapshot before using the samples reactor:

```bash
mvn -pl arachne -am install -DskipTests
mvn -f samples/pom.xml test
```

This is required because `samples/pom.xml` resolves `com.mahitotsu.arachne:arachne` from the local Maven repository instead of directly from the sibling `arachne/` module.

Dependency evidence is intentionally split:

- `mvn -Pquality-security verify` generates CycloneDX SBOM inventory artifacts for the current checkout
- GitHub Dependabot is the repository-side update and advisory monitoring source for the Maven manifest in `.github/dependabot.yml`
- a local audit can confirm the Dependabot configuration, but live alert state requires GitHub UI or API evidence

Bedrock live verification is opt-in:

```bash
mvn -Dtest=BedrockModelIntegrationTest \
  -Darachne.integration.bedrock=true \
  -Darachne.integration.bedrock.region=<aws-region> \
  -Darachne.integration.bedrock.model-id=<bedrock-model-id> \
  test
mvn -f samples/pom.xml -pl domain-separation \
  -Dtest=DomainSeparationBedrockIntegrationTest \
  -Darachne.integration.bedrock=true \
  -Darachne.integration.bedrock.region=<aws-region> \
  -Darachne.integration.bedrock.model-id=<bedrock-model-id> \
  test
```

When work changes Bedrock-specific runtime behavior, Bedrock-facing sample wiring, or Bedrock-only documentation claims, rerun both commands or record the missing live evidence explicitly.

## Current Reference Documents

- `README.md`: top-level entry point
- `arachne/docs/user-guide.md`: primary usage guide
- `arachne/docs/project-status.md`: current shipped surface and constraints
- `arachne/docs/tool-catalog.md`: current tool surface
- `arachne/docs/adr/README.md`: architecture decisions
- `samples/README.md`: runnable sample catalog