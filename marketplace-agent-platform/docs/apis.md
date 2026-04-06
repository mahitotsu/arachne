# Marketplace Agent Platform Minimal APIs

This note captures the minimal API surface for the first runnable slice.

Use [requirements.md](/home/akring/arachne/marketplace-agent-platform/docs/requirements.md) for what the sample must support.
Use [architecture.md](/home/akring/arachne/marketplace-agent-platform/docs/architecture.md) for runtime and development structure.
Use [contracts.md](/home/akring/arachne/marketplace-agent-platform/docs/contracts.md) for case and approval contract definitions.
Use this file for the smallest useful frontend and service API boundaries.

## API Boundary Rules

The first slice should preserve these boundary rules:

- the frontend talks only to `case-service`
- `case-service` owns the case-facing CRUD, query, and search API surface for operator workflows
- `workflow-service` owns internal workflow commands and session-driven case progression
- case search remains part of the case-service API surface in the first slice
- downstream services do not expose UI-facing APIs directly to `operator-console`
- read-oriented evidence APIs stay separate from mutation-oriented settlement APIs
- `case-service` does not execute cross-service business workflows itself
- `workflow-service` orchestrates across services but does not own business mutations for escrow state
- case-service-owned case views are durable projections rather than the source of truth for escrow, shipment, risk, or notification domain state
- structured responses, not chat text, feed `Case Summary`, `Evidence`, `Activity History`, and `Approval And Outcome`
- operator identity, authority, case id, and correlation context propagate across delegated backend calls

## Frontend To Case-Service APIs

The first slice should expose one UI-facing API surface through `case-service`.

### Required Endpoints

| Endpoint | Purpose | Notes |
| --- | --- | --- |
| `POST /api/cases` | Create a new case from operator chat input and case metadata | Persists the case, starts workflow handling internally, and returns the initial case view |
| `GET /api/cases` | List existing cases and support case-service-led agentic search | Query parameters can carry search text, type, status, and paging |
| `GET /api/cases/{caseId}` | Fetch the current case detail view | Returns stable structured data for the full case detail screen |
| `POST /api/cases/{caseId}/messages` | Continue a case with a new operator chat turn | Accepted by case-service and forwarded into workflow handling |
| `GET /api/cases/{caseId}/activity-stream` | Subscribe to live case activity updates | SSE endpoint used for streaming activity history updates |
| `POST /api/cases/{caseId}/approvals` | Submit an approval or rejection from finance control | Accepted by case-service and forwarded into the workflow resume path |

### Case-Service Responses

Case-service should respond with stable view-oriented contracts rather than raw agent transcripts.

Those case-service-managed responses are case-facing workflow contracts, not canonical ownership of every downstream business aggregate.

Minimum response shapes:

- `CaseListItem`
- `CaseDetailView`
- `ActivityEvent`
- `ApprovalSubmissionResult`

`CaseDetailView` should carry the data needed for these screen regions:

- `Case Summary`
- `Evidence`
- `Activity History`
- `Approval And Outcome`

The UI may still display chat messages, but structured case state remains the primary contract.

## Case-Service To Workflow-Service Internal APIs

The first slice should keep the workflow boundary explicit even when the frontend does not call it directly.

### Required Internal Workflow Commands

| Endpoint | Purpose | Notes |
| --- | --- | --- |
| `POST /internal/workflows` | Start workflow handling for a newly created case | Called by case-service after case persistence |
| `POST /internal/workflows/{caseId}/messages` | Continue workflow handling for a follow-up chat turn | Called by case-service after validating the case-facing command |
| `POST /internal/workflows/{caseId}/approvals` | Resume workflow handling after finance control input | Called by case-service after accepting the approval command |

Workflow-service should return structured workflow updates, not UI-facing responses.

Minimum internal update shapes:

- `WorkflowStartResult`
- `WorkflowProgressUpdate`
- `WorkflowResumeResult`

## Workflow-Service To Case-Service Projection Updates

Workflow-service should update case-facing projections explicitly.

Minimum projection update shapes:

