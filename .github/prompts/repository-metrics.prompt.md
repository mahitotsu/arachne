---
name: "repository-metrics"
description: "Collect and evaluate quantitative repository health metrics using existing Maven and report outputs."
argument-hint: "Optional scope. Example: library only / include marketplace / fresh coverage"
agent: "agent"
---

Collect and evaluate quantitative repository metrics for Arachne.

This prompt may update `../repository-ops/repository-metrics-latest.md`, but it must not edit unrelated files or commit anything.

Follow this procedure strictly.

1. Read [workspace instructions](../copilot-instructions.md), [repository snapshot](../repository-ops/repository-snapshot.md), [repository metrics policy](../repository-ops/repository-metrics.md), [latest repository metrics](../repository-ops/repository-metrics-latest.md), [repository metrics rules](../repository-ops/repository-metrics-rules.json), and `/memories/repo/status.md`.
2. Infer the requested scope. Default to `arachne/` library only unless the user explicitly asks to include `samples/` or `marketplace-agent-platform/`.
3. Prefer outputs that are already fresh in the current work unit. If freshness matters and the relevant outputs are missing or stale:
   - build and test status: `mvn -f arachne/pom.xml test`
   - coverage and static analysis: `mvn -f arachne/pom.xml -Pquality-report verify`
   - marketplace operator console build status: in `marketplace-agent-platform/operator-console`, run `npm ci` and `npm run build`
4. Collect the thresholded library metrics from existing outputs:
   - build and test command result
   - JaCoCo line and branch coverage from `arachne/target/site/jacoco/jacoco.csv`
   - SpotBugs total bug count from `arachne/target/spotbugsXml.xml`
   - PMD violation count from `arachne/target/pmd.xml`
   - CPD duplication-group count from `arachne/target/cpd.xml`
5. Collect report-only context metrics only when they help explain the result or the user explicitly asked for scale data, for example:
   - `rg --files arachne/src/main/java -g '*.java' | wc -l`
   - `rg --files arachne/src/test/java -g '*.java' | wc -l`
   - `rg --files marketplace-agent-platform/operator-console/src -g '*.{ts,tsx}' | wc -l`
6. Compare the thresholded metrics against `repository-metrics-rules.json`. Mark missing or stale data as `unconfirmed`; do not invent zeros or pretend stale reports are fresh.
7. Classify the result as exactly one of:
   - `HEALTHY`: all thresholded metrics are present and `ok`
   - `WATCH`: at least one thresholded metric is at warning level, but nothing is critical and build/test still pass
   - `ACTION_NEEDED`: build/test failed or at least one thresholded metric is critical
   - `INCOMPLETE`: the requested evaluation depends on missing or stale data that you did not refresh
8. Update `repository-metrics-latest.md` with the evaluated scope, freshness notes, thresholded metrics, context metrics, gaps, state, decision, and next action.
9. Recommend the shortest next action that addresses the highest-severity signal first.

Answer in this format:

```text
State: HEALTHY | WATCH | ACTION_NEEDED | INCOMPLETE
Decision: <one sentence>
Scope:
- <scope item 1>
- <scope item 2 or `none`>
Thresholded Metrics:
- <metric name>: <value or status> | <ok/warning/critical/unconfirmed>
- <metric name>: <value or status> | <ok/warning/critical/unconfirmed>
Context Metrics:
- <context metric or `none`>
- <context metric or `none`>
Gaps:
- <gap or `none`>
Recommended Next Action:
- <single next action>
```