---
description: "Detect unported Strands Agents features, identify the most relevant next implementation candidates for Arachne, and compare them with an evidence-based recommendation."
name: "Implementation Candidates"
argument-hint: "Optional scope, capability area, or product-direction notes"
agent: "agent"
---
Identify unported or deferred Strands Agents capabilities that are realistic next implementation candidates for Arachne, then compare and prioritize them.

Use `docs/project-status.md` as the current Arachne contract boundary, and use `refs/sdk-python` plus the official Strands Agents documentation as the feature reference baseline.

Requirements:

- Respond in Japanese unless the user explicitly asks for another language.
- Start by restating the target scope, the currently shipped Arachne boundary, and any explicitly deferred items that matter for prioritization.
- Detect candidate features that are not yet implemented, are only partially implemented, or are intentionally deferred but worth reconsidering.
- Do not treat every Strands feature as equally worth porting. Filter candidates by Arachne's architecture, current scope, and likely reuse value.
- For each serious candidate, confirm the current state with repository evidence from code, tests, docs, ADRs, and samples.
- Compare candidates using concrete dimensions such as:
  - user value
  - compatibility impact
  - architectural fit
  - implementation dependency chain
  - regression risk
  - documentation and test burden
  - whether an ADR or scope update would be required
- Distinguish clearly between:
  - candidate is already implemented
  - candidate is intentionally deferred and should stay deferred for now
  - candidate is viable next work
  - candidate depends on earlier work
- Recommend an execution order, not just a flat list.
- If the best candidate would require changing the published MVP or deferred boundary, say so explicitly instead of presenting it as straightforward backlog work.
- Do not make broad repository edits unless the user explicitly asks for them. This prompt is primarily for evidence gathering, comparison, and prioritization.
- End the final report with these exact section headings in this order:
  - `候補サマリー`
  - `未移行機能候補`
  - `比較と優先順位`
  - `推奨する次の実装`