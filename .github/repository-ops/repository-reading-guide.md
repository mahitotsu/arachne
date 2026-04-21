# Repository Reading Guide

Updated: 2026-04-20

This guide is the detailed companion to `repository-snapshot.md`.
Read the snapshot first. Use this file only when you need deeper entry points, a bounded read set, or the right prompt and instruction for the area you are touching.

## Core Runtime And Spring

- Status: this is the main assembly surface for the shipped library runtime.
- Work type: agent assembly, Spring integration, runtime lifecycle, and event-loop ownership.
- Canonical docs:
  - `arachne/docs/project-status.md`
  - `arachne/docs/adr/0001-agent-runtime-lifecycle.md`
  - `arachne/docs/adr/0003-spring-integration-entrypoint.md`
  - `arachne/docs/adr/0004-agent-definition-runtime-split.md`
- Main implementation entry points:
  - `arachne/src/main/java/com/mahitotsu/arachne/strands/spring/AgentFactory.java`
  - `arachne/src/main/java/com/mahitotsu/arachne/strands/spring/ArachneAutoConfiguration.java`
  - `arachne/src/main/java/com/mahitotsu/arachne/strands/agent`
  - `arachne/src/main/java/com/mahitotsu/arachne/strands/eventloop`
- Main test or sample entry points:
  - `samples/conversation-basics/README.md`
  - `samples/domain-separation/README.md`
  - `arachne/src/test/java/com/mahitotsu/arachne/strands/spring`
- Instruction binding:
  - production code: `.github/instructions/java-implementation.instructions.md`
  - test code: `.github/instructions/java-tests.instructions.md`
- Bounded read set:
  - `arachne/docs/project-status.md`
  - `arachne/docs/repository-facts.md`
  - `.github/copilot-instructions.md`
  - `AgentFactory.java`
  - `ArachneAutoConfiguration.java`
  - nearest matching Spring test or sample README
- Verification: `mvn test`

## Tools And Structured Output

- Status: this is the shipped tool authoring and execution boundary.
- Work type: tool contracts, annotation binding, execution backends, built-ins, structured output schema generation, template rendering.
- Canonical docs:
  - `arachne/docs/tool-catalog.md`
  - `arachne/docs/project-status.md`
  - `arachne/docs/adr/0005-binding-validation-boundaries.md`
  - `arachne/docs/adr/0006-tool-execution-backend.md`
  - `arachne/docs/adr/0007-phase2-tool-contracts.md`
  - `arachne/docs/adr/0014-tool-invocation-context-contract.md`
  - `arachne/docs/adr/0015-execution-context-propagation-boundary.md`
- Main implementation entry points:
  - `arachne/src/main/java/com/mahitotsu/arachne/strands/tool/Tool.java`
  - `arachne/src/main/java/com/mahitotsu/arachne/strands/tool/ToolExecutor.java`
  - `arachne/src/main/java/com/mahitotsu/arachne/strands/tool/annotation`
  - `arachne/src/main/java/com/mahitotsu/arachne/strands/schema`
  - `arachne/src/main/java/com/mahitotsu/arachne/strands/spring/ArachneTemplateRenderer.java`
- Main test or sample entry points:
  - `samples/built-in-tools/README.md`
  - `samples/tool-delegation/README.md`
  - `samples/tool-execution-context/README.md`
  - `arachne/src/test/java/com/mahitotsu/arachne/strands/tool`
  - `arachne/src/test/java/com/mahitotsu/arachne/strands/schema`
- Instruction binding:
  - production code: `.github/instructions/java-implementation.instructions.md`
  - test code: `.github/instructions/java-tests.instructions.md`
- Bounded read set:
  - `arachne/docs/tool-catalog.md`
  - `arachne/docs/project-status.md`
  - `Tool.java`
  - `ToolExecutor.java`
  - nearest sample README or test package for the touched tool surface
- Verification: `mvn test`

## Conversation And Sessions

- Status: explicit session ownership and conversation compaction are shipped.
- Work type: session manager implementations, conversation windows, restore behavior, Spring Session integration.
- Canonical docs:
  - `arachne/docs/project-status.md`
  - `arachne/docs/user-guide.md`
  - `arachne/docs/adr/0002-session-manager-explicit-session-id.md`
