# Marketplace Agent Platform Requirements

This note captures requirements for the marketplace agent platform sample.

Use [concept.md](/home/akring/arachne/marketplace-agent-platform/docs/concept.md) for the sample's purpose, value proposition, and explanation story.
Use [architecture.md](/home/akring/arachne/marketplace-agent-platform/docs/architecture.md) for execution and development structure.
Use this file for what the sample must support.

## Scope Level

These requirements define the currently shipped capability-complete sample scope.

The sample demonstrates a marketplace platform that supports multiple case types through service-local agents and a thin operator-facing frontend.

## Functional Requirements

### Supported Case Types

The initial slice should support at least these case types:

1. `ITEM_NOT_RECEIVED`
2. `DELIVERED_BUT_DAMAGED`
3. `HIGH_RISK_SETTLEMENT_HOLD`
4. `SELLER_CANCELLATION_AFTER_AUTHORIZATION`

The first end-to-end implementation target is `ITEM_NOT_RECEIVED`.

### Operator Screens

The sample should expose exactly two operator-facing screens in the first slice.

#### `Case List`

The sample must support:

- listing existing cases
- agentic case search
- starting a new case
- navigating to case detail

#### `Case Detail`

The sample must support:

- chat-driven case creation and continuation
- structured case state display
- evidence display
- activity history display
- approval interaction when required
- final outcome display

### Case Detail Regions

The `Case Detail` screen must include these functional regions:

1. `Chat`
2. `Case Summary`
3. `Evidence`
4. `Activity History`
5. `Approval And Outcome`

### Representative Workflow

The sample must support this representative flow:

1. an operator opens an `ITEM_NOT_RECEIVED` case
2. `case-service` creates the case and starts workflow handling
3. `workflow-service` activates the appropriate skill
4. `workflow-service` consults policy and runbook resources
5. `workflow-service` delegates evidence collection to shipment, escrow, and risk services
6. the system displays progress through case-facing activity updates while work is in flight
7. the workflow can interrupt for approval before final settlement action
8. the workflow can resume later from the existing Arachne resume boundary through a case-facing approval command path
9. deterministic backend execution completes the settlement action
10. the outcome is visible in the case detail screen

The first slice should support at least these representative final outcomes:

- `REFUND`
- `CONTINUED_HOLD`

## Arachne Capability Requirements

The sample must use these Arachne capabilities in non-decorative ways.

### Required

- named agents
- agent delegation across service boundaries
- structured output
- packaged skills
- built-in resource tools
- sessions
- interrupt and resume
- streaming
- steering
- execution context propagation

### Required Interpretation

These capabilities must appear in ways that are visible to the user or to the reader of the sample code.

- streaming must result in operator-visible activity updates
- interrupt and resume must result in visible pending approval and later continuation
- structured output must populate stable case fields or evidence summaries
- delegation must correspond to service-local responsibilities
- steering must visibly block or redirect an unsafe path

## Security Requirements

The sample must demonstrate Spring-oriented security behavior as a first-class concern.

### Operator Identity

The sample must treat the operator as an authenticated actor rather than as anonymous prompt input.

At minimum, the first slice must support:

- authenticated operator identity at the frontend, case-service, and workflow-service boundary
- operator role or authority information available to backend workflow handling
- explicit authorization-sensitive behavior in case handling

Minimum first-slice operator roles:

- `CASE_OPERATOR` for case creation, search, chat continuation, and status inquiry
- `FINANCE_CONTROL` for approval of settlement-changing actions that require finance control review

### Authorization

The sample must show that correctness-sensitive actions are guarded by deterministic authorization checks.

At minimum, the first slice must demonstrate:

- at least one settlement-changing action such as `REFUND` or `RELEASE_FUNDS` requiring elevated authority
- authorization failure represented as explicit backend outcome rather than model narration
- approval and authorization treated as separate concerns

Approval actor for the first slice:

- settlement approval is owned by `finance control`
- the approval step is therefore not a generic operator action
- finance control approval does not bypass deterministic authorization checks in the mutating backend service

### Propagation

The sample must propagate relevant operator authorization context across delegated execution.

This should be visible in the sample through:

- case-service-owned operator entry context
- workflow-service receiving that context for workflow handling
- delegated execution receiving that context in downstream service work
- service-local authorization checks using propagated context

