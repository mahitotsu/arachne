# Operator Console

This Next.js app is the customer-facing UI for the food delivery demo.

It keeps the ordering interaction chat-first, but it also shows:

- the current order draft
- estimated delivery timing
- payment state
- the service-local agent trace behind the reply

Run locally:

```bash
npm ci
npm run dev
```

The app proxies `/api/backend/*` to `order-service` through `BACKEND_ORIGIN`.

For container builds, `BACKEND_ORIGIN` must be set at build time so Next.js bakes the correct rewrite target into the standalone output. The compose setup passes `http://order-service:8080` for Docker and keeps `http://localhost:8080` as the default for local development.

The food-delivery services default to `DELIVERY_MODEL_MODE=deterministic` for repeatable local verification. For Bedrock-backed runs, use `make up-bedrock` so the host's temporary AWS credentials are exported into compose along with `ARACHNE_STRANDS_MODEL_ID` and `ARACHNE_STRANDS_MODEL_REGION`.