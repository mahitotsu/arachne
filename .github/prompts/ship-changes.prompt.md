---
name: "ship-changes"
description: "Execute the current change through verification and git commit or push, repairing local blockers when they are in scope."
argument-hint: "Optional scope. Example: current task only / use conventional commits / skip push"
agent: "agent"
---

Use the current conversation, workspace state, git state, and available hook output to carry the current change through verification and commit or push.

This prompt is suitable for Arachne library work, samples, food-delivery services, customer-ui, docs, and workflow changes.

Follow this procedure strictly.

1. Read [workspace instructions](../copilot-instructions.md), [repository snapshot](../repository-ops/repository-snapshot.md), and `/memories/repo/status.md`. Read [repository reading guide](../repository-ops/repository-reading-guide.md), [arachne/docs/project-status.md](../../arachne/docs/project-status.md), and [arachne/docs/repository-facts.md](../../arachne/docs/repository-facts.md) as needed for the changed area.
2. Check git state with `git status --short`, `git log -1 --oneline`, and changed files.
3. Infer the primary area and read the nearest README or instruction file for that area.
4. Before committing, apply the same readiness standard as [close-action.prompt.md](./close-action.prompt.md). Do not commit mixed-purpose work, unsynchronized workflow changes, or stale verification.
5. Run the smallest fresh verification that matches the changed area.
   - Arachne library or docs tied to shipped behavior: `mvn test`
   - sample wiring or sample behavior: `mvn -pl arachne -am install -DskipTests` then `mvn -f samples/pom.xml test`
   - food-delivery Java services: `mvn -f food-delivery-demo/pom.xml test`
   - food-delivery customer UI: in `food-delivery-demo/customer-ui`, run `npm ci` then `npm run build`
   - docs or workflow only: perform a lightweight sync review; if `.github/repository-ops/` changed, confirm snapshot and repo status synchronization explicitly
6. If local hooks or verification fail, identify the failing surface and apply only the minimum in-scope fix. Do not widen into new product work.
7. Choose a concise commit message based on the actual diff unless the user gave one.
8. Run `git add` and `git commit`. Unless the user asked to skip push, continue to `git push` after commit success.
9. If push fails because of permissions, protected branches, auth, non-fast-forward, or another user decision, stop and report facts without working around them.
10. If a fix after failed commit or push creates new changes, do not amend unless the user explicitly requested it. Use a follow-up commit if needed.
11. If remediation changes workflow or repository-operation files, sync the affected prompts, skills, snapshot, and repo status in the same work unit.
12. End with a concise state summary that does not conflict with the readiness standard from `close-action`.

Answer in this format:

```text
Scope:
- Primary area: <core-runtime | tools | sessions | extensions | bedrock | samples | food-delivery-java | customer-ui | docs | workflow | mixed | unconfirmed>
- Changed files summary: <one sentence>
Verification:
- <command/result 1>
- <command/result 2>
Commit:
- Status: <completed | blocked | skipped>
- Message: <commit message or `none`>
Push:
- Status: <completed | blocked | skipped>
- Target: <remote/branch or `unconfirmed`>
Fixes Applied:
- <change 1 or `none`>
Docs Sync:
- <synced files or `none`>
Blockers or Risks:
- <item or `none`>
Next Action:
- <single next action or `none`>
```