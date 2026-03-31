# Marketplace Agent Platform Roadmap

This file is the single progress-management surface for the root-level `marketplace-agent-platform/` product track.

Use this file for all of the following:

- the current implemented boundary
- the target definition for the next major milestone
- the ordered implementation roadmap to reach that target
- the remaining task list for the active and upcoming phases

Do not track the same implementation progress or residual task information in `README.md` or in separate `current-plan`, `current-tasks`, or `slices` documents.

## Task Management Rule

This file uses a deletion-based task workflow.

- keep only tasks that are not finished yet
- when a task is done and verified, delete it from the remaining task list
- if the completion changes the shipped baseline, update `Current Baseline` in the same turn
- if a roadmap phase is fully complete, delete its remaining-task subsection and update the next phase accordingly

That keeps the file short and prevents stale done-items from being mistaken for live scope.

## Target

The target is an Arachne capability-complete sample for the marketplace domain, not only a deterministic service scaffold.

Capability-complete means the sample visibly demonstrates all of the following in the product-track workflow itself:

- named Arachne agents mapped to service-local responsibilities
- agent delegation across explicit service boundaries
- structured output populating stable case and evidence views
- packaged skills and built-in resource tools used for real workflow decisions
- Arachne sessions as part of long-running workflow continuity
- native interrupt and resume at the approval boundary
- operator-visible streaming progress
- steering that blocks or redirects unsafe workflow paths
- execution context propagation across the workflow path

## Current Baseline

The current shipped product-track baseline is a closed deterministic first slice.

Implemented today:

- root-level independent multi-module product-track boundary
- thin `operator-console` with `Case List` and `Case Detail`
- same-origin browser-facing console flow through `http://localhost:3000`
- `case-service` case creation, list, detail, follow-up message, approval submission, and SSE activity updates
- `workflow-service` start, continue, and resume orchestration over explicit HTTP service boundaries
- deterministic downstream services for escrow, shipment, risk, and notification
- PostgreSQL-backed business persistence for service-owned data in the local runtime
- Redis-backed workflow session continuity in the composed runtime
- explicit cross-replica workflow continuity evidence in integration tests
- deterministic `REFUND` and `CONTINUED_HOLD` recommendation paths for `ITEM_NOT_RECEIVED`
- approval-complete paths for both representative outcomes
- approval-reject return to evidence gathering without settlement
- local runtime operations through `make up`, `make down`, `make reset`, `make ps`, `make logs`, `make test`, and operator-console helpers

Not implemented yet in the product-track workflow itself:

- named Arachne agents driving runtime behavior
- Bedrock-backed runtime behavior for this product track
- packaged skills and built-in resource-tool usage inside the marketplace workflow path
- native Arachne interrupt and resume instead of deterministic HTTP/session simulation
- visible steering and execution-context propagation inside operator-visible behavior

## Roadmap

The roadmap starts from the deterministic baseline above and moves toward the capability-complete target in phases that leave the sample runnable after each step.

### Phase 1: Runtime Identity And Skill Wiring

Goal:

Make the marketplace workflow visibly Arachne-native at the service-local runtime layer without collapsing service boundaries.

Exit criteria:

- workflow behavior is driven by named Arachne agents rather than only deterministic orchestration code
- packaged skills are present and necessary in the workflow path
- built-in resource access is used for policy or runbook lookup in a visible way
- deterministic Spring application services still own correctness-sensitive state transitions

### Phase 2: Native Approval Pause And Resume

Goal:

Replace the current deterministic approval simulation with native Arachne interrupt and resume behavior.

Exit criteria:

- approval pause comes from an Arachne interrupt boundary
- approval resume continues the existing workflow through Arachne-native resume handling
- the operator-visible case state still shows the same approval lifecycle clearly
- session continuity still works across the composed runtime

### Phase 3: Operator-Visible Streaming And Steering

Goal:

Make operator-visible activity updates reflect streaming and add narrow steering for unsafe workflow paths.

Exit criteria:

- streaming is visible in the case activity surface as incremental progress rather than only final deterministic events
- steering visibly blocks or redirects at least one unsafe settlement shortcut
- steering remains narrow and readable at model or tool boundaries
- the frontend stays thin and does not absorb workflow logic

### Phase 4: Capability-Complete Closeout

Goal:

Close the gap between the marketplace product track and the Arachne capability-complete sample definition.

Exit criteria:

- all capability requirements from `requirements.md` are demonstrated in the marketplace workflow itself
- README, docs, tests, and sample positioning all describe the sample consistently
- the product track is no longer described as deterministic-first or largely deferred for Arachne-native runtime behavior

## Remaining Tasks

Only unfinished tasks belong in this section.

### Next Up

- define the explicit named-agent map for `case-service`, `workflow-service`, and the downstream services without blurring service ownership
- decide the minimum packaged-skill set and resource-tool usage needed to make policy and runbook lookup non-decorative in the representative `ITEM_NOT_RECEIVED` flow
- decide how the current `workflow-service` orchestration should invoke Arachne while preserving the existing deterministic settlement and projection boundaries
- decide whether this next phase must start with deterministic in-process models, Bedrock-backed models, or a staged path that proves both without rewriting the service shape
- record the next implementation theme in docs and tests before code expansion begins

### Phase 1 Remaining

- wire named Arachne agents into the marketplace workflow path and keep the service-local responsibility split explicit
- add packaged skills and built-in resource-tool usage where the workflow consults policy, runbook, or evidence interpretation material
- update tests so the default deterministic baseline remains provable when the Arachne-native path is not yet active or is explicitly disabled
- document the runtime identity, skill boundaries, and fallback behavior once the phase lands

### Phase 2 Remaining

- introduce native interrupt and resume handling at the finance-control approval boundary
- preserve the current approval-complete and approval-reject outcomes while moving the pause/resume semantics into Arachne-native runtime behavior
- prove session continuity still works across replica changes after native resume wiring is introduced

### Phase 3 Remaining

- stream operator-visible activity updates from the Arachne-native workflow path rather than only deterministic state transitions
- add narrow steering for at least one unsafe settlement path and expose the redirected behavior in the operator-visible activity timeline
- verify the thin frontend still talks only to `case-service` while surfacing the richer runtime behavior

### Phase 4 Remaining

- align `README.md`, `requirements.md`, `architecture.md`, and any other sample-facing docs with the completed capability surface
- re-evaluate whether the sample still needs deterministic fallback wording once the Arachne-native path is shipped
- add any remaining closeout tests and documentation needed to treat the marketplace track as an Arachne capability-complete sample

## Re-entry Point

When work resumes on the next theme, start in this order:

1. this roadmap for current boundary, target, and remaining tasks
2. `README.md` for the public positioning of the product track
3. `workflow-service/src/main/java/.../WorkflowApplicationService.java` for the current orchestration seam
4. `case-service/src/main/java/.../CaseApplicationService.java` for the case-facing boundary
5. `workflow-service/src/test/java/.../WorkflowReplicaRedisContinuityIntegrationTest.java`, `workflow-service/src/test/java/.../WorkflowServiceApiTest.java`, and `case-service/src/test/java/.../CaseServiceApiTest.java` for the baseline behavior evidence

Re-run these commands first when resuming implementation:

```bash
cd /home/akring/arachne/marketplace-agent-platform && make test
cd /home/akring/arachne/marketplace-agent-platform && make ui-build
```