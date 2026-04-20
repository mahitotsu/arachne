---
name: "session-handoff"
description: "Generate a paste-ready handoff prompt for the next session. Return a single fenced code block only."
argument-hint: "Optional scope. Example: current task only / include open risks"
agent: "agent"
---

Create one handoff prompt that can be pasted directly into the next session.

This prompt is read-only. Do not create, edit, delete, update memory, or change git state while executing it.

Follow this procedure strictly.

1. Read [workspace instructions](../copilot-instructions.md), [repository snapshot](../repository-ops/repository-snapshot.md), and `/memories/repo/status.md`.
2. Read [repository reading guide](../repository-ops/repository-reading-guide.md) and the nearest README or instruction only when the current scope needs them.
3. Use the current conversation, real git state, and changed files to summarize what actually advanced in this session, what remains open, and what the next worker should do first.
4. Do not update snapshot, repo status, or any other file as part of this prompt.
5. If workflow or repository-operation files changed, include the exact files the next worker should reread and why.
6. Report only confirmed facts. Mark anything unverified as `unconfirmed` instead of guessing.
7. Include at least these parts inside the handoff prompt:
   - the current goal or continuation theme
   - the first files to read
   - what completed in this session
   - what remains open, blocked, or risky
   - the first concrete next action
   - the first verification command to rerun
8. Return exactly one fenced code block and nothing else.
9. Write the content as direct instructions to the next session's AI worker.
10. Respect any user-specified scope and do not pull in unrelated TODO items.

Return exactly this shape:

```text
<paste-ready handoff prompt>
```