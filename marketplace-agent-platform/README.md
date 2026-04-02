# Marketplace Agent Platform

This directory is the implementation home for the marketplace agent platform product track.

The active direction is a marketplace platform in which Spring-based microservices each own a service-local agent and collaborate through explicit capability boundaries, with a thin operator-facing frontend for visibility and approval interaction.

At this stage, this directory contains a runnable end-to-end product-track slice for the representative marketplace workflow, with later phases still open in the roadmap.

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

This product track currently ships a working Phase 3 slice for the representative `ITEM_NOT_RECEIVED` marketplace workflow.

That baseline includes the full composed service shape, a thin operator console, PostgreSQL-backed business persistence, Redis-backed workflow continuity, representative `REFUND` plus `CONTINUED_HOLD` outcomes, named agents, packaged skills, built-in resource tools, native finance-control pause and resume on the opt-in Arachne path, operator-visible streaming progress from packaged guidance lookup, and narrow tool-boundary steering that redirects an unsafe automatic settlement shortcut while Spring services keep deterministic state-transition ownership.

The detailed implemented boundary, remaining gaps, and capability-complete target now live only in `docs/roadmap.md`.

## Design Docs

Use these documents by responsibility, not interchangeably.

Treat this README as the public product-track overview.
Treat `docs/roadmap.md` as the single source of truth for what is implemented today, implementation progress, remaining tasks, and next-phase sequencing.
Treat the other `docs/*.md` files as concept, requirements, architecture, API, contract, and skill-boundary references.

Use these working files to track in-flight execution status for this product track:

- `docs/roadmap.md`: current baseline, capability-complete target, ordered roadmap, and remaining tasks
- `docs/concept.md`: what the sample is meant to show, why this domain fits, and the representative explanation scenario
- `docs/requirements.md`: what the first slice must support and what boundaries are treated as requirements
- `docs/architecture.md`: execution architecture, development architecture, local runtime story, and deferred architecture choices
- `docs/apis.md`: minimal frontend/case-service, case-service/workflow-service, and workflow/downstream API boundaries
- `docs/contracts.md`: minimal case-facing projections and approval-facing contract shapes
- `docs/skills.md`: service-local skill boundaries, knowledge sources, and deterministic logic boundaries

Recommended reading order:

1. `docs/concept.md`
2. `docs/requirements.md`
3. `docs/architecture.md`
4. `docs/apis.md`
5. `docs/contracts.md`
6. `docs/skills.md`
7. `docs/roadmap.md`

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
Agent skill boundary follow-up lives in `docs/skills.md`.
Implementation progress and remaining work live in `docs/roadmap.md`.

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

## Operator Console

The thin frontend now lives under `operator-console/` and is intentionally limited to the current case-service API surface.

Common local operations for this product track are exposed through `make` from `marketplace-agent-platform/`.
Run `make help` there to see the current shortcuts.

Run it with:

```bash
cd operator-console
npm ci
npm run dev
```

The Vite dev server listens on `http://localhost:3000` and proxies `/api` to `http://localhost:8080` by default.

The recommended browser-facing shape is same-origin through the console at `http://localhost:3000`.
That means the browser should talk to the console origin only, while the dev proxy or composed Nginx forwards `/api` to `case-service`.

If you need to bypass the proxy for a one-off setup, set `VITE_CASE_SERVICE_BASE_URL` before starting the Vite server.

The local composed runtime also serves the built frontend at `http://localhost:3000`.

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

Or from `marketplace-agent-platform/` use:

```bash
make up
```

Java service image builds share a Docker BuildKit Maven cache instead of bind-mounting the host local repository.
That keeps repeated dependency downloads down across service builds without leaving root-owned artifacts under your host user home.
If Docker BuildKit is disabled in your shell, enable it for this command with `DOCKER_BUILDKIT=1`.

Compose startup is health-gated rather than start-order-only:

- `postgres` and `redis` must become healthy before dependent backend services start
- Spring backend services expose `/actuator/health` for local container health checks
- `case-service`, the workflow load balancer, and `operator-console` wait on healthy upstream services rather than only waiting for container process start

The main local endpoints are:

- `operator-console`: `http://localhost:3000`
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

To remove persisted PostgreSQL state as well, use:

```bash
make reset
```

Current runtime limits:

- business persistence is still incomplete at the full architecture level: the sample now runs on PostgreSQL with service-local logical databases, but it is still a demo-oriented local topology rather than the fuller production database, migration, and operations shape described in the design docs