---
description: "Partition Arachne into meaningful audit areas, run alignment audits area-by-area, and summarize whole-repository alignment."
name: "Repository Alignment Audit"
argument-hint: "Optional focus, breadth limit, priority hints, or notes"
agent: "agent"
---
Assess repository-wide alignment for the current Arachne branch by partitioning the repository into meaningful audit areas, auditing each area in turn, and then summarizing the whole-repository result.

Use the workspace `repository-audit` skill and its checklist as the per-area audit procedure, but do not treat the whole repository as one flat target.

Requirements:

- Respond in Japanese unless the user explicitly asks for another language.
- Start by reading `docs/project-status.md`, `docs/repository-facts.md`, `docs/user-guide.md`, `.github/copilot-instructions.md`, and any relevant active instruction files.
- Derive a repository partition from shipped capability areas and public repository surfaces rather than from arbitrary package boundaries.
- Keep the partition meaningful and manageable. Prefer roughly 5 to 10 audit areas unless the repository state gives a strong reason to use fewer or more.
- When useful, use current git changed files as a prioritization hint or to explain why an area deserves extra attention, but do not let changed files replace the repository-wide partitioning step.
- For each audit area, identify the relevant implementation, tests, docs, sample READMEs, ADRs, and instruction files before judging alignment.
- Audit each area sequentially and classify it clearly as:
  - aligned
  - misaligned
  - incomplete or missing evidence
  - blocked
- Call out cross-cutting repository issues separately when they affect multiple audit areas.
- Prefer reporting first. Only make no-regret fixes when the correct update is directly evidenced by the current repository state and the change is narrow.
- If the requested sweep is too broad to finish confidently in one pass, still produce the partition, audit the highest-value areas first, and state which areas remain for a later pass instead of pretending coverage is complete.
- End the final report with these exact section headings in this order:
  - `監査分割`
  - `領域別監査結果`
  - `横断的な不整合`
  - `レポジトリ整合性サマリー`
  - `推奨フォローアップ`