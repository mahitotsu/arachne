# 0012. Post-MVP Product Boundary

## Status

Accepted

## Context

The planned MVP scope is complete on the current main branch. The repository no longer needs a phase-by-phase roadmap as the primary source of truth for what Arachne ships.

At this point, two risks become more important than additional planning detail:

- user-facing documentation can drift if implementation status and deferred work remain implicit
- future feature ideas can blur the shipped contract unless the current product boundary is stated explicitly

The project therefore needs a stable way to describe three things without relying on a phase checklist:

- what is part of the shipped contract today
- what remains intentionally deferred
- how new post-MVP feature areas should be introduced

## Decision

Arachne adopts the following post-MVP documentation and planning boundary.

- The shipped contract is documented through `README.md`, `docs/user-guide.md`, and `docs/project-status.md` rather than a repository roadmap file.
- `docs/project-status.md` is the canonical snapshot for supported scope, current constraints, and deliberately deferred features.
- ADRs remain the canonical place for design decisions that affect public API, Spring integration, lifecycle, provider boundaries, extension boundaries, or other cross-cutting contracts.
- The current shipped scope remains the Bedrock-backed runtime with annotation-driven tools, structured output, named-agent defaults, retry, conversation/session management, hooks/plugins, interrupts, skills, streaming, and steering.
- The following areas remain deliberately deferred until separately proposed and accepted: provider expansion beyond Bedrock, bidirectional realtime/audio streaming, MCP, multi-agent orchestration, A2A, Guardrails, Agent Config, Evals SDK, and remote skill distribution.
- New work in those deferred areas must begin with an ADR or ADR update before implementation starts.

## Consequences

- Removing `ROADMAP.md` no longer removes the public record of shipped features or missing capabilities.
- User-facing documents must classify unimplemented items explicitly instead of implying that they are simply undocumented.
- Contributor workflows that previously depended on roadmap phases must use the current status document, user guide, ADRs, and capability-specific evidence instead.
- Future work remains possible, but it is not implicitly promised by historical planning structure.

## Alternatives Considered

### 1. Keep the roadmap indefinitely after MVP completion

Rejected. Once all planned rows are complete, the roadmap becomes a stale duplicate of the shipped contract and a poor home for future design questions.

### 2. Put deferred work only in README or user guide prose

Rejected. That would mix current product behavior and future-looking design concerns into the same surface and make architectural deferrals harder to track.

### 3. Track future work only in issues without an ADR boundary

Rejected. Several deferred areas change public API or core boundaries, so they need explicit design records rather than issue-only intent.