# Repository Facts

This document is a lightweight engineering snapshot of the Arachne repository.

It is intended to complement, not replace, the normative project documents:

- `docs/README.md` for the documentation catalog and reading order
- `README.md` for entry points and quick start
- `docs/project-status.md` for shipped scope and deferrals
- `docs/user-guide.md` for API and usage guidance
- `docs/adr/` for architectural decisions

Update this document when a quality audit, major phase closeout, new sample module, or notable structural refactor changes the repository-wide picture.

## Scope Of This Snapshot

Structural counts below were refreshed against the checked-in repository state on 2026-03-27. Quality metrics reflect fresh local quality artifacts gathered on 2026-03-27, with the root Maven test result refreshed on 2026-03-28 after follow-up changes.

- Snapshot date: 2026-03-27
- Repository artifact: `io.arachne:arachne:0.1.0-SNAPSHOT`
- Java version: 21
- Spring Boot baseline: 3.5.12
- Primary built-in model provider: AWS Bedrock
- Default verification command: `mvn test`
- Quality snapshot commands:
  - `mvn -Pquality-report verify`
  - `mvn -Pquality-security verify`

## Quantitative Snapshot

These values reflect the checked-in repository state, the latest local quality artifacts gathered on 2026-03-27, and a fresh `mvn test` run from 2026-03-28.

### Core Code Volume

| Surface | Java files | LOC |
| --- | ---: | ---: |
| `src/main/java` | 98 | 8401 |
| `src/test/java` | 26 | 5524 |
| `samples/**/src/main/java` | 79 | not aggregated in this snapshot |
| `samples/**/src/test/java` | 3 | not aggregated in this snapshot |

### Verification And Quality Totals

| Metric | Value |
| --- | ---: |
| Root test methods (`@Test`) | 175 |
| Latest Maven test result | 175 run, 0 failures, 0 errors, 1 skipped |
| Instruction coverage | 84.78% |
| Branch coverage | 66.31% |
| SpotBugs findings | 50 |
| PMD violations | 2 |
| CPD duplications | 1 |
| ADR count | 17 |
| Sample modules | 12 |

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
| `DB_DUPLICATE_BRANCHES` | 1 |

### SBOM Snapshot

| Artifact | Timestamp |
| --- | --- |
| `target/dependency-bom.json` | 2026-03-27T14:21:04Z |
| `target/classes/META-INF/sbom/application.cdx.json` | fresh local artifact from 2026-03-27 quality-security run |

## Repository Layout

### Root Structure

- `src/`: the main library implementation and tests
- `docs/`: status, user guide, tool catalog, repository facts, ADRs, and the documentation catalog
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
| `built-in-tools` | 3 | 0 | built-in inheritance and allowlisted resource access |
| `conversation-basics` | 3 | 0 | basic multi-turn agent usage |
| `secure-downstream-tools` | 14 | 0 | security propagation and downstream API calls |
| `stateful-backend-operations` | 10 | 0 | idempotent backend mutations and workflow state |
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
- The static-analysis picture is still low-noise outside SpotBugs: PMD has 2 findings and CPD has 1 duplication in the latest quality snapshot.
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

## Quality Artifact Interpretation

Use the following artifacts as the primary evidence set for repository-level quality review:

- `target/site/jacoco/index.html` and `target/site/jacoco/jacoco.csv` for coverage
- `target/surefire-reports/` for test execution results
- `target/spotbugsXml.xml` for SpotBugs findings
- `target/pmd.xml` for PMD findings
- `target/cpd.xml` for CPD duplication findings
- `target/dependency-bom.json` and `target/dependency-bom.xml` for CycloneDX dependency inventory output

Current caveats for this repository state:

