---
description: "Audit a roadmap phase for completion gaps, stale documentation, missing ADRs, instruction drift, sample drift, and regression risk before or instead of closeout."
name: "Phase Audit"
argument-hint: "Phase number or name, plus optional notes"
agent: "agent"
---
Audit the roadmap phase identified by the slash-command arguments.

Use the workspace phase-closeout skill, but bias toward finding gaps and producing a crisp report before making broad edits.

Requirements:

- Respond in Japanese unless the user explicitly asks for another language.
- Start from ROADMAP.md and the relevant phase instruction files.
- Check completion against roadmap tasks, code, tests, docs, instructions, ADRs, and samples.
- Prefer reporting gaps and risks first. Only make no-regret fixes that are directly evidenced by the current repository state.
- Call out anything that would block phase completion or next-phase work.
- End with these Japanese sections in this order: `指摘事項`, `必須のフォローアップ`, `任意のフォローアップ`, ` /phase-closeout に進めるか `.
- Use those Japanese headings exactly in the final report.
