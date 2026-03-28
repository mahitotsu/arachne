# Marketplace Agent Platform Sample

This directory is the concept home for the next high-density Arachne sample.

The active direction is a marketplace platform in which Spring-based microservices each own a service-local agent and collaborate through explicit capability boundaries, with a thin operator-facing frontend for visibility and approval interaction.

At this stage, this directory is intentionally concept-only. It does not yet contain runnable implementation code.

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

An earlier provisional draft was discarded because it did not reflect the marketplace-centered sample direction strongly enough.