## Transaction Requirements

The sample must demonstrate Spring-oriented transaction boundaries for correctness-sensitive state changes.

### Mutation Ownership

The sample must show that transaction ownership belongs to deterministic backend services rather than to the workflow-service agent.

At minimum, the first slice must demonstrate:

- settlement-changing actions executed inside an explicit transaction boundary
- read-oriented evidence collection kept separate from mutation logic
- workflow orchestration staying outside transaction ownership

### Transaction Visibility

The sample should make it clear which actions are transactional and why.

At minimum, the design should distinguish:

- read-only shipment and risk review work
- settlement mutation work in `escrow-service`
- post-decision notification behavior separated from core settlement mutation

## Availability Requirements

The sample should demonstrate enterprise-relevant availability behavior without turning into a failure-injection demo.

### Workflow Continuity

The sample must show that case workflows can continue correctly when requests are distributed across multiple workflow-service instances.

At minimum, the first slice should support:

- multiple `workflow-service` instances behind an internal load balancer
- shared session persistence for case workflow state
- continued case progression when successive workflow requests are served by different workflow-service instances

### Load-Balanced Progression

The sample should not require sticky sessions for normal case progression.

At minimum, the first slice should demonstrate that these interactions remain valid through the case-facing entry point even when workflow handling is load-balanced internally:

- case creation
- follow-up chat turns
- status inquiry
- approval submission and resume

Those interactions may enter through `case-service`, while the actual workflow handling is routed across multiple `workflow-service` instances.

### Visibility

The sample should make this availability property understandable to the reader.

At minimum, the design should make it clear that:

- workflow and conversation state are externalized to a shared persistence layer
- workflow-service replicas are replaceable from the workflow point of view
- availability is being shown through replica-safe continuity, not through forced failure choreography

## Backend Service Requirements

The first architecture slice must include these logical backend services:

1. `case-service`
2. `workflow-service`
3. `escrow-service`
4. `shipment-service`
5. `risk-service`
6. `notification-service`

Each backend service must own one named service-local agent.

Each backend service must remain responsible for its own business domain.

At minimum, the first slice must preserve these responsibility boundaries:

- `case-service` owns case creation, case list/detail/search, and durable operator-facing case projections
- `workflow-service` owns workflow orchestration, workflow sessions, recommendations, and approval pause/resume control
- `escrow-service` owns escrow and settlement business state and settlement-changing actions
- `shipment-service` owns shipment evidence and shipment interpretation responsibilities
- `risk-service` owns risk review and escalation responsibilities
- `notification-service` owns post-decision notification responsibilities

`case-service` must not become the canonical owner of escrow, shipment, risk, or notification domain state.
`workflow-service` must not become the canonical owner of case search, list, and detail projections.

## Frontend Requirements

The sample must include a thin operator-facing frontend.

The frontend must:

- remain thin and not own business workflow logic
- expose case list and case detail views
- display activity updates from backend streaming
- expose approval actions when the workflow pauses

The frontend should interact with `case-service` only in the first slice.

The frontend must not become a general-purpose workflow product in the first slice.

## Case Detail Data Requirements

### `Chat`

Must allow:

- case creation request
- follow-up questions
- status inquiry
- operator instructions
- approval comments or decision entry

### `Case Summary`

Must display:

- case id
- type
- order or transaction identifiers
- amount and currency
- workflow status
- current recommendation

### `Evidence`

Must display:

- shipment evidence summary
- escrow and settlement summary
- risk review summary
- policy or runbook references used in the workflow

### `Activity History`

Must display:

- streamed execution updates
- delegation checkpoints
- steering-related checkpoints
- interrupt and resume markers

### `Approval And Outcome`

Must display:

- pending approval state
- approver actions
- approval comment
- final decision and settlement outcome

## Non-Functional Direction

The sample should preserve these qualities.

- the backend remains the architectural center
- the frontend stays intentionally thin
- Spring Security and transaction concerns are part of the sample's enterprise value
- availability should be demonstrated through shared-state workflow continuity
- deterministic services own correctness-sensitive state changes
- the sample demonstrates multiple case types without requiring many screens
- the representative story should stay easy to explain to new readers

## Open Requirement Questions

No open requirement questions remain at the current concept-document level.