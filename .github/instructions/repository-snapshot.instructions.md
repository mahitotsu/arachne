---
description: "Use when creating or updating repository-ops docs, workflow prompts and skills, or the workspace operating flow for Arachne."
applyTo: "{.github/repository-ops/**,.github/copilot-instructions.md,.github/prompts/{close-action.prompt.md,locality-check.prompt.md,repository-metrics.prompt.md,session-handoff.prompt.md,ship-changes.prompt.md},.github/skills/{repo-snapshot/**,repository-ops/**,repository-structure-health/**}}"
---

# Repository Snapshot Maintenance Rules

- `repository-snapshot.md` is the slim current cross-section for session restart. Keep it short enough to serve as the first-read index.
- Move detailed entry points, bounded read sets, and deeper guidance into `repository-reading-guide.md`.
- When shipped capability boundaries, sample entry points, or verification expectations change, check whether `arachne/docs/project-status.md` and `arachne/docs/repository-facts.md` also need updates.
- When the repository operating flow changes, sync `.github/copilot-instructions.md`, the related prompts, and the related skills in the same work unit.
- Keep `repository-structure-health.md` and `repository-structure-health-rules.json` aligned. Do not update thresholds without also updating the policy wording when the intent changed.
- Keep `repository-metrics.md` and `repository-metrics-rules.json` aligned. Do not tighten thresholds without checking current branch values or explaining the policy reason.
- Prefer the smallest set of repo areas that lets the next worker start cleanly. Do not turn the snapshot or reading guide into a second user guide.
- If food-delivery `customer-ui` entry points or verification commands change, update the reading guide and any TypeScript-scoped instructions in the same turn.