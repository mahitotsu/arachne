# Customer UI

This Next.js app is the customer-facing UI for the food delivery demo.

It keeps the ordering interaction chat-first, but it also shows:

- the customer sign-in state backed by `customer-service`
- the current order draft
- estimated delivery timing
- payment state
- the service-local agent trace behind the reply, including the order-agent's visible intent and workflow selection

Run locally:

```bash
npm ci
npm run dev
```

Use one of the demo accounts to sign in before ordering:

- `demo / demo-pass`
- `family / family-pass`

The app proxies `/api/customer/*` to `customer-service` through `CUSTOMER_SERVICE_ORIGIN` and `/api/backend/*` to `order-service` through `BACKEND_ORIGIN`.

For the demo, the browser stores the access token in local storage and sends it as a bearer token on chat and session requests.

For container builds, both `CUSTOMER_SERVICE_ORIGIN` and `BACKEND_ORIGIN` must be set at build time so Next.js bakes the correct rewrite targets into the standalone output. The compose setup passes `http://customer-service:8080` and `http://order-service:8080` for Docker, while local development defaults stay on `http://localhost:8085` and `http://localhost:8080`.

The food-delivery services default to `DELIVERY_MODEL_MODE=deterministic` for repeatable local verification. For Bedrock-backed runs, use `make up-bedrock` so the host's temporary AWS credentials are exported into compose along with `ARACHNE_STRANDS_MODEL_ID` and `ARACHNE_STRANDS_MODEL_REGION`.