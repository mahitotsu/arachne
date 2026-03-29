# Repository Closeout Checklist

Use this checklist at the end of a bounded task, implementation slice, or audit-driven repair.

The purpose of closeout is to leave behind a repository state that the next worker can trust and re-enter without broad rediscovery.

## 1. Name the finished boundary

- State the exact task, capability area, or repository surface being closed.
- State what is complete now.
- State what was explicitly out of scope for this task.
- Point to the scope-defining documents that now describe the finished state.

If this boundary cannot be stated precisely, do not claim closeout success.

## 2. Gather fresh evidence

- Confirm the implementation surface that changed.
- Confirm the tests that cover the change.
- Confirm whether quality evidence is fresh enough for the closeout claim.
- Confirm whether implementation, tests, docs, samples, ADRs, and instructions were checked for alignment.

If evidence is stale, partial, or missing, say so explicitly and lower the closeout status.

## 3. Close the truth surfaces

- Update the public documents that should now describe the change.
- Update or confirm samples when user-facing wiring or operator steps changed.
- Update or confirm ADRs when a cross-cutting design decision was made or deferred.
- Update or remove temporary implementation-theme instructions when they no longer match the current state.

The goal is not to touch every surface. The goal is to ensure that every surface which should describe the finished state actually does.

## 4. Force residual work to land somewhere explicit

- Do not leave vague notes such as later, maybe, or follow up.
- Classify each leftover item as one of:
  - required before this task is truly closed
  - intentionally deferred to a named later phase
  - needs ADR follow-up
  - needs docs or sample follow-up
  - needs test or verification follow-up
- Name the landing place for each leftover item.

If any leftover item has no landing place, closeout is incomplete.

## 5. Produce re-entry instructions

State these three things explicitly:

- the smallest canonical docs or files the next worker should read first
- the source of truth versus reference-only materials
- the first verification command to rerun when work resumes

If this cannot be said concisely, the area is not ready for closeout.

## 6. Assign closeout status

Use one of these states:

- closed: the task boundary is explicit, evidence is fresh enough, truth surfaces are closed, and no implicit leftovers remain
- closed with follow-ups: the task can close, but explicit follow-up items remain and each has a landing place
- not closed: important drift, stale evidence, or unlanded residual work remains
- blocked: the target boundary or evidence could not be identified confidently

## 7. Report format

Use a concise Japanese report by default with these fields unless the invoking prompt specifies exact headings:

- Closed scope
- Closeout status
- Evidence checked
- Surfaces updated or confirmed
- Residual work and landing places
- Re-entry instructions
- Recommendation