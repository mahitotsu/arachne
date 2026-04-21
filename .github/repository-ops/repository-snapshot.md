# Repository Snapshot

Updated: 2026-04-20

This file is the session-start index for Arachne repository work.
Use it with `/memories/repo/status.md` to re-establish current state quickly.
Use `repository-reading-guide.md` only when you need a deeper entry-point map.

## Current Shape

- `arachne/` is the main library module. It currently ships Spring Boot auto-configuration, `AgentFactory`, Bedrock-backed `Model` integration, tools and structured output, sessions, hooks and plugins, interrupts and resume, packaged skills, callback streaming, and steering.
- `samples/` is the runnable sample catalog for the shipped library surface. Sample-side verification requires refreshing the local `arachne` snapshot first.
- `food-delivery-demo/` is an independent multi-module food-delivery product track with Spring services and a thin Next.js customer UI. It is not part of the runnable sample catalog.
- `refs/sdk-python/` remains behavioral reference material only.
- `.github/` now uses a single repo-ops workflow layer: `repository-ops`, `repo-snapshot`, `repository-structure-health`, `repository-metrics`, `close-action`, `locality-check`, `ship-changes`, and `session-handoff`.

## Read First

1. `repository-snapshot.md`
2. `/memories/repo/status.md`
3. `arachne/docs/project-status.md`
4. `arachne/docs/repository-facts.md`
5. `repository-reading-guide.md` only when you need the detailed entry-point map

## Area Index

### Core Runtime And Spring

- Status: `AgentFactory`, Spring auto-configuration, the blocking runtime, and the standard `Agent -> EventLoop -> Model / Tool` flow are the core shipped path.
- Start here: `arachne/docs/project-status.md`, `arachne/docs/adr/0001-agent-runtime-lifecycle.md`, `arachne/docs/adr/0003-spring-integration-entrypoint.md`, `arachne/src/main/java/com/mahitotsu/arachne/strands/spring/AgentFactory.java`
- Verification: `mvn test`

### Tools And Structured Output

- Status: built-in tools, annotation-driven tools, manual `Tool` registration, tool execution backends, structured output schema generation, and deterministic template rendering are shipped.
- Start here: `arachne/docs/tool-catalog.md`, `arachne/docs/adr/0005-binding-validation-boundaries.md`, `arachne/docs/adr/0007-phase2-tool-contracts.md`, `arachne/src/main/java/com/mahitotsu/arachne/strands/tool/Tool.java`
- Verification: `mvn test`

### Conversation And Sessions

- Status: in-memory multi-turn conversation, sliding-window and summarizing managers, explicit `SessionManager`, file-backed sessions, and Spring Session restore are shipped.
- Start here: `arachne/docs/project-status.md`, `arachne/docs/adr/0002-session-manager-explicit-session-id.md`, `arachne/src/main/java/com/mahitotsu/arachne/strands/session/SessionManager.java`, `samples/session-jdbc/README.md`, `samples/session-redis/README.md`
- Verification: `mvn test`

### Extensions And Control

- Status: hooks, plugins, observation bridge, interrupts and resume, packaged skills, callback streaming, and steering are shipped as opt-in extensions.
- Start here: `arachne/docs/project-status.md`, `arachne/docs/adr/0008-hook-registry-and-plugin-boundary.md`, `arachne/docs/adr/0009-interrupt-resume-and-observation-bridge.md`, `arachne/docs/adr/0010-skills-injection-and-discovery-boundary.md`, `arachne/docs/adr/0011-streaming-and-steering-boundary.md`
- Verification: `mvn test`

### Bedrock Provider Integration

- Status: AWS Bedrock is the only built-in provider. Prompt caching and usage metrics are opt-in. Live verification remains opt-in and credential-dependent.
- Start here: `arachne/docs/project-status.md`, `arachne/docs/repository-facts.md`, `arachne/docs/adr/0016-bedrock-prompt-caching-and-usage-metrics.md`, `arachne/src/main/java/com/mahitotsu/arachne/strands/model/bedrock/BedrockModel.java`
- Verification: `mvn test`; when Bedrock-specific behavior changed, rerun the opt-in Bedrock smoke checks from `arachne/docs/repository-facts.md`

### Samples

- Status: `samples/README.md` is the canonical sample map for the shipped library surface.
- Start here: `samples/README.md`, `arachne/docs/project-status.md`
- Verification: `mvn -pl arachne -am install -DskipTests` then `mvn -f samples/pom.xml test`

### Marketplace Agent Platform

- Status: the product track now ships a food-delivery demo where each Spring microservice owns a service-local agent behind plain HTTP APIs, plus a thin chat-first operator console. It is not the same surface as `samples/`.
- Start here: `marketplace-agent-platform/README.md`, `marketplace-agent-platform/docs/architecture.md`, `marketplace-agent-platform/operator-console/README.md`
- Verification: `mvn -f marketplace-agent-platform/pom.xml test`; in `marketplace-agent-platform/operator-console`, run `npm ci` and `npm run build`

### Repository Operations

- Status: the repo-ops layer handles repo restart, quantitative repo metrics, locality checks, ship workflow, handoff, snapshot refresh, and structure-health checks.
- Start here: `.github/copilot-instructions.md`, `.github/skills/repository-ops/SKILL.md`, `.github/prompts/repository-metrics.prompt.md`, `.github/repository-ops/repository-metrics.md`, `.github/repository-ops/repository-metrics-latest.md`, `.github/prompts/close-action.prompt.md`, `.github/prompts/ship-changes.prompt.md`
- Verification: lightweight path and sync review for workflow-only changes, plus area-specific verification for touched code

## Notes And Risks

- `samples/pom.xml` resolves the local library snapshot, so sample verification is stale unless the install step ran first.
- `refs/sdk-python/` is reference-only and should not be edited as part of normal Arachne work.
- `marketplace-agent-platform/` is intentionally separate from the `samples/` catalog. Do not assume its docs or verification rules are interchangeable with sample guidance.
- When the repository operating flow changes, sync `.github/repository-ops/`, related prompts and skills, `.github/copilot-instructions.md`, and `/memories/repo/status.md` together.