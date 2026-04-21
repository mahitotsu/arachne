# Food Delivery Demo

This directory now hosts the replacement demo for the old marketplace workflow sample.

The new demo is a fast-food delivery web application with a chat-first ordering experience. The frontend looks like a normal delivery app, but every backend API is fronted by a service-local Arachne agent. What looks like ordinary microservice traffic is also a multi-agent collaboration path.

It is intentionally not part of the runnable `samples/` catalog. The goal here is a composed, practical application slice that shows how naturally Arachne can disappear into Spring Boot microservices.

## Runtime Shape

The local runtime starts:

- `customer-ui`: Next.js chat UI for the customer-facing flow
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