---
description: "Audit whether Arachne's implementation, tests, docs, ADRs, instructions, and samples are aligned for a target capability area."
name: "Alignment Audit"
argument-hint: "Capability area, repository surface, or optional scope notes"
agent: "agent"
---
Audit the target capability area identified by the slash-command arguments for internal alignment across the Arachne repository.

Use the workspace `repository-audit` skill and its checklist as the repository-specific audit procedure, but bias toward evidence gathering, drift detection, and a crisp report rather than a completion claim.

Requirements:

- Respond in Japanese unless the user explicitly asks for another language.
- Start from `docs/project-status.md`, `docs/user-guide.md`, `.github/copilot-instructions.md`, and the relevant instruction files.
- Compare implementation, tests, README.md, docs/user-guide.md, docs/project-status.md, `.github/instructions/`, relevant ADRs, and sample READMEs for the target area.
- Identify both under-documentation and overstatement. A stale or overly broad claim is a defect in alignment, not a minor wording issue.
- Call out mismatches between shipped behavior and public documentation, mismatches between code and tests, sample drift, instruction drift, and missing ADR follow-up.
- Distinguish clearly between:
  - confirmed aligned
  - misaligned
  - incomplete or missing evidence
- Prefer reporting first. Only make no-regret fixes when the correct update is directly evidenced by the current repository state.
- If an issue should block future implementation work, say so explicitly.
- End the final report with these exact section headings in this order:
  - `整合性サマリー`
  - `確認した証拠`
  - `不整合と過不足`
  - `推奨フォローアップ`