# Built-In Tools Sample

This sample is a runnable, Bedrock-free demo for the built-in tool baseline that now ships on the current main branch.

It demonstrates these current-main concepts together:

- default built-in inheritance for read-only framework-provided tools including `calculator`
- explicit named-agent selection by built-in tool name
- named-agent filtering through built-in groups
- named-agent opt-out from the default built-in baseline
- allowlisted classpath resource access through `resource_reader` and `resource_list`
- the separation between built-in tools and annotation-discovered application tools

The sample uses a deterministic in-process `Model` bean, so you can verify the built-in behavior without AWS credentials.

## Prerequisites

- Java 25
- Maven

The sample depends on the local `io.arachne:arachne` snapshot, so install the library module first:

```bash
mvn -pl arachne -am install
```

## Run The Demo

```bash
cd samples/built-in-tools
mvn spring-boot:run
```

Expected output shape:

```text
Arachne built-in tools sample
default.tools> calculator, current_time, resource_reader, resource_list
math.tools> calculator
reader.tools> resource_reader, resource_list
strict.tools> (none)
default.reply> At 2026-03-25T... in Asia/Tokyo I read the sample note: Built-in tools sample note.
default.toolResults> [current_time, resource]
math.reply> Calculator agent computed 1 + 2 * (3 + 4) = 15
math.toolResults> [calculator]
reader.reply> Reader agent found [classpath:/builtin/release-note.md] and read: Built-in tools sample note.
reader.toolResults> [resource_list, resource]
```

## What To Look For

The sample is centered on four pieces:

- `DemoBuiltInToolsModel`: a deterministic model that chooses built-in tools based on the current agent surface
- `BuiltInToolsRunner`: builds four agents to show the default baseline, explicit calculator-only selection, a resource-only named agent, and a full opt-out agent
- `application.yml`: configures the named-agent built-in policies and the allowlisted classpath root for resource access
- `src/main/resources/builtin/release-note.md`: packaged reference data that the resource tools can list and read

This intentionally shows the current built-in boundary.

- built-ins are available without declaring any `@StrandsTool` beans
- the default baseline is inherited separately from discovered-tool enablement
- named agents can select one built-in by explicit tool name without inheriting the full baseline
- named agents can narrow the built-in surface without affecting other agents
- resource access remains read-only and allowlist-driven

## Configuration

The sample ships with this default configuration in `src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: arachne-built-in-tools
  main:
    banner-mode: off
    web-application-type: none

arachne:
  strands:
    agent:
      built-ins:
        resources:
          allowed-classpath-locations:
            - classpath:/builtin/
    agents:
      math:
        system-prompt: "You are a calculator-only agent."
        built-ins:
          inherit-defaults: false
          tool-names:
            - calculator
      reader:
        system-prompt: "You are a resource-only reader."
        built-ins:
          inherit-defaults: false
          tool-groups:
            - resource
      strict:
        system-prompt: "You have no built-in tools."
        built-ins:
          inherit-defaults: false
```

No Bedrock model configuration is required because the sample provides its own deterministic `Model` bean.