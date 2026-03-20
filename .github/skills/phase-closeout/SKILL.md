---
name: phase-closeout
description: 'Phase closeout and phase audit workflow for Arachne. Use when finishing a roadmap phase or checking whether a phase is truly complete. Covers ROADMAP review, README and user guide updates, instruction review, ADR review, sample drift, regression checks, and test evidence.'
argument-hint: 'Phase number or name, plus optional notes'
user-invocable: false
disable-model-invocation: false
---

# Phase Closeout

Use this skill when a roadmap phase is believed to be complete and needs a disciplined closeout, or when you need a pre-closeout audit.

Load and follow the repository checklist in [closeout-checklist](./references/closeout-checklist.md).

## Inputs

- The slash-command arguments identify the target phase.
- If the phase is ambiguous, resolve it from ROADMAP.md before making edits.
- If the user asked for an audit rather than closeout, keep edits narrow and prioritize findings.
- Unless the user explicitly asks for another language, produce the report in Japanese.

## Required Procedure

1. Read ROADMAP.md and identify the exact phase goal and task list.
2. Read the repo instructions that govern the phase, especially `.github/copilot-instructions.md` and any active `.github/instructions/*.instructions.md` files relevant to the touched code or tests.
3. Review the implementation, tests, and samples that claim to cover the target phase.
4. Decide whether each documentation and workflow surface needs updates. At minimum, consider README.md, docs/user-guide.md, sample READMEs, ROADMAP.md, `.github/instructions/`, and docs/adr/.
5. Apply required updates in the same turn when the repository state gives enough evidence to do so safely.
6. Verify architectural invariants and prior-phase behavior have not drifted.
7. Run verification. Default to `mvn test` when code or behavior changed.
8. Produce a closeout report with explicit status: complete, incomplete, or blocked.

## Guardrails

- Do not mark a phase complete just because most code exists. Check the roadmap tasks and stated goal directly.
- Do not skip the ADR decision gate when the change affects public API, Spring integration, lifecycle assumptions, session behavior, tool binding, hooks/plugins/interrupts, or other cross-phase boundaries.
- Do not skip sample review when the phase changes how users are expected to wire or run the library.
- Do not paper over missing tests. If behavior changed, add or update tests or explicitly report the gap.
- If next-phase work is about to begin, review and update the next phase implementation and test-strategy instruction files before implementation starts.

## Expected Output

Return a concise report in Japanese with these sections when relevant:

- Phase status
- Updates made
- Checks performed
- Open items or risks
- Recommendation for next action

Use natural Japanese section headings. When the invoking prompt specifies exact required headings or section order, follow that prompt.
