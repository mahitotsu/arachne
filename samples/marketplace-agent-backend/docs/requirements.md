# Marketplace Agent Backend Requirements

This note captures requirements for the marketplace agent backend sample.

Use [concept.md](/home/akring/arachne/samples/marketplace-agent-backend/docs/concept.md) for the sample's purpose, value proposition, and explanation story.
Use [architecture.md](/home/akring/arachne/samples/marketplace-agent-backend/docs/architecture.md) for execution and development structure.
Use this file for what the sample must support.

## Scope Level

These requirements are for the first substantial sample slice, not necessarily the full long-term vision.

The sample must demonstrate a marketplace backend that supports multiple case types through service-local agents and a thin operator-facing frontend.

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
2. the coordinator activates the appropriate skill
3. the coordinator consults policy and runbook resources
4. the coordinator delegates evidence collection to shipment, escrow, and risk services
5. the system displays progress while work is in flight
6. the workflow can interrupt for approval before final settlement action
7. the workflow can resume later from the existing Arachne resume boundary
8. deterministic backend execution completes the settlement action
9. the outcome is visible in the case detail screen

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

## Backend Service Requirements

The first architecture slice must include these logical backend services:

1. `case-coordinator-service`
2. `escrow-service`
3. `shipment-service`
4. `risk-service`
5. `notification-service`

Each backend service must own one named service-local agent.

## Frontend Requirements

The sample must include a thin operator-facing frontend.

The frontend must:

- remain thin and not own business workflow logic
- expose case list and case detail views
- display activity updates from backend streaming
- expose approval actions when the workflow pauses

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
- deterministic services own correctness-sensitive state changes
- the sample demonstrates multiple case types without requiring many screens
- the representative story should stay easy to explain to new readers

## Open Requirement Questions

These items still need explicit resolution before implementation planning hardens:

1. whether `notification-service` is a full separate runtime in the first slice
2. whether Redis is required in the first runnable slice
3. whether approval is owned by finance control, risk review, or another actor
4. whether the first demonstrated outcome is only `refund` or also `continued_hold`