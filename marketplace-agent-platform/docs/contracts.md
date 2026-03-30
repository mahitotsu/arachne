# Marketplace Agent Platform Case And Approval Contracts

This note captures the minimal case-facing and approval-facing contracts for the first runnable slice.

Use [requirements.md](/home/akring/arachne/marketplace-agent-platform/docs/requirements.md) for what the sample must support.
Use [architecture.md](/home/akring/arachne/marketplace-agent-platform/docs/architecture.md) for runtime and development structure.
Use [apis.md](/home/akring/arachne/marketplace-agent-platform/docs/apis.md) for endpoint boundaries.
Use this file for the minimal contract shapes shown to the operator and passed through the approval path.

## Contract Boundary Rules

The first slice should preserve these rules:

- `case-service` owns the operator-facing case contract and durable case projection
- `workflow-service` owns workflow lifecycle and recommendation progression
- case-service-owned case contracts are durable workflow-aware projections, not the source of truth for every downstream business aggregate
- `escrow-service`, `shipment-service`, `risk-service`, and `notification-service` remain authoritative for their own domain state and service-local audit records
- case activity shown to the operator is assembled in case-service from workflow events and structured downstream results
- approval state is part of the case-service-served projection but produced by workflow handling
- final settlement outcome reflects deterministic execution in `escrow-service`, even when the case-facing contract is served by case-service

## Case Contract Direction

The first slice should treat `Case` as an operator-facing aggregate with durable workflow-aware projections.

That means:

- a case is the unit the operator opens, searches, resumes, and approves through the UI
- a case contains the current workflow-visible summary of downstream facts
- a case is not the canonical owner of escrow balances, shipment milestones, risk thresholds, or notification delivery state
- the frontend does not need to reason about whether a workflow instance currently exists; it reads the case projection only

### `CaseListItem`

`CaseListItem` is the minimal search and list projection.

Suggested fields:

- `caseId`
- `caseType`
- `caseStatus`
- `orderId`
- `amount`
- `currency`
- `currentRecommendation`
- `pendingApproval`
- `updatedAt`

### `CaseDetailView`

`CaseDetailView` is the primary operator-facing case projection.

Suggested fields:

- `caseId`
- `caseType`
- `caseStatus`
- `orderId`
- `transactionId`
- `amount`
- `currency`
- `currentRecommendation`
- `caseSummary`
- `evidence`
- `activityHistory`
- `approvalState`
- `outcome`

`CaseDetailView` should be complete enough to populate the full `Case Detail` screen without requiring direct frontend calls to downstream services.

## Case Status Direction

The first slice should keep case status simple and operator-readable.

Suggested workflow status values:

- `OPEN`
- `GATHERING_EVIDENCE`
- `AWAITING_APPROVAL`
- `READY_FOR_SETTLEMENT`
- `COMPLETED`

These statuses describe operator-visible workflow progression, not every downstream domain detail or internal runtime mechanics.

## Recommendation, Approval, And Outcome Boundary

The first slice should keep these three concepts separate.

### Recommendation

`currentRecommendation` is workflow-service-produced output served through case-service.

It represents:

- the current proposed next settlement direction
- the result of policy consultation plus delegated evidence gathering
- a value that may still require approval before execution

Suggested values for the first slice:

- `REFUND`
- `CONTINUED_HOLD`
- `PENDING_MORE_EVIDENCE`

### Approval State

`ApprovalStateView` represents the finance control decision gate.

Suggested fields:

- `approvalRequired`
- `approvalStatus`
- `requestedRole`
- `requestedAt`
- `decisionAt`
- `decisionBy`
- `comment`

Suggested approval status values:

- `NOT_REQUIRED`
- `PENDING_FINANCE_CONTROL`
- `APPROVED`
- `REJECTED`

Approval state is workflow-produced and case-service-served, but it does not itself execute settlement.

### Outcome

`OutcomeView` represents the final visible result after deterministic settlement execution.

Suggested fields:

- `outcomeType`
- `outcomeStatus`
- `settledAt`
- `settlementReference`
- `summary`

Suggested first-slice outcome values:

- `REFUND_EXECUTED`
- `CONTINUED_HOLD_RECORDED`

The outcome should only become final after `escrow-service` completes the transaction-owning action.

## Approval Commands And Responses

The first slice should keep approval contracts narrow and explicit.

### `SubmitApprovalCommand`

Purpose:

- carry a finance control approval or rejection back into the paused workflow

Suggested fields:

- `caseId`
- `decision`
- `comment`
- `actorId`
- `actorRole`

Suggested decision values:

- `APPROVE`
- `REJECT`

### `ApprovalSubmissionResult`

Purpose:

- confirm that the approval command was accepted into the workflow boundary
- report the updated approval state
- indicate whether settlement execution is now in progress or blocked

Suggested fields:

- `caseId`
- `approvalState`
- `workflowStatus`
- `resumeAccepted`
- `message`

## Activity Contract Direction

`ActivityEvent` should be case-service-owned and case-facing.

Suggested fields:

- `eventId`
- `caseId`
- `timestamp`
- `kind`
- `source`
- `message`
- `structuredPayload`

Suggested event kinds for the first slice:

- `CASE_CREATED`
- `DELEGATION_STARTED`
- `EVIDENCE_RECEIVED`
- `RECOMMENDATION_UPDATED`
- `APPROVAL_REQUESTED`
- `APPROVAL_SUBMITTED`
- `SETTLEMENT_COMPLETED`
- `NOTIFICATION_DISPATCHED`

`source` may name the originating service, but the visible event is still recorded in the case-service-owned case activity history.

## Service Ownership Reminder

This contract set should not be read as a transfer of all business ownership into case-service.

- `case-service` owns the operator-facing case projection and case-facing APIs
- `workflow-service` owns workflow sessions, workflow lifecycle, and recommendation progression
- `escrow-service` owns settlement state and mutation truth
- `shipment-service` owns shipment fact truth
- `risk-service` owns risk review truth
- `notification-service` owns notification delivery truth

Case-service is therefore a case-facing projection owner, while workflow-service is an orchestration hub. Neither is the canonical owner of every business aggregate.