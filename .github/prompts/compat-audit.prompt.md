---
description: "Audit Arachne's compatibility with Strands Agents for a target capability area and produce an evidence-based gap report."
name: "Compatibility Audit"
argument-hint: "Capability area, Strands concept, or optional scope notes"
agent: "agent"
---
Audit Arachne's compatibility with Strands Agents for the target capability area identified by the slash-command arguments.

Use `docs/project-status.md` as the Arachne MVP boundary, and use `refs/sdk-python` plus the official Strands Agents documentation as behavioral reference material.

Requirements:

- Respond in Japanese unless the user explicitly asks for another language.
- Start by identifying the exact target area, the shipped Arachne scope around that area, and any explicitly deferred boundaries that affect the comparison.
- Build a concrete compatibility matrix that distinguishes at least these states:
  - implemented
  - partially implemented or approximate only
  - intentionally deferred or non-MVP
  - MVP target but still missing
- For each relevant feature, confirm the status with repository evidence from code, tests, docs, ADRs, or samples. Prefer direct evidence over inference.
- Treat `refs/sdk-python` as reference material only. Do not edit it.
- When Strands behavior is broader than Arachne's shipped MVP, say so explicitly instead of calling it a gap.
- If any feature appears to be MVP-scope but not implemented, include a concrete implementation plan ordered by dependency and risk.
- Do not make broad repository edits unless the user explicitly asks for them. If a tiny no-regret documentation correction is directly necessary to avoid a false claim in the report, keep it narrow and explain it.
- End the final report with these exact section headings in this order:
  - `互換性サマリー`
  - `機能マトリクス`
  - `MVP不足`
  - `推奨アクション`