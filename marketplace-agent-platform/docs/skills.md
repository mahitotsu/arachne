# Marketplace Agent Platform Skill Boundaries

This note captures the recommended skill boundaries for the first runnable slice.

Use [concept.md](/home/akring/arachne/marketplace-agent-platform/docs/concept.md) for the sample story and capability intent.
Use [architecture.md](/home/akring/arachne/marketplace-agent-platform/docs/architecture.md) for service boundaries and runtime structure.
Use [apis.md](/home/akring/arachne/marketplace-agent-platform/docs/apis.md) and [contracts.md](/home/akring/arachne/marketplace-agent-platform/docs/contracts.md) for the contracts that skills must respect.
Use this file for what each service-local agent should know, what procedures it should follow, and what must stay outside skill logic.

## Skill Boundary Rules

The first slice should preserve these rules:

- skills encode business procedure, interpretation, and resource-guided reasoning
- skills do not become a substitute for deterministic Spring application logic
- each service-local agent should only hold skill knowledge that matches the service's business responsibility
- workflow-service may own broader workflow skills than case-service and downstream services
- case-service may use narrower search or query interpretation skills without becoming a workflow owner
- downstream services may use narrower interpretation skills without becoming generic reasoning hubs
- settlement mutation, authorization enforcement, and durable business state changes remain deterministic service logic

## Skill Families By Service

The first runnable slice should use asymmetric skill depth across services.

### `case-agent`

This agent may remain intentionally narrow in the first slice.

Recommended skill family:

- `case-search-interpretation`

What this skill does:

- interpret operator search phrases into case-facing search intent
- help translate broad case queries into structured case-service filtering or ranking behavior

What this skill must not do:

- start or control long-running business workflows
- become the owner of case creation rules or workflow decisions

### `case-workflow-agent`

This is the only agent that needs a clearly workflow-oriented skill family in the first slice.

Recommended skill family:

- `marketplace-dispute-intake`
- `item-not-received-investigation`
- `approval-escalation-and-resume`

What these skills do:

- interpret operator intent in the case context
- choose the representative workflow path for `ITEM_NOT_RECEIVED`
- read policy and runbook resources through built-in resource tools
- delegate evidence collection to escrow, shipment, and risk services
- assemble a typed recommendation from returned evidence summaries
- decide when finance control approval is required
- resume the workflow after approval submission

What these skills must not do:

- execute settlement mutations directly
- bypass deterministic authorization or approval checks
- become the canonical owner of downstream business facts

### `escrow-agent`

This agent should stay narrower than the workflow-service agent.

Recommended skill family:

- `escrow-evidence-interpretation`
- `settlement-eligibility-summary`

What these skills do:

- interpret escrow hold state for workflow use
- produce structured eligibility summaries
- explain why a refund or continued hold is or is not eligible from the escrow perspective

What these skills must not do:

- perform the actual settlement mutation as skill logic
- decide authorization outcomes through free-form reasoning
- replace deterministic transaction-owned settlement services

### `shipment-agent`

This agent should be interpretation-oriented.

Recommended skill family:

- `shipment-evidence-review`
- `delivery-confidence-summary`

What these skills do:

- interpret tracking milestones and shipment events
- summarize delivery confidence or exception signals
- produce structured shipment evidence for workflow-service consumption

What these skills must not do:

- own the final dispute outcome
- mutate shipment business records for workflow convenience

### `risk-agent`

This agent should also be interpretation-oriented.

Recommended skill family:

- `risk-review-summary`
- `manual-review-trigger-check`

What these skills do:

- summarize fraud and compliance indicators
- interpret threshold and policy triggers relevant to the case
- provide structured review output used by the workflow-service recommendation path

What these skills must not do:

- replace deterministic threshold enforcement where the backend needs strict control
- become the owner of approval decisions

### `notification-agent`

This agent is intentionally thin in the current slice.

Recommended skill family:

- `outcome-notification-composition`

What this skill does:

- choose the appropriate message template or summary for participant-facing and operator-facing notification flows
- produce structured notification inputs for deterministic dispatch handling

What this skill must not do:

- decide settlement outcomes
- own delivery-state durability outside notification service logic

## Knowledge And Resource Sources

The first slice should keep knowledge sources explicit and narrow.

### Workflow-Service Resources

