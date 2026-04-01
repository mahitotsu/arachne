# Tool Delegation Sample

This sample shows the current Spring Boot best practice for agent-scoped tool wiring and agent-as-tool delegation.

It exists to make these patterns concrete in one runnable example:

- named-agent defaults live in `application.yml`
- a `@Service` method annotated with `@StrandsTool` is auto-discovered as a tool
- the discovered tool is scoped to the coordinator agent through qualifiers
- the tool method builds a specialist `Agent` runtime on demand instead of doing model work directly
- the top-level agent returns typed structured output through `agent.run("...", TripPlan.class)`
- Jakarta Bean Validation is enforced on both tool input and structured output

## Prerequisites

- Java 25
- Maven
- valid AWS credentials for Bedrock
- access to the configured model in the configured region

The sample depends on the local `com.mahitotsu.arachne:arachne` snapshot, so install the library module first. The sample itself now uses the `com.mahitotsu.arachne.samples` namespace until the core library coordinates are migrated in a later step:

```bash
mvn -pl arachne -am install
```

## Run The Demo

```bash
cd samples/tool-delegation
mvn spring-boot:run
```

The application does one top-level request that asks the coordinator agent to:

1. call a tool backed by another agent
2. collect the answer into a typed Java record

Expected shape of the output:

```text
Arachne tool delegation sample
request> ...
summary.city> Tokyo
summary.forecast> ...
summary.advice> ...
```

## What To Look For

The sample is centered on four Spring-managed pieces:

- `ToolDelegationRunner`: builds the coordinator agent from `AgentFactory`
- `CityForecastTool`: a Spring `@Service` whose `@StrandsTool` method delegates to another agent
- `TripPlan`: typed structured output with runtime validation
- the named `weather-research` agent defaults in `application.yml`

Both agent runtimes are built with `factory.builder("...")` at the point of use rather than being published as shared Spring beans. The coordinator binds only tools tagged with `trip-planner`, while the specialist agent opts out of discovered tools entirely. That keeps the tool surface agent-scoped instead of application-global.

This sample intentionally reflects current-main usage rather than preserving an older phase snapshot. For persisted session restore, use one of the session samples.

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

Override any of those values with standard Spring Boot configuration mechanisms if needed. The sample's Java wiring stays small because the per-agent defaults live under `arachne.strands.agents.<name>.*`.

## Run The Opt-In Bedrock Smoke Test

```bash
cd samples/tool-delegation
mvn -Dtest=ToolDelegationBedrockIntegrationTest -Darachne.integration.bedrock=true test
```

This test disables the sample runner, builds the coordinator agent through `AgentFactory`, and proves that live tool delegation still returns validated `TripPlan` structured output.