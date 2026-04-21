# Repository Metrics Latest

Updated: 2026-04-21

This file is the latest persisted repository-metrics evaluation for Arachne.
Raw build and analysis outputs still live under `arachne/target/`.
This file is the latest summarized evaluation, not the source report.

## Scope

- `arachne/` library module only
- `samples/` and `food-delivery-demo/` not evaluated in this snapshot

## Freshness

- build and test status: partial fresh evidence from `mvn -f arachne/pom.xml -Dtest=DocumentedYamlExamplesTest test` on 2026-04-21
- coverage and static analysis: read from existing `arachne/target/` reports; freshness `unconfirmed`
- report-only context counts: collected on 2026-04-21 from the working tree

## Overall State

- State: `INCOMPLETE`
- Decision: current library metrics are mostly healthy, but the persisted coverage and static-analysis outputs were reused without a fresh `quality-report` run in this work unit.

## Thresholded Metrics

| Metric | Value | Status |
| --- | --- | --- |
| Library Maven test status | partial pass evidence only | unconfirmed |
| JaCoCo line coverage | 87.76% | ok |
| JaCoCo branch coverage | 73.70% | ok |
| SpotBugs total bug count | 106 | ok |
| PMD violation count | 0 | ok |
| CPD duplication-group count | 0 | ok |

## Context Metrics

| Metric | Value |
| --- | --- |
| Main Java file count | 109 |
| Test Java file count | 37 |
| Sample README count | 14 |
| Operator console TypeScript source-file count | 14 |

## Next Action

- Run `mvn -f arachne/pom.xml -Pquality-report verify` before the next formal repository-metrics pass if you want a fully fresh saved snapshot.