# Repository Reading Guide

Use this file to identify the right starting surface and bounded read set for a given work area.
Read `memories/repo/status.md` first to understand current work state.

## Repository Modules

- `arachne/` — main library module: Spring Boot auto-configuration, AgentFactory, Bedrock Model, tools, sessions, hooks, interrupts, skills, streaming, steering
- `samples/` — runnable sample catalog for the shipped library surface (requires local arachne install before verification)
- `food-delivery-demo/` — independent multi-module food-delivery product track with Spring services and a Next.js UI; not the same surface as `samples/`
- `refs/sdk-python/` — behavioral reference material only; do not edit

## Core Runtime And Spring

- Entry points: `arachne/docs/project-status.md`, `arachne/src/main/java/com/mahitotsu/arachne/strands/spring/AgentFactory.java`, `arachne/src/main/java/com/mahitotsu/arachne/strands/spring/ArachneAutoConfiguration.java`
- ADRs: `arachne/docs/adr/0001-agent-runtime-lifecycle.md`, `arachne/docs/adr/0003-spring-integration-entrypoint.md`, `arachne/docs/adr/0004-agent-definition-runtime-split.md`
- Bounded read set: `arachne/docs/project-status.md`, `arachne/docs/repository-facts.md`, `AgentFactory.java`, `ArachneAutoConfiguration.java`, nearest Spring test or sample README
- Scoped guidance: `arachne/src/main/CLAUDE.md` (production), `arachne/src/test/CLAUDE.md` (tests)
- Verification: `mvn test`

## Tools And Structured Output

- Entry points: `arachne/docs/tool-catalog.md`, `arachne/src/main/java/com/mahitotsu/arachne/strands/tool/Tool.java`, `arachne/src/main/java/com/mahitotsu/arachne/strands/tool/ToolExecutor.java`
- ADRs: `arachne/docs/adr/0005-binding-validation-boundaries.md`, `arachne/docs/adr/0007-phase2-tool-contracts.md`, `arachne/docs/adr/0014-tool-invocation-context-contract.md`, `arachne/docs/adr/0015-execution-context-propagation-boundary.md`
- Bounded read set: `arachne/docs/tool-catalog.md`, `arachne/docs/project-status.md`, `Tool.java`, `ToolExecutor.java`, nearest sample README or test package
- Scoped guidance: `arachne/src/main/CLAUDE.md`, `arachne/src/test/CLAUDE.md`
- Verification: `mvn test`

## Conversation And Sessions

- Entry points: `arachne/src/main/java/com/mahitotsu/arachne/strands/session/SessionManager.java`, `arachne/src/main/java/com/mahitotsu/arachne/strands/session`, `arachne/src/main/java/com/mahitotsu/arachne/strands/agent/conversation`
- ADRs: `arachne/docs/adr/0002-session-manager-explicit-session-id.md`
- Bounded read set: `arachne/docs/project-status.md`, `arachne/docs/repository-facts.md`, `arachne/docs/adr/0002-session-manager-explicit-session-id.md`, `SessionManager.java`, nearest session sample README or test package
- Scoped guidance: `arachne/src/main/CLAUDE.md`, `arachne/src/test/CLAUDE.md`
- Verification: `mvn test`; when sample restore behavior matters, run `mvn -pl arachne -am install -DskipTests` then `mvn -f samples/pom.xml test`

## Extensions And Control

- Entry points: `arachne/src/main/java/com/mahitotsu/arachne/strands/hooks`, `arachne/src/main/java/com/mahitotsu/arachne/strands/steering/SteeringHandler.java`, `arachne/src/main/java/com/mahitotsu/arachne/strands/spring/ClasspathSkillDiscoverer.java`
- ADRs: `arachne/docs/adr/0008-hook-registry-and-plugin-boundary.md`, `arachne/docs/adr/0009-interrupt-resume-and-observation-bridge.md`, `arachne/docs/adr/0010-skills-injection-and-discovery-boundary.md`, `arachne/docs/adr/0011-streaming-and-steering-boundary.md`
- Bounded read set: `arachne/docs/project-status.md`, governing ADR for the touched extension boundary, one entry point in the touched package, nearest sample README or test package
- Scoped guidance: `arachne/src/main/CLAUDE.md`, `arachne/src/test/CLAUDE.md`
- Verification: `mvn test`

## Bedrock Provider Integration

- Entry points: `arachne/src/main/java/com/mahitotsu/arachne/strands/model/bedrock/BedrockModel.java`, `arachne/src/main/java/com/mahitotsu/arachne/strands/model/Model.java`
- ADRs: `arachne/docs/adr/0016-bedrock-prompt-caching-and-usage-metrics.md`, `arachne/docs/adr/0012-post-mvp-product-boundary.md`
- Bounded read set: `arachne/docs/project-status.md`, `arachne/docs/repository-facts.md`, `BedrockModel.java`, nearest Bedrock-facing test or sample README; `refs/sdk-python` only when behavioral comparison is required
- Scoped guidance: `arachne/src/main/CLAUDE.md`, `arachne/src/test/CLAUDE.md`
- Verification: `mvn test`; opt-in live evidence: Bedrock smoke commands in `arachne/docs/repository-facts.md`

## Samples

- Entry points: `samples/README.md`, `arachne/docs/project-status.md`
- Bounded read set: `samples/README.md`, nearest sample README, `arachne/docs/project-status.md` when the shipped sample map may change
- Scoped guidance: `arachne/src/main/CLAUDE.md`, `arachne/src/test/CLAUDE.md`
- Verification: `mvn -pl arachne -am install -DskipTests` then `mvn -f samples/pom.xml test`

## Food Delivery Demo

- Entry points: `food-delivery-demo/README.md`, `food-delivery-demo/docs/architecture.md`, `food-delivery-demo/customer-ui/README.md`
- Bounded read set: `food-delivery-demo/README.md`, nearest module README, nearest `pom.xml` or `package.json`
- Scoped guidance: `arachne/src/main/CLAUDE.md`, `arachne/src/test/CLAUDE.md` (Java); `food-delivery-demo/customer-ui/CLAUDE.md` (TypeScript)
- Verification: `mvn -f food-delivery-demo/pom.xml test`; customer UI: `npm ci` then `npm run build` in `food-delivery-demo/customer-ui`

## Notes

- `samples/pom.xml` resolves the local library snapshot; always run `mvn -pl arachne -am install -DskipTests` before sample-side verification.
- `refs/sdk-python/` is reference-only; do not edit as part of normal Arachne work.
- `food-delivery-demo/` is separate from `samples/`; docs and verification commands are not interchangeable.