- `CaseProjectionUpdate`
- `CaseActivityAppend`
- `ApprovalProjectionUpdate`
- `OutcomeProjectionUpdate`

## Workflow-Service To Downstream Service APIs

The first slice should keep service-to-service APIs synchronous and explicit.

### Escrow Service

Escrow owns both evidence about settlement state and the final settlement mutation.

Read-oriented endpoint:

- `POST /internal/escrow/evidence-summary`
- `POST /internal/escrow/specialist-review`

Purpose:

- return escrow hold state
- return settlement eligibility summary
- return amount and currency facts
- return prior settlement or refund facts relevant to the case
- answer workflow follow-up delegation through the service-local `escrow-agent`

Mutation-oriented endpoint:

- `POST /internal/escrow/settlement-actions`

Purpose:

- execute `REFUND`
- execute `CONTINUED_HOLD`
- persist deterministic settlement audit information

The mutation endpoint is the transaction-owning boundary.

### Shipment Service

Shipment is read-oriented in the first slice.

Endpoint:

- `POST /internal/shipment/evidence-summary`
- `POST /internal/shipment/specialist-review`

Purpose:

- return shipment milestones
- return tracking facts
- return delivery-confidence summary
- return shipping exception interpretation for the current case
- answer workflow follow-up delegation through the service-local `shipment-agent`

### Risk Service

Risk is read-oriented in the first slice.

Endpoint:

- `POST /internal/risk/case-review`
- `POST /internal/risk/specialist-review`

Purpose:

- return risk indicators
- return manual-review triggers
- return threshold or policy flags relevant to the case
- return a structured risk review summary for recommendation building
- answer workflow follow-up delegation through the service-local `risk-agent`

### Notification Service

Notification remains outside settlement transaction ownership.

Endpoint:

- `POST /internal/notifications/case-outcome`

Purpose:

- dispatch post-decision notifications after settlement completion
- record notification delivery attempts and status
- compose structured participant-facing and operator-facing notification inputs behind the service-local `notification-agent`
- keep participant-facing and operator-facing notifications out of escrow mutation flow

## Minimal Request And Response Contracts

The first slice should keep the shared contract set small and screen-oriented.

### Frontend Commands

- `CreateCaseCommand`
- `AddCaseMessageCommand`
- `SubmitApprovalCommand`

The case-facing commands above target case-service because case-service owns the operator-facing boundary.

### Internal Workflow Commands

- `StartWorkflowCommand`
- `ContinueWorkflowCommand`
- `ResumeWorkflowCommand`

### Frontend Views

- `CaseListItem`
- `CaseDetailView`
- `CaseSummaryView`
- `EvidenceView`
- `ActivityEvent`
- `ApprovalStateView`
- `OutcomeView`

### Downstream Read Models

- `EscrowEvidenceSummary`
- `ShipmentEvidenceSummary`
- `RiskReviewSummary`

### Mutation Commands And Results

- `ExecuteSettlementCommand`
- `SettlementOutcome`
- `NotificationDispatchCommand`
- `NotificationDispatchResult`

## Security And Context Requirements For APIs

These APIs should preserve the sample's security story.

- frontend requests carry authenticated operator identity into the case-service boundary
- case-service forwards validated workflow commands into workflow-service
- workflow handling uses operator roles such as `CASE_OPERATOR` and `FINANCE_CONTROL`
- delegated backend calls propagate operator identity, authority, case id, and correlation metadata
- service-local authorization remains deterministic at the mutating boundary, especially in `escrow-service`

Approval and authorization remain separate:

- `SubmitApprovalCommand` records the finance control decision
- `ExecuteSettlementCommand` still passes through deterministic authorization checks inside the settlement-owning service

## First-Slice Non-Goals

The first slice should avoid broadening the API surface unnecessarily.

- no direct frontend calls to `escrow-service`, `shipment-service`, `risk-service`, or `notification-service`
- no event broker as the primary cross-service coordination path
- no WebSocket requirement for the UI transport
- no attempt to expose raw model protocol events as public API contracts
- no search-dedicated service in the first runnable slice