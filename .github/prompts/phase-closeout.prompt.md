---
description: "Close out a completed roadmap phase with repository-specific checks for ROADMAP, README, user guide, instructions, ADRs, samples, tests, and regressions."
name: "Phase Closeout"
argument-hint: "Phase number or name, plus optional notes"
agent: "agent"
---
Close out the completed roadmap phase identified by the slash-command arguments.

Use the workspace phase-closeout skill and follow its checklist strictly.

Requirements:

- Treat this as a completion workflow, not a brainstorming session.
- Start from ROADMAP.md and the relevant phase instruction files.
- Confirm whether README.md, docs/user-guide.md, .github/instructions/*.instructions.md, docs/adr/*, and sample READMEs need updates. If they do, make the updates in the same turn.
- Review whether the target phase is actually complete. If any roadmap task or explicit completion condition is still open, report that clearly instead of forcing a completion claim.
- Check that the change set preserves prior architecture and previously completed phases.
- Run the relevant verification. If behavior or code changed, default to mvn test unless there is a narrower command that is clearly sufficient.
- End with a concise report containing: completed updates, checks performed, open items or residual risks, and whether the phase is ready to be marked complete.
