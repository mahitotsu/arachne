---
name: "locality-check"
description: "Judge whether the current work still fits one bounded repository area and one coherent read set."
argument-hint: "Optional scope. Example: current task only / changed files only / before refactor"
agent: "agent"
---

Judge whether the current work remains localized within the repository.

This prompt is read-only. Do not create, edit, delete, or commit anything while executing it.

Follow this procedure strictly.

1. Read [workspace instructions](../copilot-instructions.md), [repository snapshot](../repository-ops/repository-snapshot.md), [repository reading guide](../repository-ops/repository-reading-guide.md), and `/memories/repo/status.md`.
2. Use any user-specified scope first. Otherwise infer scope from the current conversation and actual changed files.
3. Map the touched files and the likely additional reading surface to one or more sections in `repository-reading-guide.md`.
4. Identify one primary area and compare the current change plus required reading against that area's bounded read set.
5. Judge locality using these questions:
   - does the work primarily stay inside one responsibility boundary
   - does the required reading stay close to one area's bounded read set
   - is the spread caused by real cross-cutting work, or by missing entry points and unclear ownership
   - is the work increasing repository structural debt
6. Classify the result as exactly one of:
   - `LOCALIZED`: the work stays mostly inside one area and one bounded read set
   - `BORDERLINE`: there is still a primary area, but reading or edits are starting to spill into adjacent areas
   - `NOT_LOCALIZED`: multiple areas are required as a starting point, or the change is spread enough that one bounded read set no longer explains it
7. If the result is `BORDERLINE` or `NOT_LOCALIZED`, recommend the shortest structural repair. Typical examples are a narrower README, a reading-guide update, a responsibility split, or a `/repository-structure-health` pass.
8. Report only confirmed facts. Mark anything unverified as `unconfirmed`.

Answer in this format:

```text
State: LOCALIZED | BORDERLINE | NOT_LOCALIZED
Decision: <one sentence>
Primary Area:
- <main area or `unconfirmed`>
Touched Scope:
- <file or area 1>
- <file or area 2>
Spread Signals:
- <signal 1>
- <signal 2>
Why It Is Local Or Not:
- <fact 1>
- <fact 2>
Recommended Next Action:
- <single next action>
```