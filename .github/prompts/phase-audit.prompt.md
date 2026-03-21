---
description: "Audit a capability area or historical phase for completion gaps, stale documentation, missing ADRs, instruction drift, sample drift, and regression risk before or instead of closeout."
name: "Phase Audit"
argument-hint: "Capability area or historical phase name, plus optional notes"
agent: "agent"
---
Audit the target capability area identified by the slash-command arguments.

Use the workspace phase-closeout skill, but bias toward finding gaps and producing a crisp report before making broad edits.

Requirements:

- Respond in English unless the user explicitly asks for another language.
- Start from `docs/project-status.md`, `docs/user-guide.md`, and the relevant instruction files.
- Check completion against shipped behavior, code, tests, docs, instructions, ADRs, and samples.
- Prefer reporting gaps and risks first. Only make no-regret fixes that are directly evidenced by the current repository state.
- Call out anything that would block closeout or the next implementation theme.
- End with these section headings in this order: `Findings`, `Required Follow-up`, `Optional Follow-up`, `Ready For /phase-closeout`.
- Use those headings exactly in the final report.
