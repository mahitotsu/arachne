---
description: "Close out a completed capability area or historical phase with repository-specific checks for status docs, README, user guide, instructions, ADRs, samples, tests, and regressions."
name: "Phase Closeout"
argument-hint: "Capability area or historical phase name, plus optional notes"
agent: "agent"
---
Close out the completed capability area identified by the slash-command arguments.

Use the workspace phase-closeout skill and follow its checklist strictly.

Requirements:

- Respond in Japanese unless the user explicitly asks for another language.
- Treat this as a completion workflow, not a brainstorming session.
- Start from `docs/project-status.md`, `docs/user-guide.md`, and the relevant instruction files.
- Confirm whether README.md, docs/user-guide.md, docs/project-status.md, .github/instructions/*.instructions.md, docs/adr/*, and sample READMEs need updates. If they do, make the updates in the same turn.
- Review whether the target area is actually complete. If any explicit completion condition is still open, report that clearly instead of forcing a completion claim.
- Check that the change set preserves prior architecture and previously completed phases.
- Run the relevant verification. If behavior or code changed, default to mvn test unless there is a narrower command that is clearly sufficient.
- End with a concise Japanese report containing: completed updates, checks performed, open items or residual risks, and whether the target area is ready to be marked complete.
- Prefer Japanese section headings in the final report.
