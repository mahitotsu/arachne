# Quality Evaluation Workflow

This document defines Arachne's quality evaluation workflow for observation and diagnosis.
It is intentionally separate from build-failing quality gates.

## Goal

Collect repeatable quality evidence for the current repository state and turn it into a repo-specific evaluation report.

The workflow must answer these questions without changing the default contributor flow:

- How large is the codebase and where are the hotspots?
- What test evidence exists and where are the weak areas?
- What static analysis findings deserve attention?
- Are there dependency risks that should be reviewed?
- Do the findings align with Arachne's architecture and roadmap constraints?

## Non-goals

- Do not fail `mvn test` based on coverage or static-analysis thresholds.
- Do not use report generation as a substitute for targeted tests.
- Do not treat one aggregate score as the quality result.
- Do not require security scanning for every local inner-loop run.

## Report Profiles

### `quality-report`

Use for routine local observation.

Command:

```bash
mvn -Pquality-report verify
```

Artifacts produced under `target/` or `target/site/` include:

- JaCoCo coverage report: `target/site/jacoco/`
- SpotBugs findings: `target/spotbugsXml.xml` and `target/site/spotbugs.html`
- PMD findings: `target/pmd.xml`
- CPD duplication report: `target/cpd.xml`
- Surefire XML test results: `target/surefire-reports/`

This profile is observational. It must not introduce coverage gates or fail-on-violation behavior.

### `quality-security`

Use for lightweight dependency-inventory observation.

Command:

```bash
mvn -Pquality-security verify
```

Artifacts produced under `target/` include:

- CycloneDX SBOM JSON: `target/dependency-bom.json`
- CycloneDX SBOM XML: `target/dependency-bom.xml`

This profile stays observational and is intended to remain fast enough for local use.
It captures dependency inventory, not advisory matching.

Vulnerability monitoring is handled outside the Maven inner loop:

- GitHub Dependency Graph and Dependabot alerts provide repository-level advisory matching
- Dependabot version updates keep stale dependencies visible in pull requests

## Evaluation Flow

1. Run `mvn test` when validating behavior changes.
2. Run `mvn -Pquality-report verify` to collect routine quality evidence.
3. Run `mvn -Pquality-security verify` when you want a fresh dependency inventory and SBOM.
4. Feed the generated artifacts into the quality evaluation prompt.
5. Review the resulting report in the context of ROADMAP phase boundaries, architecture invariants, tests, samples, docs, and any repository-side dependency alerts.

## Evaluation Criteria

The final quality evaluation should cover both measured outputs and repo-specific interpretation.

- Codebase size, file concentration, and obvious maintenance hotspots
- Test inventory and coverage gaps in critical packages
- Static-analysis findings that indicate correctness or maintainability risks
- Duplicate-code patterns worth refactoring
- Dependency inventory, upgrade surface, and any available repository-side vulnerability signals
- Alignment with Arachne invariants such as core flow readability, Spring integration boundaries, and provider isolation
- Drift between code, samples, README, and user guide when relevant

## Skill Introduction Gate

Start with the prompt-only workflow.
Introduce a dedicated skill only when all of the following are true:

- the quality evaluation prompt has been used repeatedly and the output structure is stable
- the report inputs and expected artifacts no longer change from run to run
- the team wants a repeatable workflow comparable to `phase-audit` or `phase-closeout`

Until then, keep the workflow lightweight: profiles for evidence, prompt for interpretation.

## Verification Expectations

- Changes to production code still use the repo default verification command: `mvn test`
- Changes limited to report plumbing should verify that `mvn test` still passes
- When report plugin configuration changes, also run the relevant profile at least once before relying on the prompt output

## Repository Monitoring

Repository-side dependency monitoring is configured through Dependabot so the local Maven quality workflow can stay lightweight.