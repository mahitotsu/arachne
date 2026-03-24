# Repository Facts

This document is a lightweight engineering snapshot of the Arachne repository.

It is intended to complement, not replace, the normative project documents:

- `README.md` for entry points and quick start
- `docs/project-status.md` for shipped scope and deferrals
- `docs/user-guide.md` for API and usage guidance
- `docs/adr/` for architectural decisions

Update this document when a quality audit, major phase closeout, new sample module, or notable structural refactor changes the repository-wide picture.

## Scope Of This Snapshot

- Snapshot date: 2026-03-25
- Repository artifact: `io.arachne:arachne:0.1.0-SNAPSHOT`
- Java version: 21
- Spring Boot baseline: 3.5.12
- Primary built-in model provider: AWS Bedrock
- Default verification command: `mvn test`
- Quality snapshot commands:
  - `mvn -Pquality-report verify`
  - `mvn -Pquality-security verify`

## Quantitative Snapshot

These values reflect the checked-in repository state and the latest local quality artifacts gathered on 2026-03-25.

### Core Code Volume

| Surface | Java files | LOC |
| --- | ---: | ---: |
| `src/main/java` | 91 | 7706 |
| `src/test/java` | 23 | 5311 |
| `samples/**/src/main/java` | 52 | not aggregated in this snapshot |
| `samples/**/src/test/java` | 3 | not aggregated in this snapshot |

### Verification And Quality Totals

| Metric | Value |
| --- | ---: |
| Root test methods (`@Test`) | 154 |
| Latest Maven test result | 154 run, 0 failures, 0 errors, 1 skipped |
| Instruction coverage | 85.53% |
| Branch coverage | 68.01% |
| SpotBugs findings | 49 |
| PMD violations | 0 |
| CPD duplications | 0 |
| ADR count | 16 |
| Sample modules | 9 |

### Large-Class Hotspots

The following classes are notable because they combine meaningful size with repository-level architectural importance.

| Class | Instructions | Coverage |
| --- | ---: | ---: |
| `BedrockModel` | 1034 | 61.61% |
| `AgentFactory` | 732 | 85.52% |
| `DefaultAgent` | 710 | 87.61% |
| `JsonSchemaGenerator` | 687 | 83.11% |
| `AgentSkillsPlugin` | 661 | 96.52% |
| `EventLoop` | 626 | 99.20% |
| `SummarizingConversationManager` | 617 | 82.50% |
| `SkillParser` | 609 | 86.54% |
| `AgentFactory.Builder` | 609 | 79.64% |
| `SkillResourceTool` | 443 | 79.23% |

### Static-Analysis Distribution

The current SpotBugs totals are concentrated in a few recurring categories rather than a wide spread of unrelated defect classes.

| SpotBugs type | Count |
| --- | ---: |
| `EI_EXPOSE_REP` | 22 |
| `EI_EXPOSE_REP2` | 16 |
| `CT_CONSTRUCTOR_THROW` | 11 |

### SBOM Snapshot

| Artifact | Timestamp |
| --- | --- |
| `target/dependency-bom.json` | 2026-03-24T15:40:43Z |
| `target/classes/META-INF/sbom/application.cdx.json` | 2026-03-24T15:40:29Z |

## Repository Layout

### Root Structure

- `src/`: the main library implementation and tests
- `docs/`: status, user guide, tool catalog, ADRs, and repository-level documentation
- `samples/`: runnable reference applications that exercise shipped capabilities
- `refs/sdk-python/`: behavioral reference material for the Python SDK lineage
- `target/`: local build outputs and generated quality artifacts

### Main Package Areas

Top-level package groups under `src/main/java/io/arachne/strands`:

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

### Sample Modules

| Sample | Main Java files | Test Java files | Focus |
| --- | ---: | ---: | --- |
| `conversation-basics` | 3 | 0 | basic multi-turn agent usage |
| `tool-delegation` | 4 | 0 | tools and delegation patterns |
| `tool-execution-context` | 5 | 0 | execution-context propagation |
| `session-redis` | 2 | 0 | Spring Session with Redis |
| `session-jdbc` | 2 | 0 | Spring Session with JDBC |
| `approval-workflow` | 5 | 0 | hooks, interrupts, approval flow |
| `skill-activation` | 3 | 0 | skill discovery and activation |
| `streaming-steering` | 5 | 0 | callback streaming and steering |
| `domain-separation` | 23 | 3 | higher-level backend reference and richer composition |

## Architecture Summary

### Core Runtime Shape

The core runtime remains intentionally readable as:

`Agent -> EventLoop -> Model / Tool`

- `DefaultAgent` owns conversation state, orchestration entry points, and result assembly.
- `EventLoop` coordinates model turns, tool execution, and loop control.
- `Model` implementations provide blocking and opt-in streaming model invocation.
- `ToolExecutor` handles sequential or parallel tool execution, plus execution-context propagation.

### Spring Integration Shape

- `AgentFactory` is the central Spring integration boundary.
- Spring Boot auto-configuration wires defaults for model creation, tool discovery, session integration, and runtime-local builder flows.
- Named agents are expressed as configuration-layer defaults resolved through `AgentFactory.builder("name")`.

### Provider Boundary

- Bedrock-specific behavior stays centered on `BedrockModel` and its immediate helper logic.
- The rest of the runtime aims to stay provider-independent.

### Extension Boundaries

- Hooks and plugins stay centered on invocation lifecycle boundaries.
- Skills stay runtime-local and activate on demand rather than being injected wholesale.
- Steering remains an opt-in runtime-local layer, not a global policy engine.
- Sessions remain explicit at the `SessionManager` boundary and through Spring Session adapters.

## Current Engineering Observations

### Strengths

- The repository has broad runnable sample coverage across the shipped capability surface.
- The test suite is substantial relative to the current codebase size.
- PMD and CPD are currently clean in the latest quality snapshot.
- The core event loop and agent orchestration classes have strong coverage.

### Watch Areas

- `BedrockModel` remains the largest and lowest-covered architecturally important class.
- `AgentFactory` and `AgentFactory.Builder` remain central composition hotspots even after recent refactors.
- SpotBugs findings are still numerous and should eventually be triaged into fix, suppress, and defer buckets.
- Retry-related paths remain less covered than the event-loop happy path.

## Recommended Update Policy

Update this document when one of the following occurs:

- a repository-wide quality audit is refreshed
- a shipped capability area materially expands
- a new sample module is added or removed
- a major architectural hotspot is split or consolidated
- root verification totals or quality-gate baselines change meaningfully

Prefer command-derived numbers over hand-maintained estimates. When updating the quantitative snapshot, re-run these commands first:

```bash
mvn test
mvn -Pquality-report verify
mvn -Pquality-security verify
```

## Notes On Interpretation

- This document is descriptive, not normative.
- Coverage and static-analysis values are snapshots, not release gates by themselves.
- Line counts intentionally focus on the root library codebase and exclude generated `target/` output.