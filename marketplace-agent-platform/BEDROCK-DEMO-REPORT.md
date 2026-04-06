# Marketplace Agent Platform Bedrock Demo Report

## Demo Purpose

This report records a live Bedrock-backed demonstration run of the marketplace agent platform product track.

The purpose of the demo is to show that the current marketplace sample is not only structurally complete on paper, but can also be started and exercised as a composed multi-service Arachne application with:

- a thin operator console
- case-facing HTTP entrypoints
- Bedrock-backed workflow handling in `workflow-service`
- service-local specialist agents behind explicit internal APIs
- operator-visible streaming progress reflected in case activity history
- finance-control approval interrupt and later resume
- deterministic post-resume settlement and notification completion

## Runtime Entry Point Used

The demo used the recommended startup entry point documented in the product-track README.

```bash
cd /home/akring/arachne/marketplace-agent-platform
make up-bedrock
```

Observed behavior during startup:

- the first `make up-bedrock` attempt failed during Docker BuildKit image export with a transient snapshot error
- the second `make up-bedrock` attempt succeeded without code changes
- after the successful retry, the full composed runtime became healthy

The successful runtime exposed these primary endpoints:

- operator console: `http://localhost:3000`
- case-service: `http://localhost:8080`
- workflow load balancer: `http://localhost:8081`

Health verification performed during the demo:

```bash
curl -sf http://localhost:3000 >/dev/null && echo operator-console-up
curl -sf http://localhost:8080/actuator/health
curl -sf http://localhost:8081/actuator/health
```

The composed runtime remained healthy after startup, including:

- `case-service`
- `workflow-service-1`
- `workflow-service-2`
- `workflow-load-balancer`
- `escrow-service`
- `shipment-service`
- `risk-service`
- `notification-service`
- `operator-console`
- `postgres`
- `redis`

## Bedrock Mode Verification

The workflow-service container was inspected after startup and confirmed to be running in Bedrock mode.

Observed container environment values:

- `MARKETPLACE_WORKFLOW_ARACHNE_MODEL_MODE=bedrock`
- `ARACHNE_STRANDS_MODEL_PROVIDER=bedrock`

For this run, `ARACHNE_STRANDS_MODEL_ID` and `ARACHNE_STRANDS_MODEL_REGION` were not explicitly set on the container environment, so the runtime relied on the documented default fallback behavior from the marketplace README.

## Public API Flow Exercised

The live demo exercised the public case-facing API in `case-service`.

Relevant public endpoints:

- `POST /api/cases`
- `GET /api/cases/{caseId}`
- `POST /api/cases/{caseId}/approvals`
- `GET /api/cases/{caseId}/activity-stream`

These endpoints are implemented in:

- `case-service/src/main/java/com/mahitotsu/arachne/samples/marketplace/caseservice/CaseController.java`

### Case 1: Interrupt And Approved Completion Path Using `APPROVE`

The first case was created with:

```bash
curl -sS -X POST http://localhost:8080/api/cases \
  -H 'Content-Type: application/json' \
  -d '{
    "caseType":"ITEM_NOT_RECEIVED",
    "orderId":"ord-demo-001",
    "amount":199.99,
    "currency":"USD",
    "initialMessage":"Buyer reports the item never arrived. Seller claims it was shipped.",
    "operatorId":"operator-1",
    "operatorRole":"CASE_OPERATOR"
  }'
```

Observed result:

- `caseStatus` returned as `AWAITING_APPROVAL`
- `currentRecommendation` returned as `CONTINUED_HOLD`
- evidence was returned from `shipment-agent`, `escrow-agent`, and `risk-agent`
- activity history already contained streaming and hook/runtime evidence

Visible activity evidence from the returned case detail included:

- `DELEGATION_STARTED`
- `EVIDENCE_RECEIVED`
- `STREAM_PROGRESS`
- `HOOK_FORCED_TOOL_SELECTION`
- `TOOL_CALL_RECORDED`
- `APPROVAL_INTERRUPT_REGISTERED`
- `POLICY_CONSISTENCY_APPLIED`
- `RECOMMENDATION_UPDATED`
- `APPROVAL_REQUESTED`

The returned activity history also made the following concrete tool/runtime behavior visible through the public API:

- `resource_list`
- `resource_reader`
- forced `finance_control_approval`
- policy reconciliation from `REFUND` to `CONTINUED_HOLD`

The approval was then submitted with `decision="APPROVE"`.

```bash
curl -sS -X POST \
  http://localhost:8080/api/cases/e7945611-9712-4ec6-b002-585887373559/approvals \
  -H 'Content-Type: application/json' \
  -d '{
    "decision":"APPROVE",
    "comment":"Finance control approved continued hold after evidence review.",
    "actorId":"finance-1",
    "actorRole":"FINANCE_CONTROL"
  }'
```