- treat `target/spotbugsXml.xml` as the primary SpotBugs source of truth for the current workflow
- do not treat `target/spotbugs.xml` as authoritative unless a future workflow refresh proves it contains the real findings again
- treat historical logs under `target/*.log` and stale rendered reports under `target/reports/` as non-primary unless they were explicitly regenerated for the current review
- when fresh and stale artifacts coexist, prefer artifacts that were regenerated by the current `quality-report` and `quality-security` runs and call out the stale leftovers explicitly

## Bedrock Live Evidence Workflow

Intentional Bedrock-backed verification should remain separate from ordinary repository verification.

Ordinary commands:

- `mvn test`
- `mvn -Pquality-report verify`
- `mvn -Pquality-security verify`

These commands should remain usable without Bedrock credentials. In ordinary runs, `BedrockModelIntegrationTest` is expected to stay skipped unless the live-run property is set.

### Root smoke command

Use the root Bedrock smoke test only when intentionally collecting live provider evidence:

```bash
mvn -Dtest=BedrockModelIntegrationTest \
  -Darachne.integration.bedrock=true \
  -Darachne.integration.bedrock.region=<aws-region> \
  -Darachne.integration.bedrock.model-id=<bedrock-model-id> \
  test
```

Prerequisites:

- valid AWS credentials with access to Bedrock runtime APIs
- access to the selected Bedrock model in the target region
- explicit intent to collect live evidence rather than ordinary local verification

Primary evidence artifacts for the root smoke path:

- `target/surefire-reports/TEST-io.arachne.strands.model.bedrock.BedrockModelIntegrationTest.xml`
- the invoking terminal output, if the run is being recorded for an audit or report

### Sample-backed workflow evidence

When the goal is stronger end-to-end Bedrock evidence rather than a minimal smoke test, use the `samples/domain-separation` live workflow:

```bash
cd samples/domain-separation
mvn -Dstyle.color=never spring-boot:run \
  -Dspring-boot.run.profiles=bedrock \
  -Dspring-boot.run.arguments='--sample.domain-separation.demo-logging.verbose-executor=true' \
  > bedrock-demo-capture.txt 2>&1
```

Primary evidence artifacts for the sample-backed path:

- `samples/domain-separation/bedrock-demo-capture.txt`
- `samples/domain-separation/BEDROCK-DEMO-REPORT.md`

Interpretation rule:

- do not mix Bedrock live-run evidence into ordinary quality conclusions unless the command, prerequisites, and artifact paths are stated explicitly in the same review
- treat skipped Bedrock integration tests during ordinary runs as expected behavior, not as missing baseline coverage

## SpotBugs Triage Snapshot

The current 50 SpotBugs findings are not evenly spread across unrelated defect classes. They are concentrated in a few recurring patterns, and those patterns should not all be treated the same way.

Current triage rule:

- `EI_EXPOSE_REP` and `EI_EXPOSE_REP2`: default to watch-level design noise unless a specific case can be fixed without changing the intended mutability contract. The current findings are concentrated in hook events, session snapshots, message/content holders, skill descriptors, tool metadata carriers, and constructor-injected collaborators such as `ObjectMapper` and Spring repositories.
- `CT_CONSTRUCTOR_THROW`: default to watch-level findings for validation-heavy constructors. These should stay visible, but they are not current closeout blockers by themselves.
- `DB_DUPLICATE_BRANCHES`: treat as a probable fix candidate. The current instance is in `BuiltInResourceAccessPolicy`, which is already a branch-sensitive safety area and should be reviewed together with the built-in resource test work.

Practical implication for follow-up work:

- do not try to drive the raw SpotBugs count to zero as an immediate quality goal
- first preserve the design-noise vs fix-candidate split
- use the duplicate-branches finding as the first direct code-level SpotBugs follow-up unless a later review finds a clearly unsafe mutability exposure

## Notes On Interpretation

- This document is descriptive, not normative.
- Coverage and static-analysis values are snapshots, not release gates by themselves.
- Line counts intentionally focus on the root library codebase and exclude generated `target/` output.