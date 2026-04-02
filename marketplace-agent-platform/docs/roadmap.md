# Marketplace Agent Platform Roadmap

This file is the single progress-management surface for the root-level `marketplace-agent-platform/` product track.

Use this file for only the remaining work:

- the current boundary relevant to the next unfinished phase
- the target definition for the next major milestone
- the ordered implementation roadmap from the current position forward
- the remaining task list for the active and upcoming phases

Do not track the same implementation progress or residual task information in `README.md` or in separate `current-plan`, `current-tasks`, or `slices` documents.

## How To Read This File

Read this file in this order:

1. `Current Active Queue` to see the concrete work order
2. the first unchecked checkbox to identify the current position
3. the active phase section to see what must become true before that phase is done
4. later phase sections only after the active phase is complete

Working rule:

- do not work ahead on a later phase while an earlier phase still has remaining tasks
- the current position is the boundary between checked and unchecked items in `Current Active Queue`
- when a checkbox in the active phase becomes true and is verified, flip it to `[x]`
- when all checkboxes in a phase are `[x]`, the next unchecked phase becomes active

## Task Management Rule

This file uses a deletion-based task workflow.

- keep only active and upcoming unfinished work
- when a task is done and verified, delete it from the remaining task list
- when a phase is done and verified, delete its phase section instead of preserving a history entry here
- when the final phase is done and verified, delete this file
- if completion changes the shipped baseline, update `Current Baseline` in the same turn

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

The current shipped baseline is a runnable product track with Phase 3 complete.

Implemented today:

- end-to-end local marketplace flow with deterministic service ownership preserved
- opt-in Arachne runtime identity, packaged skills, and built-in resource-tool usage for the representative `ITEM_NOT_RECEIVED` path inside `workflow-service`
- native Arachne interrupt and resume now back the finance-control approval boundary on the opt-in workflow path, including Redis-backed continuity across workflow-service replicas
- operator-visible activity now includes native workflow streaming progress from packaged guidance lookup and settlement-policy review on the opt-in path
- narrow tool-boundary steering now blocks the automatic settlement shortcut and redirects the workflow to finance-control approval while keeping the frontend thin
- deterministic fallback still as the default path, with focused enabled-path tests and existing continuity coverage still green

Not implemented yet in the product-track workflow itself:

- execution-context propagation
- Bedrock-backed product-track runtime wiring

## Current Active Queue

These are the concrete tasks to execute in order.

The current position is the first unchecked item below.

- [ ] complete Phase 4 `Capability-Complete Closeout`

## Roadmap

The roadmap below defines what each unchecked phase-completion item above means.

### Phase 4: Capability-Complete Closeout

Goal:

Close the gap between the marketplace product track and the Arachne capability-complete sample definition.

Definition of done:

- [ ] all capability requirements from `requirements.md` are demonstrated in the marketplace workflow itself
- [ ] README, docs, tests, and sample positioning all describe the sample consistently
- [ ] the product track is no longer described as deterministic-first or largely deferred for Arachne-native runtime behavior

Remaining implementation tasks for this phase:

- align `README.md`, `requirements.md`, `architecture.md`, and any other sample-facing docs with the completed capability surface
- re-evaluate whether the sample still needs deterministic fallback wording once the Arachne-native path is shipped
- add any remaining closeout tests and documentation needed to treat the marketplace track as an Arachne capability-complete sample

## Re-entry Point

When work resumes on the next theme, start in this order:

1. this roadmap for current boundary, target, and remaining tasks
2. `README.md` for the public positioning of the product track
3. `workflow-service/src/main/java/.../WorkflowApplicationService.java` and `workflow-service/src/main/java/.../ArachneWorkflowRuntimeAdapter.java` for the current orchestration seam
4. `workflow-service/src/main/java/.../WorkflowServiceConfiguration.java` and the packaged skills/resources under `workflow-service/src/main/resources/` for the current native approval runtime boundary
5. `workflow-service/src/test/java/.../WorkflowReplicaRedisContinuityIntegrationTest.java`, `workflow-service/src/test/java/.../WorkflowServiceApiTest.java`, and `workflow-service/src/test/java/.../WorkflowServiceArachneApiTest.java` for the current baseline, native approval continuity, and opt-in runtime evidence

Re-run these commands first when resuming implementation:

```bash
cd /home/akring/arachne/marketplace-agent-platform && make test
cd /home/akring/arachne/marketplace-agent-platform && make ui-build
```