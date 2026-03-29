---
description: "Assess whether the current Arachne repository is broadly ready for the next wave of work by partitioning it into bounded readiness areas."
name: "Repository Readiness Audit"
argument-hint: "Optional focus, breadth limit, priority hints, or closeout notes"
agent: "agent"
---
Assess repository-wide readiness for the current Arachne branch by partitioning the repository into meaningful readiness areas, auditing each area in turn, and then summarizing whether the branch is ready for the next bounded work.

Use the workspace `repository-audit` skill as the base procedure, and use `.github/skills/repository-audit/references/repository-readiness-checklist.md` as the closeout-readiness checklist.

Requirements:

- Respond in Japanese unless the user explicitly asks for another language.
- Start by reading `docs/project-status.md`, `docs/repository-facts.md`, `docs/user-guide.md`, `.github/copilot-instructions.md`, and any relevant active instruction files.
- Partition the repository by shipped capability areas and cross-cutting repository surfaces, not by arbitrary package boundaries.
- Prefer roughly 5 to 10 readiness areas unless the repository state gives a strong reason to use fewer or more.
- Use git changed files only as a prioritization hint. Do not let changed files replace repository-wide partitioning.
- For each readiness area, identify the canonical docs, implementation entry points, tests, samples, ADRs, and instruction files before judging readiness.
- Reuse existing quality or alignment evidence when it is fresh, but do not assume readiness from those audits alone.
- If sample-side evidence will be gathered via `samples/pom.xml`, refresh the root snapshot first with `mvn install -DskipTests` so the sample reactor does not read a stale local `io.arachne:arachne` snapshot.
- Classify each readiness area as one of:
  - `ready`
  - `ready with follow-ups`
  - `not ready`
  - `blocked`
- For every area that is not fully `ready`, produce concrete fix candidates and a recommended repair order.
- Prefer repair candidates that reduce future reading spread, remove duplicate sources of truth, or make residual work land somewhere explicit.
- Call out cross-cutting readiness defects separately when they increase the amount of context a future worker must absorb, such as duplicated sources of truth, stale implementation-theme instructions, or unclassified residual work.
- If the requested sweep is too broad to finish confidently in one pass, still produce the partition, audit the highest-value areas first, and state which areas remain instead of pretending whole-repository coverage.
- Prefer reporting first. Only make no-regret fixes when the repository state directly proves the correct update.

End the final report with these exact section headings in this order:

- `準備状態の分割`
- `領域別準備状態`
- `横断的な準備阻害要因`
- `修正候補と推奨修復順`
- `レポジトリ準備状態サマリー`
- `推奨アクション`