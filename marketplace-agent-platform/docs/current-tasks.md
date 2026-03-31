# Marketplace Agent Platform Current Tasks

Status legend:

- `done`: implemented and verified on the current branch
- `in-progress`: active work item for the current push
- `next`: ready to start after the active work item
- `later`: intentionally sequenced after the deterministic first-slice closeout work
- `deferred`: not part of the current deterministic implementation push

## Current Task List

| Status | Task | Notes |
| --- | --- | --- |
| `done` | Establish root-level product-track boundary | Independent from `samples/`; repo docs and doc-surface test already aligned |
| `done` | Build deterministic service-backed scaffold | Case, workflow, escrow, shipment, risk, notification, and operator console are present |
| `done` | Prove continued-hold approval completion path | Covered in service tests and visible in the current UI |
| `done` | Prove approval rejection re-entry path | Workflow returns to evidence gathering without settlement |
| `done` | Add deterministic refund outcome path | Lower-value `ITEM_NOT_RECEIVED` cases now reach `REFUND_EXECUTED` through the same approval boundary |
| `done` | Prove replica-safe workflow continuity | Explicitly covered by a cross-replica Redis integration test plus `mvn test` under `marketplace-agent-platform` |
| `done` | Tighten deterministic search and operator states | Case search now matches structured operator-facing state and the queue surfaces approval/outcome state more clearly |
| `done` | Re-evaluate deterministic Slice 1 closeout | README, plan, tests, and UI evidence now support treating the deterministic first slice as closed |
| `later` | Start the next implementation theme for Arachne-native capabilities | Only begin when named agents, skills, native resume, streaming, steering, and context propagation are explicitly in scope |
| `deferred` | Introduce named Arachne agents and Bedrock runtime behavior | Must update README and tests when this starts |
| `deferred` | Introduce packaged skills and built-in resource tools | Not shipped in the current product-track boundary |
| `deferred` | Replace deterministic interrupt/resume simulation with native Arachne wiring | Keep outside the current deterministic push |
| `deferred` | Add steering and execution-context propagation as visible runtime behavior | Do not imply this is implemented until the boundary changes |

## Next Action Checklist

The next concrete implementation pass should do only the following:

1. preserve the closed deterministic slice as the documented baseline
2. keep the current search, approval, and continuity evidence aligned with README and docs
3. avoid starting deferred Arachne-native capability work until the next implementation theme is explicit
