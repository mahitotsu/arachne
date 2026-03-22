# Domain Separation Sample

This sample is intended to show how Arachne can be used in a Spring Boot backend that is starting to outgrow a single agent with a single prompt.

It is the reference sample for evaluating two design questions together:

- when a Python-side Strands design should be carried forward as a provider-neutral core pattern
- when Arachne should evolve in a Spring-specific direction because that is the natural fit for backend applications

This README focuses on the sample's purpose and eventual usage. Temporary implementation planning is tracked separately in `ROADMAP.md` while the sample is under construction.

## Status

This sample is not runnable yet. The runnable Spring Boot application has not been added yet.

Until implementation starts, this README should be treated as the user-facing overview for the intended sample, and `ROADMAP.md` should be treated as the temporary build plan.

The first implementation target is an approval-oriented backend workflow.

The business procedure side of that workflow is intended to be carried by packaged skills rather than by hard-coded prompt logic.

## Purpose

The sample is meant to evaluate whether Arachne can express the benefits of domain-oriented agent design in a Spring backend without immediately requiring deferred capabilities such as A2A or multi-agent orchestration.

In this context, avoiding orchestration means avoiding new distributed coordination mechanisms, not avoiding the current-main delegation patterns that Arachne already supports.

More concretely, the sample is intended to make these questions testable:

- can one application keep agent responsibilities separated without collapsing back into one large prompt
- can system knowledge and business knowledge be kept distinct enough to support different editing responsibilities
- which parts of that structure belong in Arachne core contracts versus Spring integration patterns versus application code

## Background

This direction is based on an earlier Python-side experiment that explored two hypotheses:

- domain boundaries are a useful way to split agent responsibilities when the system grows
- business procedures should not be forced into the system prompt owned by the application team

That experiment validated the architectural idea, but Arachne still needs a Java and Spring-specific reference implementation that shows what the same thinking looks like within the current repository boundary.

## Intended Usage

Once implemented, this sample should be the place to look when you want a higher-level backend reference rather than a single-feature demo.

It is intended for readers who want to see:

- multiple role-specific agent runtimes inside one Spring Boot application
- clear boundaries between coordinating logic and specialist logic
- separation between runtime wiring and business procedure content
- a realistic backend-oriented combination of tools, session state, approval points, and typed outputs

If you only need one isolated capability, the existing samples remain the smaller starting point.

The initial scenario is intended to look like an operations backend rather than a chat demo: one coordinating runtime analyzes an operation request, consults specialist runtimes through tools, and pauses for explicit approval before the final action is accepted.

The intended growth model is skill-driven: when a new business workflow is needed, the preferred path should be to add or revise packaged skill content that carries business knowledge, then redeploy the application, rather than expanding one central prompt indefinitely.

## What This Sample Should Demonstrate

The first runnable version should demonstrate a Spring Boot backend that uses multiple role-specific agent runtimes with explicit responsibility boundaries.

The intended areas of focus are:

- role-specific runtimes built from `AgentFactory` at the point of use rather than shared as singleton agent beans
- named-agent defaults used to express role-specific configuration cleanly inside one application
- `agent as tool` delegation used behind a small capability-oriented tool surface between the top-level runtime and specialist runtimes
- packaged skills used as the main carrier of business procedure knowledge so the deployed application can grow into additional workflows without rewriting the runtime wiring
- deliberate separation between system-controlled instructions and business-controlled procedural knowledge
- typed boundaries between agent interactions and application code through tools, records, and validation
- realistic Spring integration concerns such as services, persistence, session state, approval points, and lifecycle observation

The initial implementation does not need to prove distribution across multiple deployable services. It only needs to prove that the structure remains coherent when the application is written the way a Spring backend would naturally be written.

The intended coordination style is local and explicit: one coordinating runtime may call a small set of capability-oriented tools, and those tools may delegate to specialist runtimes. The sample should stop short of introducing a new orchestration layer or remote protocol boundary.

## Initial Scenario

The first runnable version is planned as an approval-oriented operation workflow with two explicit runtimes inside one Spring Boot application.

- an operations coordinator agent that owns the top-level workflow
- an operations executor agent that owns system-operation behavior

The coordinator should use a stable capability-oriented tool surface such as preparation and execution tools rather than embedding workflow-specific tools into the runtime. Those tools should delegate to the operations executor agent. The workflow should then pause on an approval boundary before the final execution step.

Business procedure knowledge should live primarily in packaged skills. The coordinating runtime should stay focused on system behavior such as delegation, approval boundaries, skill activation, and typed result handling.

This gives the sample one concrete domain while still keeping the design questions general:

- whether role separation stays readable in code
- whether named agents are enough to express those roles cleanly
- whether local delegation is sufficient before considering deferred distributed patterns
- whether skill-packaged business procedures are enough to expand supported workflows without turning the runtime layer into a workflow-specific prompt bundle
- whether capability-oriented tools are a better boundary than workflow-specific tools when skills are expected to grow

## How To Use This Sample

The runnable instructions will be added once the application exists.

The intended usage pattern is:

1. build and run the Spring Boot sample application
2. trigger one backend-oriented workflow scenario
3. inspect how coordinating and specialist responsibilities are separated
4. compare the design to the narrower feature samples under `samples/`

## What This Sample Should Not Demonstrate

This sample is not intended to pull deferred features into scope.

The initial version should not require:

- A2A protocol support
- MCP support
- multi-agent Swarm or Graph orchestration
- remote skill registries
- bidirectional realtime streaming

If later work shows that one of those capabilities is necessary, that should start with an ADR or ADR update rather than by growing this sample implicitly past the current product boundary.

Using named agents and `agent as tool` does not violate that boundary. They are part of the current shipped surface and are expected to be central to this sample.

Likewise, packaging business procedures as skills is in scope because skills are already part of the current shipped surface. The sample should use them as an application-level workflow building block without implying that Arachne itself has become a general-purpose workflow engine.

## Evaluation Criteria

The sample should be considered successful only if it gives clear evidence for all of the following.

1. Responsibility boundaries stay visible in code.
2. Business procedure updates can be discussed separately from runtime wiring changes.
3. The implementation uses current-main Arachne capabilities in a way that feels natural for Spring developers.
4. The sample reveals concrete gaps that can be classified as one of the following:
   - Python-compatible core capability
   - Spring-specific integration improvement
   - application-level responsibility
   - deliberately deferred feature

## Relationship To Existing Samples

This sample is expected to compose ideas that are currently shown separately elsewhere:

- agent construction and named defaults from `tool-delegation`
- local agent-to-agent delegation through the existing tool pattern from `tool-delegation`
- state restore from the session samples
- interrupt and resume from `approval-workflow`
- externalized procedural knowledge from `skill-activation`

Unlike those samples, this one is intended to show a higher-level backend design style rather than a single isolated feature boundary.

It should be read as an application-level workflow shell built on top of Arachne's current features, not as a claim that Arachne core now owns generic workflow-engine responsibilities.