# Repository Structure Health

Updated: 2026-04-20

This document defines how Arachne checks whether repository reading structure is staying bounded enough for efficient restart and localized work.

For this repository, structure-health remains a lightweight workflow-first policy. The current flow intentionally avoids introducing a Python-only checker dependency into the Java and TypeScript repository workflow. Until a dedicated checker becomes worth the maintenance cost, use the policy and threshold file together with simple shell inspection such as `wc -l` and section counts.

## Goal

- Keep normal work localized to one primary area and one bounded read set.
- Detect when the snapshot or reading guide becomes too broad to serve as a first-read map.
- Detect when a section needs too many entry points or too large a minimal read set.
- Keep threshold changes explicit and reviewable.

## Managed Files

- `repository-snapshot.md`
- `repository-reading-guide.md`
- `repository-structure-health-rules.json`
- `.github/skills/repository-structure-health/SKILL.md`

## Core Standard

- The real concern is not raw repository size. The real concern is whether one task can still begin from one primary area without broad rediscovery.
- `Localized work` means the change and its required reading stay mostly inside one area defined by `repository-reading-guide.md`.
- Snapshot size, reading-guide size, section count, entry-point count, and minimal-read-set size are proxy metrics for that locality.
- Use `.github/prompts/locality-check.prompt.md` when the diff itself may already be spreading beyond a healthy boundary.

## Trigger Types

### Snapshot Bloat

- Symptom: `repository-snapshot.md` starts behaving like a full guide or repeats detail from the reading guide.
- Typical fix: compress the snapshot and move detail back into `repository-reading-guide.md`.

### Reading Guide Fan-out

- Symptom: one section needs too many entry points, too many bullets, or too large a bounded read set.
- Typical fix: add a narrower README or update the section boundaries.

### Section Drift

- Symptom: a guide section is missing a bounded read set, verification note, or clear entry points.
- Typical fix: restore the missing block immediately.

### Workflow Drift

- Symptom: prompts, skills, snapshot, and repo status no longer describe the same operating flow.
- Typical fix: sync the affected files in one work unit and update `/memories/repo/status.md`.

## Threshold Handling

- The threshold source of truth is `repository-structure-health-rules.json`.
- Review thresholds manually when the observed structure sits near warning levels for multiple edits in a row, or when cleanup clearly reduced the needed size.
- Do not change thresholds just to avoid a warning. Prefer structural cleanup first.

## Manual Review Commands

Use commands like these from the repository root when you need a quick manual check:

```bash
wc -l .github/repository-ops/repository-snapshot.md .github/repository-ops/repository-reading-guide.md
rg '^## ' .github/repository-ops/repository-reading-guide.md
rg '^### ' .github/repository-ops/repository-structure-health.md
```

## Response Playbook

- If snapshot size drifts upward, compress it before adding more sections.
- If one guide section keeps growing, prefer a narrower README, a better first-read file, or a clearer section split.
- If workflow files drift, sync the prompts, skills, snapshot, and repo status together.
- If the diff itself is already spread across multiple areas, use `/locality-check` before editing more structure.