- Main implementation entry points:
  - `arachne/src/main/java/com/mahitotsu/arachne/strands/session/SessionManager.java`
  - `arachne/src/main/java/com/mahitotsu/arachne/strands/session`
  - `arachne/src/main/java/com/mahitotsu/arachne/strands/agent/conversation`
- Main test or sample entry points:
  - `samples/session-jdbc/README.md`
  - `samples/session-redis/README.md`
  - `samples/approval-workflow/README.md`
  - `arachne/src/test/java/com/mahitotsu/arachne/strands/session`
- Instruction binding:
  - production code: `.github/instructions/java-implementation.instructions.md`
  - test code: `.github/instructions/java-tests.instructions.md`
- Bounded read set:
  - `arachne/docs/project-status.md`
  - `arachne/docs/repository-facts.md`
  - `arachne/docs/adr/0002-session-manager-explicit-session-id.md`
  - `SessionManager.java`
  - nearest session sample README or session test package
- Verification: `mvn test`; when sample restore behavior matters, refresh the local snapshot and run `mvn -f samples/pom.xml test`

## Extensions And Control

- Status: hooks, plugins, interrupt and resume, skills, streaming, and steering are all opt-in layers over the existing blocking runtime.
- Work type: extension hooks, pause and resume boundaries, packaged skill discovery, callback streaming, and steering handlers.
- Canonical docs:
  - `arachne/docs/project-status.md`
  - `arachne/docs/user-guide.md`
  - `arachne/docs/adr/0008-hook-registry-and-plugin-boundary.md`
  - `arachne/docs/adr/0009-interrupt-resume-and-observation-bridge.md`
  - `arachne/docs/adr/0010-skills-injection-and-discovery-boundary.md`
  - `arachne/docs/adr/0011-streaming-and-steering-boundary.md`
- Main implementation entry points:
  - `arachne/src/main/java/com/mahitotsu/arachne/strands/hooks`
  - `arachne/src/main/java/com/mahitotsu/arachne/strands/skills`
  - `arachne/src/main/java/com/mahitotsu/arachne/strands/steering/SteeringHandler.java`
  - `arachne/src/main/java/com/mahitotsu/arachne/strands/model/StreamingModel.java`
  - `arachne/src/main/java/com/mahitotsu/arachne/strands/spring/ClasspathSkillDiscoverer.java`
- Main test or sample entry points:
  - `samples/approval-workflow/README.md`
  - `samples/skill-activation/README.md`
  - `samples/streaming-steering/README.md`
  - `arachne/src/test/java/com/mahitotsu/arachne/strands/skills`
  - nearest hook, interrupt, or streaming test package under `arachne/src/test/java`
- Instruction binding:
  - production code: `.github/instructions/java-implementation.instructions.md`
  - test code: `.github/instructions/java-tests.instructions.md`
- Bounded read set:
  - `arachne/docs/project-status.md`
  - the governing ADR for the touched extension boundary
  - one implementation entry point in the touched package
  - one matching sample README or nearest test package
- Verification: `mvn test`

## Bedrock Provider Integration

- Status: Bedrock is the only shipped model provider and the main provider-specific boundary.
- Work type: provider-specific request mapping, prompt caching, metrics, and opt-in live verification.
- Canonical docs:
  - `arachne/docs/project-status.md`
  - `arachne/docs/repository-facts.md`
  - `arachne/docs/adr/0016-bedrock-prompt-caching-and-usage-metrics.md`
  - `arachne/docs/adr/0012-post-mvp-product-boundary.md`
- Main implementation entry points:
  - `arachne/src/main/java/com/mahitotsu/arachne/strands/model/Model.java`
  - `arachne/src/main/java/com/mahitotsu/arachne/strands/model/bedrock/BedrockModel.java`
  - `arachne/src/main/java/com/mahitotsu/arachne/strands/model/bedrock`
- Main test or sample entry points:
  - `arachne/src/test/java/com/mahitotsu/arachne/strands/model`
  - `samples/domain-separation/README.md`
  - `food-delivery-demo/BEDROCK-DEMO-REPORT.md`
- Instruction binding:
  - production code: `.github/instructions/java-implementation.instructions.md`
  - test code: `.github/instructions/java-tests.instructions.md`
