---
description: "Close out a finished Arachne task so the repository stays trustworthy and easy to re-enter for the next worker."
name: "Closeout"
argument-hint: "Finished task, capability area, or optional closeout notes"
agent: "agent"
---
Close out a finished task, capability area, or repository surface.

This prompt is not a broad audit. Its job is to finish the task properly: confirm the completed boundary, close the relevant truth surfaces, land every leftover item somewhere explicit, and leave a small re-entry surface for the next worker.

Use the workspace `repository-audit` skill as the base repository procedure. Apply `.github/skills/repository-audit/references/repository-closeout-checklist.md` as the required closeout protocol, and use `.github/skills/repository-audit/references/repository-readiness-checklist.md` when judging whether the area is easy to resume.

The closeout decision must answer one operational question explicitly: may the worker now treat this task boundary as finished, commit the resulting work as complete for that boundary, and end the session without leaving implicit repository drift behind?

Requirements:

- Respond in Japanese unless the user explicitly asks for another language.
- Start from `docs/project-status.md`, `docs/repository-facts.md`, `docs/user-guide.md`, `.github/copilot-instructions.md`, and any relevant active instruction files.
- Reconfirm the exact finished boundary before making any closeout claim.
- Treat `docs/project-status.md` update as part of closeout whenever the finished boundary changed the branch-level shipped surface, sample map, current constraints, current non-goals, or related document map.
- If only `docs/project-status.md` drift remains after the rest of closeout is already done, use `.github/prompts/project-status-sync.prompt.md` instead of broadening back into a full readiness audit.
- Reuse fresh quality and alignment evidence when it already exists, but do not assume closeout from those audits alone.
- If sample-side evidence will be gathered via `samples/pom.xml`, refresh the library snapshot first with `mvn -pl arachne -am install -DskipTests` so the sample reactor does not read a stale local `io.arachne:arachne` snapshot.
- Check whether code, tests, docs, samples, ADRs, and instructions that should describe the finished state have actually been closed.
- Make narrow no-regret fixes when the repository state directly proves the needed update. Prefer finishing the closeout in the same turn instead of reporting obvious drift and leaving it behind.
- Force every leftover item into an explicit landing place. Never leave residual work as an implicit note.
- Produce concise re-entry instructions for the next worker.
- Assign exactly one closeout status:
  - `closed`
  - `closed with follow-ups`
  - `not closed`
  - `blocked`
- State explicitly whether the worker may now close the task boundary, commit it as finished work for that boundary, and end the session.
- Interpret the statuses this way:
  - `closed`: yes, the boundary may be treated as finished and safely closed now
  - `closed with follow-ups`: yes, the boundary may be closed now, but the follow-up items must be listed with landing places
  - `not closed`: no, more work is required before claiming the boundary is finished
  - `blocked`: no, the boundary cannot be responsibly closed because the result or evidence is still unclear
- If the area is `not closed`, state the minimum actions required to reach `closed` or `closed with follow-ups`.
- If the area is `closed with follow-ups`, list each follow-up with its landing place.

End the final report with these exact section headings in this order:

- `クローズアウト対象`
- `クローズアウト判定`
- `閉じた証拠と更新面`
- `残件の着地先`
- `次の作業者への再開手順`
- `推奨アクション`