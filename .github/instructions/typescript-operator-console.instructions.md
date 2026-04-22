---
description: "Use when working in the food-delivery customer-ui Next.js + TypeScript app."
applyTo: "food-delivery-demo/customer-ui/**"
---

# Customer UI Workflow

- Treat `food-delivery-demo/customer-ui` as a thin `Next.js + TypeScript` frontend over the food-delivery product-track APIs.
- Use `npm ci` and `npm run build` as the default verification commands. Keep `npm run dev` and the README run path aligned when tooling changes.
- Preserve the same-origin backend proxy shape unless the task explicitly changes the contract and updates the README in the same turn.
- Keep the console thin and contract-driven. Do not move workflow or business-state ownership out of the backend services.
- When behavior changes, update the nearest README and keep verification in the same turn.
- If tests are added for new UI behavior, prefer deterministic component-level coverage instead of browser-only checks.