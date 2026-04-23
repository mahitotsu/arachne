# Food Delivery Demo APIs

## Public API

`order-service`

- `POST /api/chat`
  Accepts a chat turn and returns the updated conversation, order draft, routing decision, and agent trace.
- `GET /api/session/{sessionId}`
  Restores the current chat transcript and draft for a browser refresh.

## Internal APIs

`menu-service`

- `POST /internal/menu/suggest`
  Accepts the current user intent and returns candidate menu items plus `menu-agent` commentary.

`kitchen-service`

- `POST /internal/kitchen/check`
  Accepts selected menu item ids and returns stock, prep timing, substitution guidance from `kitchen-agent`, and optional collaborator trace entries when `kitchen-agent` asks `menu-agent` for fallback help.

`menu-service`

- `POST /internal/menu/substitutes`
  Accepts an unavailable item plus the customer context and returns menu-agent fallback candidates for `kitchen-agent` to validate.

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

`order-service` also returns a deterministic routing summary for the current turn so the trace can show:

- the interpreted customer intent
- the selected workflow skill
- the workflow entry step used for that turn

That split keeps Spring in charge of correctness while still making the Arachne layer visible.