Observed result:

- `approvalStatus=APPROVED`
- `workflowStatus=COMPLETED`
- `resumeAccepted=true`

The final case detail for that run showed:

- `caseStatus=COMPLETED`
- `currentRecommendation=CONTINUED_HOLD`
- `approvalState.approvalStatus=APPROVED`
- `outcome.outcomeType=CONTINUED_HOLD_RECORDED`
- `outcome.outcomeStatus=SUCCEEDED`

The final activity history included:

- `SETTLEMENT_COMPLETED` from `escrow-service`
- `NOTIFICATION_DISPATCHED` from `notification-service`

## Case 2: Interrupt And Approved Completion Path Using `APPROVED`

The second case was created with:

```bash
curl -sS -X POST http://localhost:8080/api/cases \
  -H 'Content-Type: application/json' \
  -d '{
    "caseType":"ITEM_NOT_RECEIVED",
    "orderId":"ord-demo-002",
    "amount":249.50,
    "currency":"USD",
    "initialMessage":"Buyer reports package missing and requests refund.",
    "operatorId":"operator-2",
    "operatorRole":"CASE_OPERATOR"
  }'
```

Observed pre-approval result:

- `caseStatus=AWAITING_APPROVAL`
- `currentRecommendation=CONTINUED_HOLD`
- activity history again showed streaming progress, resource reads, approval interrupt registration, and policy reconciliation

The positive approval path was then exercised with `decision="APPROVED"` on the rebuilt live runtime.

```bash
curl -sS -X POST \
  http://localhost:8080/api/cases/2b9f89a7-6317-4d45-b93f-f2dcad2903e7/approvals \
  -H 'Content-Type: application/json' \
  -d '{
    "decision":"APPROVED",
    "comment":"Finance control approved the continued hold after review.",
    "actorId":"finance-live-2",
    "actorRole":"FINANCE_CONTROL"
  }'
```

Observed resume result:

- `approvalStatus=APPROVED`
- `workflowStatus=COMPLETED`
- `resumeAccepted=true`

The final case detail returned from `GET /api/cases/2b9f89a7-6317-4d45-b93f-f2dcad2903e7` showed:

- `caseStatus=COMPLETED`
- `currentRecommendation=CONTINUED_HOLD`
- `approvalState.approvalStatus=APPROVED`
- `outcome.outcomeType=CONTINUED_HOLD_RECORDED`
- `outcome.outcomeStatus=SUCCEEDED`
- a concrete settlement reference

The final activity history included:

- `SETTLEMENT_COMPLETED` from `escrow-service`
- `NOTIFICATION_DISPATCHED` from `notification-service`

That confirms the product-track README claim that the finance-control pause/resume uses the Arachne-native interrupt boundary while post-resume completion remains deterministic and Spring-owned.

## What This Demo Proves

This live run demonstrated all of the following on the current branch.

- the recommended Bedrock startup path is usable in practice
- the full composed marketplace runtime can be brought to a healthy state locally
- the case-facing API can create a representative `ITEM_NOT_RECEIVED` case end to end
- the workflow reaches an approval interrupt and exposes that pause in operator-facing case data
- operator-visible activity history reflects streaming progress and concrete tool/runtime events
- packaged resource reads and approval-tool forcing are visible through the case projection
- the workflow recommendation can be reconciled against policy before settlement
- approval submission resumes the workflow through the existing case-facing boundary
- both `APPROVE` and `APPROVED` are accepted on the approved resume path
- the approved path completes deterministically and records settlement plus notification activity

## Current Approval Contract

The current live approval contract for positive decisions accepts both of these values:

- `decision="APPROVE"` is treated as approval
- `decision="APPROVED"` is treated as approval

For the current documented contract shape, callers should use these values:

- `APPROVE` or `APPROVED` for approval
- `REJECT` for rejection

Relevant implementation points:

- `workflow-service/src/main/java/com/mahitotsu/arachne/samples/marketplace/workflowservice/ArachneWorkflowRuntimeAdapter.java`
- `workflow-service/src/main/java/com/mahitotsu/arachne/samples/marketplace/workflowservice/MarketplaceWorkflowArachneModel.java`

These values were verified in both focused tests and the live rebuilt runtime.

## Audit Status

Status: aligned.

The marketplace product track now has both:

- implementation and runtime evidence for the Bedrock-backed composed demo path
- a checked-in report that records how the live verification was performed and what was observed

The checked-in report now reflects the currently verified approval behavior of the running product track.