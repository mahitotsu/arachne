# Marketplace Agent Platform Sample

This directory is the concept home for the next high-density Arachne sample.

The active direction is a marketplace platform in which Spring-based microservices each own a service-local agent and collaborate through explicit capability boundaries, with a thin operator-facing frontend for visibility and approval interaction.

At this stage, this directory is intentionally concept-only. It does not yet contain runnable implementation code.

## Current State

This sample is still in design only.

The current first-slice direction is:

- marketplace platform with escrow-mediated settlement and exception handling
- representative scenario: `ITEM_NOT_RECEIVED`
- thin frontend with exactly two screens: `Case List` and `Case Detail`
- `case-service` owns case creation, list, detail, and search
- `workflow-service` owns long-running workflow handling and case progression
- `React + TypeScript + Vite` for the thin `operator-console`
- workflow-service replicas behind an internal load balancer with shared Redis-backed session continuity
- relational database storage for business data
- `finance control` as the approval actor
- `HTTP` for case-facing commands and `SSE` for case activity updates

## Design Docs

Use these documents by responsibility, not interchangeably.

- `docs/concept.md`: what the sample is meant to show, why this domain fits, and the representative explanation scenario
- `docs/requirements.md`: what the first slice must support and what boundaries are treated as requirements
- `docs/architecture.md`: execution architecture, development architecture, local runtime story, and deferred architecture choices
- `docs/apis.md`: minimal frontend/case-service, case-service/workflow-service, and workflow/downstream API boundaries
- `docs/contracts.md`: minimal case-facing projections and approval-facing contract shapes
- `docs/slices.md`: recommended implementation slice order and validation checkpoints
- `docs/skills.md`: service-local skill boundaries, knowledge sources, and deterministic logic boundaries

Recommended reading order:

1. `docs/concept.md`
2. `docs/requirements.md`
3. `docs/architecture.md`
4. `docs/apis.md`
5. `docs/contracts.md`
6. `docs/slices.md`
7. `docs/skills.md`

## Active Concept

The sample is intended to model a backend that can support multiple marketplace workflows such as:

- dispute handling under escrow
- settlement and refund decisions
- shipment exception handling
- risk review and manual approval
- post-decision notification and follow-up

The representative explanation scenario is still an `ITEM_NOT_RECEIVED` dispute under escrow:

- a buyer reports that an item did not arrive
- the seller claims it was shipped
- funds are currently held in escrow
- the platform must gather shipment evidence, settlement state, and risk signals before deciding whether to refund, keep the hold, or release funds

The concept details live in `docs/concept.md`.
Requirements live in `docs/requirements.md`.
Architecture follow-up lives in `docs/architecture.md`.
API boundary follow-up lives in `docs/apis.md`.
Case and approval contract follow-up lives in `docs/contracts.md`.
Implementation slice planning lives in `docs/slices.md`.
Agent skill boundary follow-up lives in `docs/skills.md`.

## Planned UX Shape

The current concept uses two screens only.

1. `Case List`
	Agentic search, new case creation, and case selection.
2. `Case Detail`
	Chat-driven case handling together with structured case state, evidence, activity history, approval state, and final outcome.

This keeps the UI intentionally thin while still making streaming and human approval understandable.

## Why This Is The Active Direction

This direction preserves the same structural value as trade-finance style settlement mediation while remaining easier to understand as a sample.

It naturally justifies:

- multiple service-local agents
- explicit cross-service delegation
- policy and runbook lookup
- structured evidence summaries
- approval pause and resume
- long-running session restore
- operator-visible streaming
- steering that blocks unsafe settlement shortcuts

It also benefits from a thin frontend because streaming progress and human approval are easier to understand when an operator can see the case timeline and respond directly.

## Status

Concept only.

The current design work has already fixed the main first-slice direction and reduced the remaining work to:

- implementation and scaffold work, if the sample moves beyond concept docs

An earlier provisional draft was discarded because it did not reflect the marketplace-centered sample direction strongly enough.