---
name: repository-audit
description: 'Repository-side audit workflow for Arachne. Use when checking whether a capability area is implemented, documented, tested, and aligned across code, docs, ADRs, instructions, and samples.'
argument-hint: 'Capability area name, repository surface, or optional notes'
user-invocable: false
disable-model-invocation: false
---

# Repository Audit

Use this skill when a capability area needs a disciplined repository audit for alignment, evidence, and closeout readiness.

Load and follow the repository checklist in [repository-audit-checklist](./references/repository-audit-checklist.md).

## Inputs

- The slash-command arguments identify the target capability area or repository surface.
- If the scope is ambiguous, first check whether the current git changed files narrow it to one capability area or one repository surface.
- If git changed files do not yield a single defensible area, resolve the scope from `docs/project-status.md`, `docs/user-guide.md`, and the relevant ADRs before making edits.
- If the user asked for an audit rather than closeout, keep edits narrow and prioritize findings.
- Unless the user explicitly asks for another language, produce the report in Japanese.

## Required Procedure

1. Read `docs/project-status.md` and identify the exact shipped scope, current constraints, and deferred items relevant to the target area.
2. Read the repo instructions that govern the area, especially `.github/copilot-instructions.md` and any active `.github/instructions/*.instructions.md` files relevant to the touched code or tests.
3. Review the implementation, tests, and samples that claim to cover the target area.
4. Decide whether each documentation and workflow surface needs updates. At minimum, consider README.md, docs/user-guide.md, docs/project-status.md, sample READMEs, `.github/instructions/`, and docs/adr/.
5. Apply required updates in the same turn when the repository state gives enough evidence to do so safely.
6. Verify architectural invariants and prior-phase behavior have not drifted.
7. Run verification. Default to `mvn test` when code or behavior changed.
8. Produce an audit report with explicit status: aligned, misaligned, incomplete, or blocked.

## Guardrails

- Do not mark an area aligned just because most code exists. Check the shipped behavior, docs, tests, and any explicit task conditions directly.
- Do not skip the ADR decision gate when the change affects public API, Spring integration, lifecycle assumptions, session behavior, tool binding, hooks/plugins/interrupts, or other cross-phase boundaries.
- Do not skip sample review when the phase changes how users are expected to wire or run the library.
- Do not paper over missing tests. If behavior changed, add or update tests or explicitly report the gap.
- If a new implementation theme is about to begin, review and update the next implementation and test-strategy instruction files before implementation starts.

## Expected Output

Return a concise report in Japanese with these sections when relevant:

- Audit status
- Updates made
- Checks performed
- Open items or risks
- Recommendation for next action

Use natural Japanese section headings. When the invoking prompt specifies exact required headings or section order, follow that prompt.
