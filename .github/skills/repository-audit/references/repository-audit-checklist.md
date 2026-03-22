# Repository Audit Checklist

Use this checklist as the shared repository audit procedure behind `/alignment-audit` and, when useful for repository-side evidence gathering, `/compat-audit`.

## 1. Reconfirm the target area

- Read `docs/project-status.md`, `docs/user-guide.md`, and relevant ADRs for the target area.
- Capture the shipped behavior, current constraints, and any explicitly deferred items that border the target area.
- Treat undocumented or unverified behavior as incomplete until code, docs, and tests prove otherwise.

## 2. Confirm implementation coverage

- Find the code paths that implement the target area.
- Check whether the implementation still matches the active instructions for that area.
- Confirm that the target goal is satisfied from the public API and runtime behavior, not only from internal scaffolding.

## 3. Confirm test coverage and evidence

- Identify the tests that cover the target area.
- Check whether changed behavior has matching test additions or updates.
- Use the repo default verification command `mvn test` when code or behavior changed.
- If only an audit was requested and no edits were made, state what verification would be required for a full closeout.

## 4. Review documentation surfaces

Always check whether each of these needs an update:

- README.md
- docs/user-guide.md
- docs/project-status.md
- `.github/instructions/*.instructions.md`
- docs/adr/README.md and relevant ADR files
- sample READMEs under samples/

Typical triggers:

- README.md: branch-wide current status, feature list, quick start, verification instructions, contributor workflow entry points
- docs/user-guide.md: user-facing API, configuration, lifecycle, sample references, limitations, usage notes
- docs/project-status.md: shipped scope, wording that has become stale, newly deferred work, current constraints
- instructions: current phase focus, obsolete constraints, next-phase entry criteria, test emphasis
- ADRs: decisions that affect public API, Spring wiring, lifecycle, session persistence, tool binding and validation, execution backend, hooks/plugins/interrupts, or other cross-phase boundaries
- sample READMEs: runnable examples, new standard idioms, changed configuration, changed operator steps

## 5. Review architecture and regression risk

At minimum, check these repo-specific invariants:

- core flow remains readable as Agent -> EventLoop -> Model / Tool
- AgentFactory remains the standard Spring integration entry point
- stateful Agent runtimes are not pushed back toward shared singleton-bean usage
- provider-independent logic stays out of Bedrock-specific areas unless the task is explicitly Bedrock-specific
- previously shipped behavior keeps working unless there is an explicit contract change documented in the status docs and ADRs

## 6. ADR decision gate

Ask explicitly whether the target area finished with a design decision that should be recorded.

Create or update an ADR when the work:

- changes a public API or the standard usage pattern
- changes Spring wiring or bean lifecycle assumptions
- fixes a boundary that later phases will rely on
- closes a tradeoff that would otherwise be rediscovered later
- leaves behind a deliberate deferral that needs to stay visible

If no ADR change is needed, say why.

## 7. Next-theme readiness gate

If the team is about to start the next implementation theme:

- review the next implementation instruction file
- review the next test-strategy instruction file
- remove stale constraints from the old phase
- align both files with `docs/project-status.md`, relevant ADRs, completion conditions, and the test emphasis of the next theme

If no new implementation theme is starting yet, state that no instruction switch was made.

## 8. Residual work classification

Any leftover work must be classified instead of left implicit:

- required before phase completion
- intentionally deferred to a named later phase
- needs ADR follow-up
- needs sample or docs follow-up
- needs test follow-up

## 9. Report format

Use a compact report in Japanese by default with these fields:

- Area: target area and goal
- Status: aligned, misaligned, incomplete, or blocked
- Evidence checked: code, tests, docs, samples, ADRs, instructions
- Updates made: exact files changed and why
- Remaining work: only if anything is still open
- Recommendation: keep aligned, continue implementation, run closeout later, or prepare the next implementation theme

If the invoking prompt requires a different final section set or heading wording, follow the prompt while keeping the prose in Japanese.
