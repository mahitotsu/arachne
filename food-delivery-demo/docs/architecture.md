# Food Delivery Demo Architecture

This product track demonstrates a customer-facing delivery app where service boundaries and agent boundaries line up.

## Intent

- keep the UI familiar: a chat-first food ordering flow
- keep the backend recognizably Spring microservices
- make the hidden agent mesh visible through traces instead of through an artificial orchestration UI
- keep correctness-sensitive state changes deterministic inside Spring services

## Services

- `order-service`
  Public entrypoint, chat orchestration, Redis-backed session restore, PostgreSQL-backed confirmed orders.
- `menu-service`
  Owns menu search and substitution suggestions through `menu-agent`.
- `kitchen-service`
  Owns stock and prep-time interpretation through `kitchen-agent`.
- `delivery-service`
  Owns ETA estimation and routing options through `delivery-agent`.
- `payment-service`
  Owns payment method guidance through `payment-agent`, while the actual charge remains deterministic.
- `customer-ui`
  Next.js web app that shows the customer chat, current order draft, and the service-to-agent trace.

## Runtime Story

1. The browser sends a chat turn to `order-service`.
2. `order-service` restores the current chat session from Redis.
3. `order-service` fans out to the downstream service APIs.
4. Each downstream API invokes a service-local Arachne agent before returning.
5. `order-service` merges those service results, runs its own `order-agent`, and returns a customer-facing reply plus a trace.
6. On confirmation, `payment-service` executes the charge and `order-service` persists the final order in PostgreSQL.

## Why This Fits Arachne

- the frontend interaction is naturally conversational
- the service decomposition is still normal Spring Boot engineering
- the multi-agent behavior emerges at backend seams instead of replacing the backend with a giant chat prompt
- users can see why the answer changed: stock, ETA, menu substitutions, and payment readiness are all traceable to specific services and agents