# Closeout Checklist

Use this checklist for both `/phase-closeout` and `/phase-audit`.

## 1. Reconfirm the phase target

- Read the target phase section in ROADMAP.md.
- Capture the stated goal and every task row for that phase.
- Treat unchecked roadmap rows as incomplete until code, docs, and tests prove otherwise.

## 2. Confirm implementation coverage

- Find the code paths that implement the target phase.
- Check whether the implementation still matches the active phase instructions.
- Confirm that the phase goal is satisfied from the public API and runtime behavior, not only from internal scaffolding.

## 3. Confirm test coverage and evidence

- Identify the tests that cover the phase.
- Check whether changed behavior has matching test additions or updates.
- Use the repo default verification command `mvn test` when code or behavior changed.
- If only an audit was requested and no edits were made, state what verification would be required for closeout.

## 4. Review documentation surfaces

Always check whether each of these needs an update:

- README.md
- docs/user-guide.md
- ROADMAP.md
- `.github/instructions/*.instructions.md`
- docs/adr/README.md and relevant ADR files
- sample READMEs under samples/

Typical triggers:

- README.md: branch-wide current status, feature list, quick start, verification instructions, contributor workflow entry points
- docs/user-guide.md: user-facing API, configuration, lifecycle, sample references, limitations, usage notes
- ROADMAP.md: checkbox completion, wording that has become stale, newly split or deferred work
- instructions: current phase focus, obsolete constraints, next-phase entry criteria, test emphasis
- ADRs: decisions that affect public API, Spring wiring, lifecycle, session persistence, tool binding and validation, execution backend, hooks/plugins/interrupts, or other cross-phase boundaries
- sample READMEs: runnable examples, new standard idioms, changed configuration, changed operator steps

## 5. Review architecture and regression risk

At minimum, check these repo-specific invariants:

- core flow remains readable as Agent -> EventLoop -> Model / Tool
- AgentFactory remains the standard Spring integration entry point
- stateful Agent runtimes are not pushed back toward shared singleton-bean usage
- provider-independent logic stays out of Bedrock-specific areas unless the task is explicitly Bedrock-specific
- prior phases keep their published behavior unless the roadmap explicitly changed it

## 6. ADR decision gate

Ask explicitly whether the phase finished with a design decision that should be recorded.

Create or update an ADR when the work:

- changes a public API or the standard usage pattern
- changes Spring wiring or bean lifecycle assumptions
- fixes a boundary that later phases will rely on
- closes a tradeoff that would otherwise be rediscovered later
- leaves behind a deliberate deferral that needs to stay visible

If no ADR change is needed, say why.

## 7. Next-phase readiness gate

If the team is about to start the next roadmap phase:

- review the next phase implementation instruction file
- review the next phase test-strategy instruction file
- remove stale constraints from the old phase
- align both files with the roadmap goal, completion conditions, and test emphasis of the next phase

If next-phase work is not starting yet, state that no instruction switch was made.

## 8. Residual work classification

Any leftover work must be classified instead of left implicit:

- required before phase completion
- intentionally deferred to a named later phase
- needs ADR follow-up
- needs sample or docs follow-up
- needs test follow-up

## 9. Report format

Use a compact report with these fields:

- Phase: target phase and goal
- Status: complete, incomplete, or blocked
- Evidence checked: code, tests, docs, samples, ADRs, instructions
- Updates made: exact files changed and why
- Remaining work: only if anything is still open
- Recommendation: mark complete, continue implementation, run closeout later, or prepare next phase
