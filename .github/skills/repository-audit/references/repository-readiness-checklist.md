# Repository Readiness Checklist

Use this checklist when the question is not only whether the repository is correct now, but whether it is ready for the next bounded piece of work.

Readiness in this repository means the next worker can identify the right starting surfaces quickly, can trust the current implementation and documentation boundary, and does not need to rediscover unresolved decisions from scattered context.

## 1. Reconfirm the closeout target

- Name the capability area, repository surface, or cross-cutting change that just finished.
- State the intended closeout boundary explicitly: what is complete now, what was intentionally not part of this work, and which published scope documents define that boundary.
- If the closeout target cannot be named precisely, report readiness as blocked instead of guessing.

## 2. Require fresh evidence, not memory

- Identify the exact code, tests, docs, samples, ADRs, and instructions that now represent the target area.
- Confirm whether the latest quality evidence is fresh enough for the closeout claim. If not, either regenerate it or say that readiness is limited by stale evidence.
- Confirm whether implementation-test-doc alignment was checked for the target area. If not, report that readiness is incomplete even if the code looks plausible.

## 3. Check entry-point boundedness

- Identify the smallest canonical set of starting points for the next worker: usually one or two docs, the main implementation entry point, and the main test or sample.
- If the target area requires reading a broad or ambiguous set of files before work can begin, treat that as a readiness defect and explain what boundary or map is missing.
- Prefer canonical references over duplicated explanations. If the same concept is explained in multiple places, confirm that one surface is the source of truth and the others point to it cleanly.

## 4. Check boundary and ownership health

- Confirm that the finished work did not blur responsibility boundaries between core runtime, Spring wiring, provider-specific code, samples, or docs.
- Check that temporary implementation-theme instructions are still accurate, narrowly scoped, or removed if the theme ended.
- Check that public scope documents do not overstate what is shipped and do not hide important constraints or deferrals.

## 5. Classify residual work explicitly

- Every leftover item must be classified rather than left implicit.
- Use these classes when relevant:
  - required before closeout is actually complete
  - intentionally deferred to a named later phase
  - needs ADR follow-up
  - needs docs or sample follow-up
  - needs test or verification follow-up
- If a residual item has no landing place, readiness is not complete.

## 6. Check re-entry clarity

- State what the next worker should read first.
- State what should be treated as reference only versus source of truth.
- State which command or verification step should be rerun first if work resumes in this area.
- If these re-entry instructions cannot be given concisely, the area is not yet ready.

## 7. Report status

Use one of these states:

- ready: the next task can start from a small, explicit surface with fresh evidence and no implicit leftovers
- ready with follow-ups: the next task can start cleanly, but explicitly recorded follow-up items remain
- not ready: important drift, ambiguous entry points, stale instructions, or unclassified leftovers remain
- blocked: the target area or required evidence could not be identified confidently

## 8. Report format

Use a concise Japanese report by default with these fields unless the invoking prompt specifies exact headings:

- Scope
- Readiness status
- Fresh evidence checked
- Entry points for the next worker
- Fix candidates
- Recommended repair order
- Residual work classification
- Recommendation