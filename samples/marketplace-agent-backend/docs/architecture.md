# Marketplace Agent Backend Architecture

This note separates architecture decisions from the sample concept.

Use [concept.md](/home/akring/arachne/samples/marketplace-agent-backend/docs/concept.md) to describe what the sample is meant to demonstrate.
Use this file to describe how the sample should be structured and run.

The architecture is intentionally split into two views:

1. execution architecture
2. development architecture

That separation matters because the sample is trying to demonstrate a realistic distributed shape without committing too early to an unnecessarily heavy local development setup.

## Architectural Intent

The sample should demonstrate a marketplace backend where service boundaries and agent boundaries reinforce each other.

Architecture should make these points clear:

- each backend service owns its own local state and its own service-local agent
- cross-service collaboration happens through explicit backend contracts, not through one shared global prompt
- operator interaction happens through a thin frontend and a coordinator boundary
- correctness-sensitive state transitions remain deterministic inside backend services

## Execution Architecture

Execution architecture describes the runtime topology of the sample when it is running.

### Runtime Topology

The target runtime topology is:

- `operator-console`
- `case-coordinator-service`
- `escrow-service`
- `shipment-service`
- `risk-service`
- `notification-service`

Each backend service is expected to be independently runnable.

The first implementation slice may still simplify some infrastructure, but it should preserve these logical runtime boundaries.

### Frontend Role

`operator-console` is not the subject of the sample. It is a visibility and control surface.

Its responsibilities are limited to:

- case list display
- agentic case search
- case detail display
- operator chat entry
- approval submission
- live activity updates derived from streaming events

It should not own business workflow logic.

### Backend Roles

`case-coordinator-service`

- workflow entry point
- chat-oriented orchestration boundary
- Arachne session ownership for case workflows
- skill activation and policy/runbook consultation
- downstream service delegation
- approval pause and resume control

`escrow-service`

- escrow and settlement state
- refund, hold, and release execution
- deterministic settlement audit records

`shipment-service`

- shipment milestones
- delivery evidence interpretation
- shipping-exception summaries

`risk-service`

- fraud and compliance indicators
- threshold checks
- manual review triggers

`notification-service`

- operator-facing and participant-facing notification dispatch
- post-decision delivery status

### Agent Placement

Each backend service owns one named Arachne agent.

Recommended mapping:

- `case-coordinator-service` -> `case-coordinator`
- `escrow-service` -> `escrow-agent`
- `shipment-service` -> `shipment-agent`
- `risk-service` -> `risk-agent`
- `notification-service` -> `notification-agent`

This mapping should stay explicit. The sample should not blur service boundaries by hiding several service concerns behind one agent.

### Communication Shape

The current architectural direction is:

- frontend to coordinator: HTTP or WebSocket depending on the need for live updates
- coordinator to backend services: synchronous service-to-service API calls in the first slice
- streaming to frontend: coordinator-originated live event feed
- approval submission to coordinator: explicit API call that resumes the paused workflow

The first slice should prefer the simplest communication model that still makes the distributed shape visible.

### Session Boundary

`case-coordinator-service` should own the case session lifecycle.

This means:

- chat turns attach to a case session id
- approval pause and resume are case-session aware
- streamed activity entries are associated with the current case

Other services may remain stateless from the Arachne session perspective even if they maintain their own business state.

### Streaming Boundary

Streaming should be exposed as coordinator-visible activity updates.

The sample should not expose raw low-level model protocol events directly to the user when a clearer activity entry can be produced.

Expected transformation path:

- Arachne stream event
- coordinator-visible activity event
- frontend-readable case activity item

### Approval Boundary

Approval should be visible as a first-class workflow state.

The architecture should preserve these rules:

- approval interrupts before deterministic settlement action
- the paused state is queryable from the case detail view
- resume happens through an explicit backend endpoint that re-enters the existing Arachne resume path

## Development Architecture

Development architecture describes how the sample is laid out in source control and how contributors build and run it locally.

### Repository Layout Direction

The sample should become a multi-module project under its sample directory.

Recommended shape:

- parent aggregator module
- `shared-contracts`
- `shared-policy-resources`
- `operator-console`
- `case-coordinator-service`
- `escrow-service`
- `shipment-service`
- `risk-service`
- `notification-service`
- optional `integration-tests`

This shape keeps shared types explicit while avoiding one giant backend module.

### Shared Modules

`shared-contracts`

- case records
- evidence summary types
- approval command and response types
- settlement outcome types

`shared-policy-resources`

- allowlisted runbooks
- settlement policy documents
- approval threshold references

This separation helps avoid mixing domain contracts with packaged content.

### Frontend Development Shape

The frontend should stay lightweight.

Possible implementation choices remain open, but architecture should constrain the outcome:

- minimal dependency footprint
- easy local startup
- clear API boundary to the coordinator
- no business logic duplication from the backend

The frontend technology choice should be deferred until after the backend module and API boundaries are clearer.

### Local Run Strategy

The likely local run target is Docker Compose, but only after the module structure is stable.

Expected role of Compose:

- start all sample services consistently
- connect frontend and backend endpoints
- optionally provide shared infrastructure such as Redis

Before Compose is introduced, individual services should still be runnable for focused development.

### Testing Strategy Direction

Testing should follow the architecture split.

Recommended layers:

- service-local tests inside each module
- coordinator workflow tests at the orchestration boundary
- optional end-to-end sample tests across the composed runtime

The sample should avoid requiring full multi-service startup for every small backend test.

## Deferred Decisions

These decisions should be recorded here but not forced too early:

- whether streaming to the frontend uses SSE or WebSocket
- whether Redis is required in the first runnable slice or later
- whether `notification-service` starts as a full separate process or an initially simplified service module
- whether agentic case search uses a dedicated search-oriented service or remains coordinator-led in the first slice
- which frontend framework, if any, gives the best thin-UI outcome

## Current Recommendation

Use this sequence:

1. keep `concept.md` as the product and UX definition
2. use this architecture note to separate execution and development concerns
3. decide runtime boundaries before choosing local infrastructure
4. decide module boundaries before choosing frontend tooling