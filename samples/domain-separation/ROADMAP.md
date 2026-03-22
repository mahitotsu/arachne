# Domain Separation Sample Roadmap

This file is a temporary implementation plan for the `domain-separation` sample.

It exists only while the sample is being designed and built. Once the sample reaches a stable runnable state, the remaining durable information should live in `README.md` and this file can be removed.

## Goal

Build one runnable Spring Boot sample that shows how Arachne can support domain-oriented responsibility separation inside a backend application without depending on deferred capabilities such as A2A or orchestration frameworks.

The first version should actively use current-main delegation mechanisms, especially named agents and `agent as tool`, instead of treating them as out of scope.

## Current Decisions

- the first version stays within one Spring Boot application
- the sample domain is account operations for a backend application
- the sample should cover multiple workflow variants under one account-operations shell
- one coordinating runtime delegating to specialist runtimes through tools is in scope
- the coordinator should see a small capability-oriented tool surface rather than workflow-specific tools
- named-agent defaults are the preferred way to express role-specific runtime configuration
- packaged skills are the business-knowledge boundary for the first version
- request-scoped authorization context should be propagated into delegated tool execution
- concrete mutation tools should own transaction boundaries for state-changing operations
- the sample should reuse current-main capabilities rather than forcing new core features first
- the sample should prefer a backend workflow with durable state over a generic chat demo
- distributed multi-agent concerns remain outside the initial implementation scope
- the sample may look workflow-oriented at the application level, but it must not introduce a generic workflow-engine abstraction into Arachne core

## First Scenario

- `operations-coordinator`: top-level runtime that receives the request, activates the relevant business skill, calls stable capability-oriented tools, and owns the approval pause
- `operations-executor`: specialist runtime reached behind those tools to inspect or execute the requested system operation

Working domain shape:

- a request comes in for an account-management operation
- the coordinator activates and uses packaged business-procedure skills relevant to the requested workflow
- the coordinator calls a stable capability-oriented tool to prepare the account operation
- that tool delegates to the operations executor runtime
- the coordinator assembles a structured summary, including approval checks when required by the active workflow
- the workflow pauses at the approval boundary only when the active workflow requires approval
- the resumed or approval-free workflow calls a stable capability-oriented execution tool
- the final response is returned with workflow-appropriate execution results

Why this scenario is first:

- it exercises named-agent defaults in a way that looks natural for backend role separation
- it uses `agent as tool` as a local coordination mechanism without implying deferred distributed orchestration
- it tests whether capability-oriented tools are a stable boundary while business workflows grow through skills
- it composes well with existing approval, session, and skill capabilities
- it keeps the business flow concrete enough to discuss durable state, authorization propagation, and operator review

## Open Questions

These questions are still open, but they should be resolved within the phases below rather than tracked as free-floating design notes.

- which subset of the planned account-operation workflows should ship in the first runnable cut
- which session sample shape is the better base to reuse first
- what the simplest sample representation of access-token-backed authorization should be

## Phase 0: Scope Fix

Purpose:
Lock the initial scenario, role split, and sample boundary before adding code.

Completion Criteria:
The first scenario, role ownership, and sample boundary are explicit enough that implementation can proceed without reopening the core direction.

Checklist:

- [x] choose the account-operations workflow shell as the first scenario
- [x] choose named agents and `agent as tool` as core coordination mechanisms
- [x] choose packaged skills as the business-knowledge boundary
- [x] state that deferred distributed orchestration remains out of scope

## Phase 1: Skill And Role Design

Purpose:
Define the first packaged skill set and the concrete responsibilities of the coordinator and specialist runtimes.

Completion Criteria:
The initial skill set, named-agent roles, and delegation boundaries are concrete enough to implement without inventing sample behavior ad hoc in code, and they are recorded in `SPEC.md`.

Checklist:

- [x] add `SPEC.md` as the design record for phase-level sample decisions
- [x] define the first packaged skill names and their intended usage
- [x] define which business procedures belong in skills versus runtime prompts
- [x] define the exact responsibility of `operations-coordinator`
- [x] define the exact responsibility of `operations-executor`
- [x] decide whether any workflow step remains plain application-service logic

## Phase 2: Module Skeleton

Purpose:
Create the Maven module and minimum Spring Boot application structure for the sample.

Completion Criteria:
The sample exists as a buildable module under `samples/` with a runnable Spring Boot entry point and baseline configuration.

Checklist:

- [ ] add the sample module to `samples/pom.xml`
- [ ] add the sample `pom.xml`
- [ ] add the Spring Boot main application class
- [ ] add baseline `application.yml`
- [ ] add the initial package structure for runner, config, and domain types
- [ ] add the initial security-context holder and propagation package structure

## Phase 3: Local Delegation Flow

Purpose:
Implement the minimum coordinating and specialist runtime flow using named agents and `agent as tool`.

Completion Criteria:
One top-level request can delegate to specialist runtimes locally inside the same application and return a structured intermediate result.

Checklist:

- [ ] implement the coordinator runtime entry point
- [ ] implement the capability-oriented preparation tool path
- [ ] implement the capability-oriented execution tool path
- [ ] implement the operations executor delegation path
- [ ] wire named-agent defaults for each role
- [ ] verify that delegation happens through tools rather than direct inlined specialist logic
- [ ] propagate authorization context into delegated tool execution

## Phase 4: Skill-Driven Procedure Layer

Purpose:
Make business procedure selection and execution depend on packaged skills instead of baking workflow-specific procedures into one prompt.

Completion Criteria:
The sample can activate the relevant packaged skill for the chosen scenario and use that skill content as the business-procedure layer.

Checklist:

- [ ] add the initial packaged skill files under `resources/skills/`
- [ ] define how the coordinator discovers or selects the relevant skill
- [ ] implement skill activation in the main workflow
- [ ] verify that workflow-specific business knowledge lives primarily in skill content

## Phase 5: Approval And Session

Purpose:
Add the approval pause/resume path and the session behavior needed to make the workflow feel like a backend process rather than a one-shot chat.

Completion Criteria:
The sample can pause at an approval boundary, resume with an external decision, and preserve the relevant state through the chosen session mechanism.

Checklist:

- [ ] add the approval pause path
- [ ] add the resume path with external approval input
- [ ] choose and wire the first session persistence shape
- [ ] verify that relevant workflow state survives across the intended boundary
- [ ] enforce authorization checks in concrete executor tools
- [ ] add transaction boundaries for state-changing executor tools

## Phase 6: Documentation And Verification

Purpose:
Make the sample understandable and verify whether it surfaces new implementation candidates.

Completion Criteria:
The README contains runnable instructions and expected output, and the sample has been checked for the most important classification outcomes.

Checklist:

- [ ] add runnable instructions to `README.md`
- [ ] add expected output shape to `README.md`
- [ ] document how this sample relates to the narrower feature samples
- [ ] classify any surfaced gaps as core, Spring-specific, application-level, or deferred
- [ ] remove or shrink roadmap content that is no longer needed once the sample stabilizes