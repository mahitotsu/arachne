---
name: "close-action"
description: "Decide whether the current work is ready to commit or the session is ready to close without leaving repository drift behind."
argument-hint: "Optional scope. Example: current task only / include docs sync"
agent: "agent"
---

Judge whether the current workspace state is ready to commit or ready to close.

Follow this procedure strictly.

1. Read [workspace instructions](../copilot-instructions.md), [repository snapshot](../repository-ops/repository-snapshot.md), and `/memories/repo/status.md`.
2. Read [repository reading guide](../repository-ops/repository-reading-guide.md), [arachne/docs/project-status.md](../../arachne/docs/project-status.md), and [arachne/docs/repository-facts.md](../../arachne/docs/repository-facts.md) only as needed for the changed scope.
3. Check the real git state with `git status --short` and `git log -1 --oneline`.
4. Infer the intended scope from the current conversation and actual diff.
5. If the change touches shipped surface, sample entry points, verification guidance, or repository workflow, confirm the relevant truth surfaces are synchronized. At minimum consider `arachne/docs/project-status.md`, `arachne/docs/repository-facts.md`, affected READMEs, `.github/repository-ops/`, and `/memories/repo/status.md`.
6. Confirm the work still fits one coherent commit boundary and one primary repository area. If the diff has spread beyond the bounded read set implied by `repository-reading-guide.md`, do not declare readiness.
7. Confirm verification is fresh enough for the touched scope. Prefer running cheap checks instead of guessing.
   - library behavior or public docs tied to shipped behavior: `mvn test`
   - sample-side evidence: `mvn -pl arachne -am install -DskipTests` then `mvn -f samples/pom.xml test`
   - marketplace Java services: `mvn -f marketplace-agent-platform/pom.xml test`
   - marketplace operator console: run `npm ci` and `npm run build` in `marketplace-agent-platform/operator-console`
   - workflow/docs only: perform a lightweight path, link, and sync review; if repo-ops changed, confirm snapshot and repo status sync explicitly
8. Classify the result as exactly one of:
   - `READY_TO_COMMIT`: the diff is one coherent commit unit, but the working tree is not yet clean
   - `READY_TO_CLOSE`: the work is already committed or the working tree is clean and no in-scope follow-up remains
   - `NOT_READY`: blockers, stale verification, mixed scope, unsynced docs, or unresolved follow-up remain
9. Do not declare readiness when any of these are still true:
   - unrelated or mixed-purpose changes are bundled together
   - workflow or repository-operation changes are not synchronized across prompts, skills, snapshot, or repo status
   - shipped-surface changes are not reflected in the docs that describe that surface
   - the primary area is unclear or the work is no longer localized
   - required verification is missing
   - follow-up that belongs in the same slice is still implicit
10. If the result is `READY_TO_COMMIT`, propose a commit message but do not commit unless the user explicitly asked for it.
11. If the result is `READY_TO_CLOSE`, say explicitly whether the worker may end the session now.

Answer in this format:

```text
State: READY_TO_COMMIT | READY_TO_CLOSE | NOT_READY
Decision: <one sentence>
Evidence:
- <fact 1>
- <fact 2>
- <fact 3>
Blockers or Risks:
- <item or `none`>
Recommended Next Action:
- <single next action>
Suggested Commit Message:
- <message or `none`>
```