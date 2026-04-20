# Repository Metrics

Updated: 2026-04-21

This document defines how Arachne collects and evaluates quantitative repository metrics without turning the repository snapshot into a dashboard.

Raw metric sources live in build outputs such as `arachne/target/site/jacoco/jacoco.csv`, `arachne/target/spotbugsXml.xml`, `arachne/target/pmd.xml`, and `arachne/target/cpd.xml`.
The persisted summarized evaluation lives in `repository-metrics-latest.md`.

## Goal

- Collect a small set of action-oriented metrics for repository health.
- Keep quantitative checks separate from restart guidance and reading-structure guidance.
- Reuse existing Maven, JaCoCo, SpotBugs, PMD, and CPD outputs before adding new tooling.
- Report missing or stale data explicitly instead of manufacturing false precision.

## Managed Files

- `repository-metrics.md`
- `repository-metrics-latest.md`
- `repository-metrics-rules.json`
- `.github/prompts/repository-metrics.prompt.md`
- `.github/skills/repository-ops/SKILL.md` when routing changes

## Core Standard

- Prefer metrics that map to a maintenance action. Do not collect vanity metrics that do not change decisions.
- Separate `thresholded health metrics` from `report-only context metrics`.
- Keep the default scope on the `arachne/` library module unless the user explicitly asks for `samples/` or `marketplace-agent-platform/`.
- Treat missing or stale reports as `unconfirmed`, not as zero.
- Do not turn one point-in-time measurement into synthetic trend analysis. If historical comparison matters, record it deliberately elsewhere.
- Persist the latest evaluated summary in `repository-metrics-latest.md`, but keep raw report ownership in `arachne/target/`.

## Metric Groups

### Thresholded Health Metrics

- library build and test command result
- JaCoCo line coverage
- JaCoCo branch coverage
- SpotBugs total bug count
- PMD violation count
- CPD duplication-group count

### Report-Only Context Metrics

- `arachne/src/main/java` file count
- `arachne/src/test/java` file count
- optional sample README count when sample scope is requested
- optional operator-console TypeScript source-file count when marketplace scope is requested

## Freshness Rules

- Reuse outputs from the current work unit when they are already fresh enough for the user’s question.
- If build and test status is missing or stale, run `mvn -f arachne/pom.xml test`.
- If coverage or static-analysis outputs are missing or stale and the user asked for fresh numbers, run `mvn -f arachne/pom.xml -Pquality-report verify`.
- Extend into `samples/` or `marketplace-agent-platform/` only when the user explicitly asked for those surfaces.
- If fresh metrics would be too expensive for the current task, report the missing freshness as `INCOMPLETE` instead of guessing.
- Update `repository-metrics-latest.md` whenever `/repository-metrics` completes, even when the result is `INCOMPLETE`.

## Evaluation Rules

- For `minimum` metrics, values below `critical` are `critical`, values below `warning` are `warning`, and values at or above `warning` are `ok`.
- For `maximum` metrics, values above `critical` are `critical`, values above `warning` are `warning`, and values at or below `warning` are `ok`.
- For `pass-fail` metrics, any failed command is `critical`.
- `report-only` metrics provide context and do not determine the overall state by themselves.

## Manual Collection Commands

Use commands like these from the repository root when you need a quick metrics pass:

```bash
rg --files arachne/src/main/java -g '*.java' | wc -l
rg --files arachne/src/test/java -g '*.java' | wc -l
awk -F, 'NR>1{lm+=$8; lc+=$9; bm+=$6; bc+=$7} END {printf "line=%.4f\nbranch=%.4f\n", lc/(lc+lm), bc/(bc+bm)}' arachne/target/site/jacoco/jacoco.csv
rg -o "total_bugs='[0-9]+'" arachne/target/spotbugsXml.xml
rg '<violation\b' arachne/target/pmd.xml
rg '<duplication\b' arachne/target/cpd.xml
```

## Response Playbook

- If build or test status failed, classify the result as `ACTION_NEEDED`.
- If any thresholded metric is critical, classify the result as `ACTION_NEEDED`.
- If no metric is critical but at least one thresholded metric is at warning level, classify the result as `WATCH`.
- If required metrics are missing or stale and you did not refresh them, classify the result as `INCOMPLETE`.
- If all thresholded metrics are present and `ok`, classify the result as `HEALTHY`.
- Recommend the shortest next action that addresses the highest-severity signal first.
- Write the collected scope, freshness notes, evaluated metrics, and next action into `repository-metrics-latest.md`.