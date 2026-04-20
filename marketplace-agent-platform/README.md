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

`workflow-service` supports both a deterministic Arachne model mode for repeatable local verification and an alternate Bedrock-backed model mode through the standard `arachne.strands.model.*` configuration surface, while `escrow-service`, `shipment-service`, and `risk-service` now host their own service-local specialist agents behind explicit internal APIs.

The capability-complete flow is still the opt-in Arachne-native workflow path in `workflow-service`, and the finance-control pause/resume runs through the Arachne-native interrupt boundary while post-resume settlement completion stays deterministic and Spring-owned once finance input has been accepted.

## Design Docs
# Food Delivery Agent Platform

This directory now hosts the replacement demo for the old marketplace workflow sample.

The new demo is a fast-food delivery web application with a chat-first ordering experience. The frontend looks like a normal delivery app, but every backend API is fronted by a service-local Arachne agent. What looks like ordinary microservice traffic is also a multi-agent collaboration path.

It is intentionally not part of the runnable `samples/` catalog. The goal here is a composed, practical application slice that shows how naturally Arachne can disappear into Spring Boot microservices.

## Runtime Shape

The local runtime starts:

- `operator-console`: Next.js chat UI for the customer-facing flow
- `order-service`: public API, orchestration, Redis-backed chat session continuity, PostgreSQL-backed order persistence
- `menu-service`: menu discovery and substitution suggestions behind `menu-agent`
- `kitchen-service`: availability and prep timing behind `kitchen-agent`
- `delivery-service`: ETA and courier planning behind `delivery-agent`
- `payment-service`: payment preparation and deterministic charge execution behind `payment-agent`
- `postgres`
- `redis`

Each downstream service owns its own service-local agent. The APIs remain plain Spring HTTP boundaries, but the response text and coordination behavior come from Arachne runtimes embedded inside each service.

## Main Demo Story

1. The user opens the chat UI and asks for a fast-food order in natural language.
2. `order-service` coordinates downstream services and keeps the active conversation in Redis.
3. `menu-service`, `kitchen-service`, `delivery-service`, and `payment-service` answer through their own Arachne agents.
4. The UI shows both the user-facing reply and a visible service/agent trace so the microservice shape and multi-agent shape are both obvious.
5. When the user confirms the draft, `payment-service` performs a deterministic charge and `order-service` records the order in PostgreSQL.

## Local Commands

Use `make` from this directory:

```bash
make up
make ps
make smoke
make down
```

Useful frontend-only commands:

```bash
make ui-install
make ui-build
make ui-dev
```

Backend verification:

```bash
make test
```

## Endpoints

- UI: `http://localhost:3000`
- Public chat API: `http://localhost:8080/api/chat`
- Menu service: `http://localhost:8081`
- Kitchen service: `http://localhost:8082`
- Delivery service: `http://localhost:8083`
- Payment service: `http://localhost:8084`

## Docs

- `docs/architecture.md`: runtime roles and ownership boundaries
- `docs/apis.md`: public and internal API surface