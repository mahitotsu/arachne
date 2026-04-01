---
description: "Refresh docs/project-status.md from the current repository truth surfaces without doing a broad readiness audit."
name: "Project Status Sync"
argument-hint: "Finished task, capability area, or optional sync notes"
agent: "agent"
---
Refresh `docs/project-status.md` so it matches the current repository truth surfaces.

This prompt is for keeping the branch-level status snapshot accurate. It is narrower than readiness audit and narrower than full closeout.

Use it when:

- a bounded task changed the shipped surface, current constraints, sample map, or current non-goals, but the rest of closeout is already handled
- a readiness or alignment audit found that `docs/project-status.md` drifted from code, tests, docs, ADRs, or samples
- multiple bounded tasks have landed and the branch snapshot needs consolidation before the next wave of work

Do not use it as the default workflow after every small implementation change that does not affect the branch-level snapshot.

Requirements:

- Respond in Japanese unless the user explicitly asks for another language.
- Start from `docs/project-status.md`, `docs/repository-facts.md`, `.github/copilot-instructions.md`, and the smallest implementation, test, sample, ADR, and instruction surfaces that prove the relevant current state.
- Treat `docs/project-status.md` as a branch snapshot, not as a changelog, task log, or speculative roadmap.
- Update only statements that the current repository state proves.
- Keep the document high-signal and bounded. Prefer short bullet updates over narrative expansion.
- Preserve the document structure unless the current structure itself now causes ambiguity or drift.
- If the current repository state is not strong enough to justify a `project-status` edit, say so and report what evidence is missing.
- When the user asked for status sync only, do not broaden into a repository-wide audit unless the repository state forces that conclusion.

Before editing, explicitly check whether the bounded change affected any of these branch-level surfaces:

- available today
- start with these resources
- current constraints
- current non-goals
- related documents

If none changed, report that `docs/project-status.md` does not need an update.

End the final report with these exact section headings in this order:

- `同期対象`
- `更新要否判定`
- `更新した内容`
- `更新根拠`
- `残る不確実性`
- `推奨アクション`