- Bounded read set:
  - `arachne/docs/project-status.md`
  - `arachne/docs/repository-facts.md`
  - `BedrockModel.java`
  - nearest Bedrock-facing test or sample README
  - `refs/sdk-python` only when behavioral comparison is required
- Verification:
  - default: `mvn test`
  - opt-in live evidence when Bedrock-specific behavior changed: rerun the Bedrock smoke commands documented in `arachne/docs/repository-facts.md`

## Samples And Product Track

- Status: `samples/` is the canonical runnable library surface, while `food-delivery-demo/` is an independent composed product track.
- Work type: sample wiring, README guidance, product-track Java services, customer-ui, and cross-module verification.
- Canonical docs:
  - `samples/README.md`
  - `arachne/docs/project-status.md`
  - `arachne/docs/repository-facts.md`
  - `food-delivery-demo/README.md`
- Main implementation entry points:
  - `samples/*/README.md`
  - `food-delivery-demo/pom.xml`
  - `food-delivery-demo/customer-ui/package.json`
  - nearest `food-delivery-demo/docs/*.md` file for the touched area
- Main test or sample entry points:
  - `samples/conversation-basics/README.md`
  - `samples/domain-separation/README.md`
  - `food-delivery-demo/customer-ui/README.md`
- Instruction binding:
  - Java code: `.github/instructions/java-implementation.instructions.md` or `.github/instructions/java-tests.instructions.md`
  - TypeScript customer UI: `.github/instructions/typescript-customer-ui.instructions.md`
- Bounded read set:
  - `samples/README.md` or `food-delivery-demo/README.md`, depending on the surface
  - one nearest README for the touched module
  - one nearest `pom.xml` or `package.json`
  - `arachne/docs/project-status.md` when the shipped sample map may change
- Verification:
  - samples: `mvn -pl arachne -am install -DskipTests` then `mvn -f samples/pom.xml test`
  - food-delivery Java: `mvn -f food-delivery-demo/pom.xml test`
  - customer UI: in `food-delivery-demo/customer-ui`, run `npm ci` and `npm run build`

## Repository Workflow

- Status: the repo-ops layer is the single repository workflow surface for restart, quantitative repo health, locality, ship, handoff, snapshot maintenance, and structure-health checks.
- Work type: workflow prompts, skills, operating docs, repo restart, commit readiness, quantitative metrics passes, structural drift checks, and session handoff.
- Canonical docs:
  - `.github/copilot-instructions.md`
  - `.github/repository-ops/repository-snapshot.md`
  - `.github/repository-ops/repository-metrics.md`
  - `.github/repository-ops/repository-metrics-latest.md`
- Main prompt and skill entry points:
  - `.github/skills/repository-ops/SKILL.md`
  - `.github/skills/repo-snapshot/SKILL.md`
  - `.github/skills/repository-structure-health/SKILL.md`
  - `.github/prompts/close-action.prompt.md`
  - `.github/prompts/locality-check.prompt.md`
  - `.github/prompts/repository-metrics.prompt.md`
  - `.github/prompts/ship-changes.prompt.md`
  - `.github/prompts/session-handoff.prompt.md`
- Bounded read set:
  - `.github/copilot-instructions.md`
  - `repository-snapshot.md`
  - `/memories/repo/status.md`
  - one relevant prompt or skill file for the intended operation
  - `repository-metrics.md`, `repository-metrics-latest.md`, and `repository-metrics-rules.json` when the task is quantitative repository evaluation
- Verification:
  - workflow-only changes: path and sync review across the touched prompts, skills, and repo-ops docs
  - if the workflow change alters shipped guidance, also review `arachne/docs/project-status.md` and `arachne/docs/repository-facts.md`

## Prompt And Skill Routing

- Use `.github/skills/repository-ops/SKILL.md` when the right workflow is unclear.
- Use `.github/prompts/close-action.prompt.md` before commit or session close.
- Use `.github/prompts/locality-check.prompt.md` when the change may be spreading.
- Use `.github/prompts/repository-metrics.prompt.md` when you need quantified repository health from build, coverage, and static-analysis outputs.
- Use `.github/prompts/ship-changes.prompt.md` when the user explicitly wants commit or push execution.
- Use `.github/prompts/session-handoff.prompt.md` when you need a paste-ready next-session prompt.