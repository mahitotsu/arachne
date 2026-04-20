---
name: repository-structure-health
description: 'Use when checking whether repository snapshot and reading-guide structure are still bounded enough for localized work.'
argument-hint: 'Optional scope. Example: after snapshot update / workflow refactor'
user-invocable: true
---

# Repository Structure Health

Use this skill when repository reading structure may have drifted and you need an operational workflow rather than only the policy document.

## Read First

- [workspace instructions](../../copilot-instructions.md)
- [repository structure health policy](../../repository-ops/repository-structure-health.md)
- [repository structure health rules](../../repository-ops/repository-structure-health-rules.json)
- [repository snapshot](../../repository-ops/repository-snapshot.md)
- [repository reading guide](../../repository-ops/repository-reading-guide.md)
- `/memories/repo/status.md`

## Use This Skill When

- `repository-snapshot.md` or `repository-reading-guide.md` changed
- workflow prompts or skills changed and the repo restart path may have drifted
- `/locality-check` says the work is borderline or not localized
- one reading-guide section is becoming the default catch-all area

## Procedure

1. Identify the affected scope: snapshot only, reading guide only, or broader workflow surfaces.
2. Read the policy and rules JSON and note the relevant thresholds.
3. Use lightweight inspection from the repository root, for example:
   - `wc -l .github/repository-ops/repository-snapshot.md .github/repository-ops/repository-reading-guide.md`
   - `rg '^## ' .github/repository-ops/repository-reading-guide.md`
4. Compare the observed shape against the rules JSON.
5. If the structure is drifting, choose the smallest repair:
   - compress snapshot detail
   - narrow a guide section
   - add a more precise README or entry file
   - sync prompts, skills, and repo status after workflow changes
6. Recheck the affected files after the repair.

## Response Rules

- Prefer structural cleanup over threshold inflation.
- Do not introduce a new tooling dependency only to avoid doing a small manual structure check.
- If the real problem is a diff that is already too broad, route to `/locality-check` and treat structure cleanup as the repair path.