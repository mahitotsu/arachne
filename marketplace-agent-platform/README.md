# Marketplace Agent Platform

This directory is the implementation home for the marketplace agent platform product track.

The active direction is a marketplace platform in which Spring-based microservices each own a service-local agent and collaborate through explicit capability boundaries, with a thin operator-facing frontend for visibility and approval interaction.

At this stage, this directory contains the first backend implementation slice for the product. It still does not contain the runnable end-to-end system described in the design documents.

It is intentionally not part of the runnable `samples/` catalog.

It now lives at the repository root as its own multi-module aggregator.

The current implementation modules are:

- `case-service`
- `workflow-service`
- `escrow-service`
- `shipment-service`
- `risk-service`
- `notification-service`
- `operator-console`

## Current State

This product has moved beyond design-only status and now includes a working first backend slice.

The current first-slice direction is:

- marketplace platform with escrow-mediated settlement and exception handling
- representative scenario: `ITEM_NOT_RECEIVED`
- thin frontend with exactly two screens: `Case List` and `Case Detail`
- `case-service` owns case creation, list, detail, activity history, approval submission, and SSE activity updates
- `workflow-service` owns internal workflow start and resume handling
- `workflow-service` collects downstream evidence from shipment, escrow, and risk services over HTTP
- approval completion triggers escrow settlement handling and notification dispatch over HTTP
- downstream services are separated into `shipment-service`, `escrow-service`, `risk-service`, and `notification-service`
- `React + TypeScript + Vite` for the thin `operator-console`
- workflow-service replicas behind an internal load balancer with shared Redis-backed session continuity in the composed runtime
- relational database storage remains the target system of record for business data, and the local runtime now uses PostgreSQL rather than H2 for service-owned persistence
- `finance control` as the approval actor
- `HTTP` for case-facing commands and `SSE` for case activity updates

## Design Docs

Use these documents by responsibility, not interchangeably.

Treat this README as the source of truth for what is implemented today.
Treat `docs/*.md` in this directory as design and next-slice reference unless this README explicitly says the implementation now matches them.

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

Backend first slice implemented and verified by module tests. A local composed runtime is now available.

The current repository state now includes:

- parent aggregator wiring at the repository root for `marketplace-agent-platform`
- thin `operator-console` for the case-service API surface
- `case-service` case-facing API and PostgreSQL-backed projection storage
- `escrow-service` business truth and settlement audit persisted in PostgreSQL-backed JDBC storage
- `shipment-service` shipment evidence facts persisted in PostgreSQL-backed JDBC storage
- `risk-service` risk review facts persisted in PostgreSQL-backed JDBC storage
- `notification-service` dispatch audit persisted in PostgreSQL-backed JDBC storage
- `case-service -> workflow-service` HTTP integration
- `workflow-service` internal workflow API and downstream orchestration
- workflow-service session repository with memory default and Redis-backed continuity in the composed runtime
- deterministic downstream service modules for escrow, shipment, risk, and notification with service-owned persistence
- module test coverage for all six backend services
- Docker Compose runtime for the six backend services plus an internal workflow load balancer, Redis, and PostgreSQL
- Vite-based `operator-console` that consumes `case-service` only

Agent-driven runtime behavior still needs implementation.

That means the current slice is a deterministic service-backed scaffold with a thin frontend, not yet the full Arachne-capability-complete sample described across the design documents.

## Re-entry Boundary

If work resumes tomorrow, start from these surfaces in this order:

1. this README for the implemented boundary and current limits
2. `workflow-service/src/main/java/.../WorkflowApplicationService.java` for the current orchestration shape
3. `case-service/src/main/java/.../CaseApplicationService.java` for the case-facing boundary
4. `workflow-service/src/test/java/.../WorkflowServiceApiTest.java` and `case-service/src/test/java/.../CaseServiceApiTest.java` for the main behavior evidence

Treat the following as explicitly deferred from the implemented slice:

- named Arachne agents and Bedrock-backed runtime behavior
- packaged skills and built-in resource-tool usage
- Arachne-native interrupt/resume wiring rather than HTTP/session simulation
- steering and execution-context-propagation integration beyond the current deterministic service boundaries

Re-run these commands first when resuming work:

```bash
cd /home/akring/arachne/marketplace-agent-platform && mvn test
cd /home/akring/arachne/marketplace-agent-platform/operator-console && npm run build
```

## Operator Console

The thin frontend now lives under `operator-console/` and is intentionally limited to the current case-service API surface.

Run it with:

```bash
cd operator-console
npm ci
npm run dev
```

The default frontend endpoint configuration targets `http://localhost:8080` for `case-service`.

If you are using a different case-service address, set `VITE_CASE_SERVICE_BASE_URL` before starting the Vite server.

The local composed runtime also serves the built frontend at `http://localhost:8086`.

## Local Composed Runtime

The repository now includes a local composed runtime for verification of the current slice.

It starts:

- `operator-console`
- `case-service`
- `workflow-load-balancer`
- two `workflow-service` replicas
- `redis`
- `postgres`
- `escrow-service`
- `shipment-service`
- `risk-service`
- `notification-service`

Start it with:

```bash
docker compose -f marketplace-agent-platform/compose.yml up --build
```

Compose startup is health-gated rather than start-order-only:

- `postgres` and `redis` must become healthy before dependent backend services start
- Spring backend services expose `/actuator/health` for local container health checks
- `case-service`, the workflow load balancer, and `operator-console` wait on healthy upstream services rather than only waiting for container process start

The main local endpoints are:

- `operator-console`: `http://localhost:8086`
- `case-service`: `http://localhost:8080`
- `workflow-load-balancer`: `http://localhost:8081`
- `redis`: `localhost:6379`
- `postgres`: `localhost:5432`
- `escrow-service`: `http://localhost:8082`
- `shipment-service`: `http://localhost:8083`
- `risk-service`: `http://localhost:8084`
- `notification-service`: `http://localhost:8085`

Stop it with:

```bash
docker compose -f marketplace-agent-platform/compose.yml down
```

Current runtime limits:

- business persistence is still incomplete at the full architecture level: the sample now runs on PostgreSQL with service-local logical databases, but it is still a demo-oriented local topology rather than the fuller production database, migration, and operations shape described in the design docs