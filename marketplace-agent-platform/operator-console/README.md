# Operator Console

This directory contains the thin `React + TypeScript + Vite` operator console for the marketplace agent platform.

The UI talks only to `case-service` and covers the current first-slice flows:

- case creation
- case list and search
- case detail inspection
- follow-up operator messages
- activity history updates through SSE
- finance control approval submission

## Run

Install dependencies:

```bash
npm ci
```

Start the development server:

```bash
npm run dev
```

By default the console calls `http://localhost:8080` for `case-service`.

Override that with:

```bash
VITE_CASE_SERVICE_BASE_URL=http://localhost:8080 npm run dev
```

## Current Limits

- authentication is modeled through explicit operator id and role inputs rather than a real login flow
- the UI assumes the current backend slice and contract shapes under `case-service`
- the runtime still depends on the backend services and workflow load balancer being available separately