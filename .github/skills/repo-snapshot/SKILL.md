---
name: repo-snapshot
description: 'Use when refreshing the repository snapshot, reading guide, and repo status for the current Arachne branch.'
argument-hint: 'Optional scope. Example: workflow only / full refresh'
user-invocable: true
---

# Repository Snapshot Maintenance

Use this skill when the repository needs a refreshed current cross-section rather than a broad readiness audit.

## Update Targets

- [repository snapshot](../../repository-ops/repository-snapshot.md)
- [repository reading guide](../../repository-ops/repository-reading-guide.md)
- [repository structure health policy](../../repository-ops/repository-structure-health.md)
- [repository structure health rules](../../repository-ops/repository-structure-health-rules.json)
- `/memories/repo/status.md`
- [workspace instructions](../../copilot-instructions.md) when the operating flow changed
- [arachne/docs/project-status.md](../../../arachne/docs/project-status.md) and [arachne/docs/repository-facts.md](../../../arachne/docs/repository-facts.md) when shipped surface, entry points, or verification expectations changed

## What To Record

- the current repository cross-section for the branch
- the bounded first-read surfaces for each major area
- the current workflow entry points for repo restart, locality, ship, and handoff
- the next-session repo status in `/memories/repo/status.md`

## Procedure

1. Read [workspace instructions](../../copilot-instructions.md), [repository snapshot](../../repository-ops/repository-snapshot.md), [arachne/docs/project-status.md](../../../arachne/docs/project-status.md), [arachne/docs/repository-facts.md](../../../arachne/docs/repository-facts.md), and `/memories/repo/status.md`.
2. Check the current git working tree and infer the intended scope from the latest conversation and diff.
3. Refresh `repository-snapshot.md` and `repository-reading-guide.md` so they describe the current branch and the bounded starting surfaces.
4. If the operating flow changed, sync [workspace instructions](../../copilot-instructions.md), the related prompts and skills, and `/memories/repo/status.md` in the same work unit.
5. If the change altered shipped capability boundaries, sample entry points, or verification commands, sync [arachne/docs/project-status.md](../../../arachne/docs/project-status.md) and [arachne/docs/repository-facts.md](../../../arachne/docs/repository-facts.md) as needed.
6. If the reading structure changed materially, review whether [repository structure health](../../repository-ops/repository-structure-health.md) or its thresholds also need an update.

## Editing Rules

- Keep the snapshot slim and current, not historical.
- Keep the reading guide bounded and area-oriented, not exhaustive.
- Prefer links to ADRs, READMEs, and docs instead of copying long rationale into the snapshot.