# Closeout And Readiness

This document explains how to use closeout and readiness in everyday local development for Arachne.

The goal is simple: do not depend on perfect end-of-task discipline, but make it cheap to recover repository health before the next meaningful change.

## The Short Version

- use `Closeout` when you have just finished a bounded task and want to finish it cleanly
- update `docs/project-status.md` inside `Closeout` when the bounded task changed the branch-level shipped snapshot
- use `Readiness Audit` when you want to check whether one area is easy to resume and what should be repaired
- use `Repository Readiness Audit` when the repository feels scattered, a new theme is about to begin, or you want a wider maintenance sweep
- use `Project Status Sync` when the main remaining drift is the branch snapshot in `docs/project-status.md`

## What Each Workflow Does

### Closeout

Use [.github/prompts/closeout.prompt.md](../.github/prompts/closeout.prompt.md) after a bounded task.

It is the end-of-task protocol.

It should:

- restate the exact finished boundary
- confirm fresh enough evidence
- close the truth surfaces that should now reflect the finished state
- force every leftover item to land somewhere explicit
- leave short re-entry instructions for the next worker
- say explicitly whether the worker may now treat that boundary as finished, commit it as complete for that boundary, and end the session

Use it when the work is still fresh and you can still fix obvious drift in the same turn.

Closeout does not require a prior readiness audit, and it does not automatically invoke one. It is the primary end-of-task decision point for whether the bounded task is actually ready to be closed.

Interpret the closeout statuses this way:

- `closed`: yes, the bounded task may be treated as finished and closed now
- `closed with follow-ups`: yes, the bounded task may be closed now, but the follow-up items must already have explicit landing places
- `not closed`: no, more work is required before the task may be treated as finished
- `blocked`: no, the boundary or evidence is too unclear to close responsibly

### Readiness Audit

Use [.github/prompts/readiness-audit.prompt.md](../.github/prompts/readiness-audit.prompt.md) for one capability area or repository surface.

It is the recovery and repair workflow.

It should:

- judge whether the next worker can restart from a small, trustworthy surface
- identify what makes the area hard to re-enter
- produce concrete fix candidates
- order those candidates by repair priority

Use it when closeout was skipped, when an area feels scattered, or before starting new work in that area.

If readiness finds that the main problem is a stale `docs/project-status.md` snapshot rather than broader area drift, use `.github/prompts/project-status-sync.prompt.md` to repair that snapshot directly.

### Project Status Sync

Use [.github/prompts/project-status-sync.prompt.md](../.github/prompts/project-status-sync.prompt.md) when the branch snapshot in `docs/project-status.md` needs to be reconciled with the current repository truth surfaces.

It is the branch-snapshot maintenance workflow.

It should:

- decide whether `docs/project-status.md` actually needs an update
- refresh only the branch-level surfaces that changed
- avoid turning the status document into a changelog or roadmap
- report missing evidence when the repository state is not strong enough to justify an edit

Use it:

- during closeout when the task changed shipped surface or constraints and the snapshot still needs to be updated
- after readiness or alignment work when the main remaining drift is in `docs/project-status.md`
- after several bounded tasks when the branch snapshot needs consolidation

### Repository Readiness Audit

Use [.github/prompts/repository-readiness-audit.prompt.md](../.github/prompts/repository-readiness-audit.prompt.md) for a broader maintenance sweep.

It partitions the repository into bounded readiness areas, then reports which areas are ready and which need repair.

Use it:

- before a new implementation theme
- before a release or wider cleanup
- when repository context feels like it is spreading across too many surfaces

## Recommended Local Workflow

For normal local development:

1. Finish a bounded task.
2. If the work is still fresh, run `Closeout` and update `docs/project-status.md` there when the branch snapshot changed.
3. If only the branch snapshot drift remains, run `Project Status Sync`.
4. If you skipped closeout or later suspect wider drift, run `Readiness Audit` for that area.
5. When the repository as a whole feels scattered, run `Repository Readiness Audit`.

This repository does not currently force these workflows with hooks or CI. They are maintainers' operating procedures.

## Sample Reactor Re-Entry Rule

When you are about to use `samples/pom.xml` for sample-side readiness, consistency, or quality evidence, refresh the library snapshot in the local Maven repository first.

Use this sequence:

```bash
mvn -pl arachne -am install -DskipTests
mvn -f samples/pom.xml test
```

The reason is mechanical: the sample reactor resolves `io.arachne:arachne` from the local Maven repository, not from the sibling `arachne/` module's in-memory reactor outputs. Without the install step, sample checks can accidentally evaluate an older local snapshot and produce false drift.

## Bedrock-Specific Re-Entry Rule

When a bounded task changes Bedrock-specific runtime behavior, Bedrock-facing sample wiring, or Bedrock-only documentation claims, treat live Bedrock smoke verification as part of closeout evidence.

Rerun these opt-in checks when credentials and model access are available:

```bash
mvn -Dtest=BedrockModelIntegrationTest -Darachne.integration.bedrock=true test
mvn -f samples/pom.xml -pl domain-separation -Dtest=DomainSeparationBedrockIntegrationTest -Darachne.integration.bedrock=true test
```

If you do not rerun them, leave that gap as explicit residual work instead of implying that live provider evidence is fresh.

## What Counts As Good Enough

An area is in good working shape when all of these are true:

- the next worker can identify the right starting docs, code, and tests quickly
- the shipped boundary and current constraints are easy to find
- code, tests, docs, samples, ADRs, and instructions do not contradict each other in meaningful ways
- leftover work is explicitly classified instead of implied
- readiness can name the highest-value repairs without broad rediscovery

`docs/project-status.md` is in good shape when it stays a short branch snapshot: accurate enough to orient the next worker, narrow enough to avoid becoming a second user guide, and updated only when branch-level facts changed.

## If You Only Do One Thing

If you do not want to run everything, prefer this rule:

- run `Closeout` when finishing an important bounded task
- run `Readiness Audit` before starting work in an area that feels fuzzy or scattered