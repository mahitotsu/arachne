# Food Delivery Demo APIs

## Public API

`order-service`

- `POST /api/chat`
  Accepts a chat turn and returns the updated conversation, order draft, and agent trace.
- `GET /api/session/{sessionId}`
  Restores the current chat transcript and draft for a browser refresh.

## Internal APIs

`menu-service`

- `POST /internal/menu/suggest`
  Accepts the current user intent and returns candidate menu items plus `menu-agent` commentary.

`kitchen-service`

- `POST /internal/kitchen/check`
  Accepts selected menu item ids and returns stock, prep timing, and substitution guidance from `kitchen-agent`.

`delivery-service`

- `POST /internal/delivery/quote`
  Accepts the draft order and returns delivery ETA options from `delivery-agent`.

`payment-service`

- `POST /internal/payment/prepare`
  Accepts the draft order and returns payment readiness plus optional deterministic charge execution.

## Response Shape Principle

Each internal service returns both:

- deterministic structured data for the orchestrator
- an agent-facing summary string for the UI trace

That split keeps Spring in charge of correctness while still making the Arachne layer visible.