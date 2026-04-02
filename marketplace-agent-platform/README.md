# Marketplace Agent Platform

This directory is the implementation home for the marketplace agent platform product track.

The active direction is a marketplace platform in which Spring-based microservices each own a service-local agent and collaborate through explicit capability boundaries, with a thin operator-facing frontend for visibility and approval interaction.

This directory contains the capability-complete marketplace sample for the representative marketplace workflow on the current branch.

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

This product track ships a capability-complete marketplace workflow sample for the representative `ITEM_NOT_RECEIVED` path.

The current sample includes the full composed service shape, a thin operator console, PostgreSQL-backed business persistence, Redis-backed workflow continuity across workflow-service replicas, representative `REFUND` plus `CONTINUED_HOLD` outcomes, named agents, service-local delegation, structured output, packaged skills, built-in resource tools, native finance-control pause and resume, operator-visible streaming progress, steering that redirects the unsafe settlement shortcut, and execution-context propagation of operator authorization into parallel delegated tool execution while Spring services keep deterministic state-transition ownership.

`workflow-service` supports both a deterministic Arachne model mode for repeatable local verification and an alternate Bedrock-backed model mode through the standard `arachne.strands.model.*` configuration surface.

The capability-complete flow is still the opt-in Arachne-native workflow path in `workflow-service`, and the finance-control pause/resume runs through Arachne-native interrupt handling.

## Design Docs

Use these documents by responsibility, not interchangeably.

Treat this README as the public product-track overview.
Treat the other `docs/*.md` files as concept, requirements, architecture, API, contract, and skill-boundary references.
Treat `docs/requirements.md`, `docs/architecture.md`, `docs/apis.md`, `docs/contracts.md`, and `docs/skills.md` together as the source of truth for what is implemented today in this product track.

Use these documents to understand the shipped shape of this product track:

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
The current shipped boundaries are described across `docs/requirements.md`, `docs/architecture.md`, `docs/apis.md`, `docs/contracts.md`, and `docs/skills.md`.

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

The console now makes the agent/runtime boundary visible instead of treating the workflow as a flat chat log. The case detail view shows:

- activity categories for `agent`, `runtime`, `tool`, and `hook`
- a delegation trace from `case-workflow-agent` to specialist agents
- tool calls such as `resource_list`, `resource_reader`, `operator_authorization_probe`, and `finance_control_approval`
- approval-start control points where the hook forces `finance_control_approval` and the runtime registers the interrupt
- operator follow-up messages flowing back through `case-workflow-agent` before they are delegated

Common local operations for this product track are exposed through `make` from `marketplace-agent-platform/`.
Run `make help` there to see the current shortcuts.

The main startup modes are:

- `make up`: composed runtime with the default deterministic workflow path
- `make up-arachne`: composed runtime with the deterministic Arachne-native workflow path enabled
- `make up-bedrock`: composed runtime with the Bedrock-backed Arachne workflow path enabled

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

The `make` entrypoints keep using `docker compose up --build`, but they now set `COMPOSE_PARALLEL_LIMIT=1` by default.
That keeps build and startup integrated while reducing local BuildKit snapshot failures that can happen when all marketplace service images build at once from the shared Dockerfile setup.
If your Docker setup is stable under more concurrency, you can override that limit when launching make.

```bash
COMPOSE_PARALLEL_LIMIT=4 make up-bedrock
```

To enable the deterministic Arachne-native path instead of the default workflow path, use:

```bash
make up-arachne
```

To enable the Bedrock-backed Arachne path, you can export model settings before startup when you want to override the defaults:

```bash
export ARACHNE_STRANDS_MODEL_ID=your-bedrock-model-id
export ARACHNE_STRANDS_MODEL_REGION=us-east-1
make up-bedrock
```

If you omit those variables, Arachne falls back to its Bedrock defaults: model `jp.amazon.nova-2-lite-v1:0` in region `ap-northeast-1`.

`make up-bedrock` resolves AWS credentials at runtime by calling `aws configure export-credentials --format env-no-export` in the same shell that launches compose.
That means it can reuse whichever credentials the AWS CLI can currently resolve in your terminal session, including SSO-backed, assumed-role, and temporary session credentials.
If you want to force a specific profile, pass `AWS_PROFILE=...` to the make command.

```bash
AWS_PROFILE=my-sso-profile make up-bedrock
```

The target sets `MARKETPLACE_WORKFLOW_ARACHNE_ENABLED=true`, switches the workflow runtime to `bedrock`, and forwards the model plus resolved AWS credential environment variables into both workflow-service replicas.

Before compose starts, `make up-bedrock` now fails fast unless all of the following pass on the host shell:

- `aws configure export-credentials` can resolve credentials
- `aws sts get-caller-identity` succeeds

That preflight only proves the resolved principal can authenticate successfully from the current shell.
It intentionally does not validate model-specific Bedrock permissions because that would couple the startup target too tightly to the sample runtime behavior.

The workflow-service containers still do not mount host `~/.aws` files directly.
The supported path is to let the host AWS CLI resolve credentials first and then hand the resolved values to compose for that one launch.

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