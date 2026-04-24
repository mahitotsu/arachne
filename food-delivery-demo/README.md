# Food Delivery Demo

This directory now hosts the replacement demo for the old marketplace workflow sample.

The demo is a chat-first delivery app for a single-brand cloud kitchen. There is one kitchen only, no dine-in flow, and no branch switching. The frontend looks like a normal delivery app, but every backend API is fronted by a service-local Arachne agent. What looks like ordinary microservice traffic is also a multi-agent collaboration path.

It is intentionally not part of the runnable `samples/` catalog. The goal here is a composed, practical application slice that shows how naturally Arachne can disappear into Spring Boot microservices.

## Runtime Shape

The local runtime starts:

- `customer-ui`: Next.js chat UI for the customer-facing flow
- `customer-service`: demo customer directory, sign-in API, JWT issuer, and JWKS publisher
- `order-service`: public API, orchestration, Redis-backed chat session continuity, PostgreSQL-backed order persistence
- `menu-service`: menu discovery and substitution suggestions behind `menu-agent`
- `kitchen-service`: availability and prep timing behind `kitchen-agent`
- `delivery-service`: ETA and courier planning behind `delivery-agent`
- `payment-service`: payment preparation and deterministic charge execution behind `payment-agent`
- `postgres`
- `redis`

Each downstream service owns its own service-local agent. The APIs remain plain Spring HTTP boundaries, but the response text and coordination behavior come from Arachne runtimes embedded inside each service.

## Main Demo Story

1. The customer signs in through `customer-service` with a demo ID/password and receives a JWT access token.
2. The browser stays same-origin by calling `customer-ui` rewrites: `/api/customer/*` goes to `customer-service` and `/api/backend/*` goes to `order-service`.
3. The chat UI sends that bearer token to `order-service`.
4. `order-service` coordinates downstream services and keeps the active conversation in Redis.
5. `menu-service`, `kitchen-service`, `delivery-service`, and `payment-service` validate the same access token and answer through their own Arachne agents.
6. `kitchen-agent` can ask `menu-agent` for same-brand fallback items when the only kitchen cannot serve a requested item.
7. The UI shows both the user-facing reply and a visible service/agent trace so the microservice shape and multi-agent shape are both obvious.
8. The user chooses between partner-standard delivery and in-house express delivery when both are available.
9. When the user confirms the draft, `payment-service` performs a deterministic charge and `order-service` records the order in PostgreSQL.

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
- Customer service: `http://localhost:8085`
- Public chat API: `http://localhost:8080/api/chat`
- Menu service: `http://localhost:8081`
- Kitchen service: `http://localhost:8082`
- Delivery service: `http://localhost:8083`
- Payment service: `http://localhost:8084`

Demo sign-in accounts:

- `demo / demo-pass`
- `family / family-pass`

Key customer-service endpoints:

- `POST /api/auth/sign-in`
- `GET /api/customers/me`
- `GET /oauth2/jwks`


## Docs

- `docs/architecture.md`: runtime roles and ownership boundaries
- `docs/apis.md`: public and internal API surface