The workflow-service agent should rely on packaged resources such as:

- dispute handling runbooks
- settlement policy summaries
- approval threshold references

These should be accessed through built-in resource tools rather than a custom policy lookup tool unless the built-in tools prove insufficient.

### Downstream Service Knowledge

Downstream service-local agents should rely primarily on service-owned facts plus small packaged guidance where needed.

- `escrow-agent`: escrow rules, hold-state interpretation guidance, settlement eligibility criteria summaries
- `shipment-agent`: shipment status interpretation guidance, carrier event normalization references
- `risk-agent`: risk factor interpretation guidance, manual-review trigger references
- `notification-agent`: notification templates, audience rules, channel mapping guidance

The downstream agents should not read broad cross-domain runbooks that effectively relocate workflow-service knowledge into every service.

## Deterministic Logic Boundary

These areas must remain deterministic application logic rather than skill logic:

- persistence of cases, projections, audit records, and business state
- authorization checks for settlement-changing actions
- approval command acceptance and workflow resume plumbing
- transaction-owned settlement execution in `escrow-service`
- notification dispatch persistence and delivery recording
- request validation and API contract enforcement

If a behavior must always happen the same way for correctness, it should not depend on skill wording.

## First-Slice Minimum Skill Set

The first runnable slice does not need a large skill catalog.

Minimum recommended set:

- one case-service search interpretation skill
- one workflow-service skill for `ITEM_NOT_RECEIVED`
- one workflow-service skill or sub-procedure for approval escalation and resume
- one escrow interpretation skill
- one shipment interpretation skill
- one risk interpretation skill
- thin notification composition skill

This is enough to make skills visible and necessary without turning slice 1 into a skill taxonomy exercise.

## Phase 1 Representative Activation Set

For the current Phase 1 implementation, the representative `ITEM_NOT_RECEIVED` path activates only the smallest skill and resource surface that makes the workflow visibly Arachne-native.

### Activated Skills

- `case-workflow-agent`
	- `marketplace-dispute-intake`
	- `item-not-received-investigation`
	- `approval-escalation-and-resume`
- `escrow-agent`
	- `settlement-eligibility-summary`
- `shipment-agent`
	- `shipment-evidence-review`
- `risk-agent`
	- `risk-review-summary`

Not yet required on the representative dispute-handling path:

- `case-agent` search interpretation, which stays scoped to list and search behavior

Implemented thin downstream composition on the post-settlement path:

- `notification-agent` with `outcome-notification-composition`, which shapes structured notification inputs inside `notification-service` while dispatch persistence and delivery state remain deterministic service logic

### Resource-Tool Usage

Phase 1 should keep built-in resource-tool usage inside `workflow-service` and keep it legible to a reader.

Required packaged resources for the representative path:

- dispute-handling runbook for the `ITEM_NOT_RECEIVED` investigation procedure
- settlement policy summary that frames `REFUND` versus `CONTINUED_HOLD`
- finance-control approval threshold reference that explains why the workflow pauses for approval

Required built-in tool usage pattern:

- use `resource_list` to discover the allowlisted workflow-skill bundle when the agent needs to orient itself
- use `resource_reader` for the specific runbook and policy documents that influence recommendation shaping or approval escalation
- do not introduce a custom policy lookup tool in Phase 1 unless the built-in resource tools fail to cover a concrete workflow need

### Structured Output Boundary

The activated skills should return typed recommendation, evidence-summary, and approval-gate data that `workflow-service` can hand back to deterministic Spring application logic.

The skills should not own:

- settlement mutation execution
- case projection persistence
- authorization enforcement
- notification delivery recording

## Expansion Direction After Slice 1

Only after the first runnable slice is stable should the skill set broaden.

Likely expansion areas:

- case-type-specific workflow skills for `DELIVERED_BUT_DAMAGED` and other future cases
- richer service-local interpretation variants in shipment and risk services
- cleaner packaging of reusable policy and runbook fragments
- more explicit separation between base workflow skill and approval sub-procedure skill

## Failure Modes To Avoid

The sample should avoid these skill-design traps:

- making every service agent equally broad just for symmetry
- moving deterministic settlement or authorization behavior into prompts or skills
- duplicating the same cross-domain workflow knowledge into all services
- using skills to hide weak API boundaries
- turning notification into a generic policy engine in the first slice