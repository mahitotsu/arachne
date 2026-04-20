---
description: "Use when working in the marketplace operator-console React + TypeScript + Vite app."
applyTo: "marketplace-agent-platform/operator-console/**"
---

# Operator Console Workflow

- Treat `marketplace-agent-platform/operator-console` as a thin `React + TypeScript + Vite` frontend over the marketplace product-track APIs.
- Use `npm ci` and `npm run build` as the default verification commands. Keep `npm run dev` and the README run path aligned when tooling changes.
- Preserve the same-origin `/api` development shape unless the task explicitly changes the contract and updates the README in the same turn.
- Keep the console thin and contract-driven. Do not move workflow or business-state ownership out of the backend services.
- When behavior changes, update the nearest README and keep verification in the same turn.
- If tests are added for new UI behavior, prefer deterministic component-level coverage such as Vitest and Testing Library instead of browser-only checks.