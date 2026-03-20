# Phase 2 Tools Sample

This sample shows the intended Phase 2-style Spring idiom on the current main branch:

- named-agent defaults are declared in `application.yml`
- a `@Service` method annotated with `@StrandsTool` is auto-discovered as a tool
- the tool is scoped to the coordinator agent with a named-agent qualifier policy
- that service can delegate to another `Agent`, which makes the service an agent-as-tool adapter
- the top-level agent can still request typed structured output through `agent.run("...", MyType.class)`
- Jakarta Bean Validation annotations on tool parameters and the structured output type are enforced at runtime

The example keeps the pieces intentionally small so you can see the wiring directly.

## Prerequisites

- Java 21
- Maven
- valid AWS credentials for Bedrock
- access to the configured model in the configured region

The sample depends on the local snapshot of this repository, so install the root project first:

```bash
mvn install
```

## Run The Demo

```bash
cd samples/phase2-tools
mvn spring-boot:run
```

The application does one top-level request that asks the coordinator agent to:

1. call a tool backed by another agent
2. collect the answer into a typed Java record

Expected shape of the output:

```text
Arachne Phase 2 agent-as-tool sample
request> ...
summary.city> Tokyo
summary.forecast> ...
summary.advice> ...
```

## What To Look For

The sample is centered on three beans:

- `tripPlannerAgent`: the top-level agent built from `AgentFactory`
- `cityForecastTool`: a Spring `@Service` whose `@StrandsTool` method is auto-discovered
- `weatherResearchAgent`: a second agent used internally by that tool service

Both `Agent` beans are built with `factory.builder("...")`, while Spring `@Qualifier` keeps injection explicit on the Java side. The coordinator binds only tools tagged with `trip-planner`, while the specialist agent opts out of discovered tools entirely. That keeps the tool surface agent-scoped instead of application-global.

That means the tool method is not doing the real language-model work itself. It delegates to another agent and exposes the result through a narrow Spring service API.

This is the Phase 2 agent-as-tool pattern in Spring form.

The sample also shows the current validation model for Phase 2: `@NotBlank` on the tool input and the `TripPlan` record is checked at runtime, but those constraints are not projected into the generated JSON schema.

The sample intentionally stays focused on tool wiring and typed output. For persisted session restore, use the Phase 3 session samples.

## Configuration

The sample ships with this default configuration in `src/main/resources/application.yml`:

```yaml
arachne:
  strands:
    model:
      provider: bedrock
      id: jp.amazon.nova-2-lite-v1:0
      region: ap-northeast-1
    agents:
      trip-planner:
        tool-qualifiers: [trip-planner]
      weather-research:
        use-discovered-tools: false
```

Override any of those values with standard Spring Boot configuration mechanisms if needed. The sample's Java configuration stays small because the per-agent defaults now live under `arachne.strands.agents.<name>.*`.