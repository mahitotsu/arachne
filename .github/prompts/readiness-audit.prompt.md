---
description: "Audit whether a finished Arachne change left the target area ready for the next bounded piece of work."
name: "Readiness Audit"
argument-hint: "Capability area, repository surface, or closeout notes"
agent: "agent"
---
Audit the target capability area or repository surface for closeout readiness.

This prompt is for the question: can the next worker restart in this area from a small, explicit, trustworthy surface without rediscovering scattered context?

Use the workspace `repository-audit` skill as the base procedure, then apply the repository readiness checklist at `.github/skills/repository-audit/references/repository-readiness-checklist.md`.

Requirements:

- Respond in Japanese unless the user explicitly asks for another language.
- Start from `docs/project-status.md`, `docs/repository-facts.md`, `docs/user-guide.md`, `.github/copilot-instructions.md`, and any relevant active instruction files.
- Treat readiness as stricter than quality or alignment alone. A target area is not ready if it is merely correct today but still requires broad rediscovery to continue tomorrow.
- Reuse existing quality and alignment evidence when it is fresh and scoped correctly. If the evidence is stale, incomplete, or for the wrong surface, say so and limit the readiness claim.
- Check implementation, tests, docs, ADRs, instructions, and samples for the target area.
- Explicitly evaluate:
  - whether the next worker has a small canonical set of entry points
  - whether the shipped boundary and current constraints are easy to find
  - whether residual work is classified instead of implied
  - whether temporary instruction files or phase guidance have gone stale
  - whether docs, samples, and ADRs create one clear source of truth instead of duplicated drift-prone summaries
- Distinguish clearly between:
  - `ready`
  - `ready with follow-ups`
  - `not ready`
  - `blocked`
- Always produce concrete repair guidance, not only status labels.
- For every `ready with follow-ups`, `not ready`, or `blocked` judgment, include fix candidates that are specific enough to implement without rediscovering the problem.
- Order fix candidates by repair priority. Prefer the smallest set of changes that would most reduce future context spread.
- Prefer reporting first. Only make no-regret fixes when the repository state already proves the correct correction.
- If the target area is not ready, say which missing mechanism would make it ready: boundary map, residual-work ledger, ADR update, sample refresh, instruction cleanup, or fresh verification.

End the final report with these exact section headings in this order:

- `準備状態サマリー`
- `次の作業者の開始面`
- `準備を妨げる要因`
- `修正候補と修復順`
- `推奨クローズアウト`