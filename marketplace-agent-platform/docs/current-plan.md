# Marketplace Agent Platform Current Plan

This file tracks the active implementation plan for the root-level `marketplace-agent-platform/` product track.

Use [../README.md](../README.md) as the source of truth for what is implemented today.
Use this file to answer these questions:

- how far the current implementation has progressed
- what remains before the deterministic first slice can be treated as complete
- what the immediate next action should be

## Current Boundary

The product track has closed deterministic Slice 1 on the current branch.

Implemented today:

- root-level independent multi-module product track boundary
- thin `operator-console` with `Case List` and `Case Detail`
- `case-service` case creation, list, detail, follow-up message, approval submission, and SSE activity updates
- `workflow-service` start, continue, and resume orchestration over explicit HTTP service boundaries
- deterministic downstream services for escrow, shipment, risk, and notification
- PostgreSQL-backed business persistence for service-owned data in the local runtime
- Redis-backed workflow session continuity in the composed runtime
- explicit cross-replica workflow continuity evidence in module integration tests against a shared Redis session store
- deterministic `REFUND` and `CONTINUED_HOLD` recommendation paths for `ITEM_NOT_RECEIVED`
- approval-complete path for `CONTINUED_HOLD`
- approval-complete path for `REFUND`
- approval-reject path that returns the case to evidence gathering without settlement

Explicitly not implemented yet in the shipped product-track slice:

- Arachne-native named agents driving the runtime behavior
- Bedrock-backed workflow execution for this product track
- packaged skills and built-in resource-tool usage in the workflow path
- native interrupt-resume wiring beyond the current deterministic HTTP/session flow
- steering and execution-context-propagation integration as visible product behavior

## Progress Against Slice 1

Deterministic Slice 1 is now treated as complete for the current product-track boundary.

Closed gates:

- Gate 1: downstream service APIs and deterministic responses
- Gate 2: case projections and case-facing API
- Gate 3: workflow start, continue, approval interrupt, approval resume, and representative refund/hold settlement behavior
- Gate 4: replica continuity with Redis wired and proven
- Gate 5: thin frontend shows the representative deterministic flow with search, approval, activity, and outcome visibility

## Progress Against Full Requirements

The overall product is not close to full requirements completion yet.

Roughly:

- deterministic service-backed scaffold: largely in place
- deterministic representative workflow coverage: largely in place
- full Slice 1 requirement satisfaction: not complete yet
- Arachne-capability-complete sample: still largely deferred

The main gap between the current implementation and the full requirements is that `requirements.md` expects visible Arachne-native capabilities, while the current product-track README still correctly describes the implementation as deterministic-first and deferred for those features.

## Immediate Next Action

Do not expand the deterministic-first boundary further unless a concrete defect is found.

That means:

1. preserve the current deterministic workflow, approval, search, and continuity boundary
2. keep README, task tracking, and tests aligned with the closed deterministic slice
3. begin deferred Arachne-native work only through an explicit next implementation theme

## After That

When the next implementation theme starts, decide how to add the deferred Arachne-native capability layer without rewriting the current deterministic evidence.
