# Repository Metrics Latest

Updated: 2026-05-09

This file is the latest persisted repository-metrics evaluation for Arachne.
Raw build and analysis outputs still live under `arachne/target/`.
This file is the latest summarized evaluation, not the source report.

## Scope

- `arachne/` library module only
- `samples/` and `food-delivery-demo/` not evaluated in this snapshot

## Freshness

- build and test status: fresh from `mvn -f arachne/pom.xml test` on 2026-05-09
- coverage and static analysis: fresh from `mvn -f arachne/pom.xml -Pquality-report verify` on 2026-05-09
- report-only context counts: collected on 2026-05-09 from the working tree

## Overall State

- State: `HEALTHY`
- Decision: all thresholded library metrics are present and within configured limits after fixing CPD duplication and correcting SpotBugs exclusion matching.

## Thresholded Metrics

| Metric | Value | Status |
| --- | --- | --- |
| Library Maven test status | BUILD SUCCESS (262 run, 0 failed, 0 errors, 2 skipped) | ok |
| JaCoCo line coverage | 87.79% | ok |
| JaCoCo branch coverage | 74.29% | ok |
| SpotBugs total bug count | 0 | ok |
| PMD violation count | 0 | ok |
| CPD duplication-group count | 0 | ok |

## Context Metrics

| Metric | Value |
| --- | --- |
| Main Java file count | 109 |
| Test Java file count | 37 |

## Gaps

- none

## Next Action

- No immediate remediation required; keep the normal quality gate by running `mvn -f arachne/pom.xml -Pquality-report verify` in regular repository-metrics passes.