# Tool Execution Context Sample

This sample demonstrates the current Arachne replacement for a broad "ToolContext"-style concept.

Instead of one catch-all context object, Arachne splits the concern into two contracts:

- `ToolInvocationContext` for logical tool-call metadata such as tool name, tool use id, raw input, and `AgentState`
- `ExecutionContextPropagation` for executor-boundary propagation of thread-local execution state such as request ids, MDC, or security context

The sample is deterministic and Bedrock-free so you can inspect both concepts without AWS credentials.

## Prerequisites

- Java 25
- Maven

The sample depends on the local `io.arachne:arachne` snapshot, so install the library module first:

```bash
mvn -pl arachne -am install
```

## Run The Demo

```bash
cd samples/tool-execution-context
mvn spring-boot:run
```

Expected output shape:

```text
Arachne tool execution context sample
request> Demonstrate the tool context split.
final.reply> completed tool demo
state.toolCalls> [tool-1:context_echo:alpha, tool-2:context_echo:beta]
propagation.requestIds> [demo-request-42, demo-request-42]
tool.results> [tool-1|context_echo|alpha|demo-request-42, tool-2|context_echo|beta|demo-request-42]
```

## What To Look For

The sample is centered on four pieces:

- `ToolExecutionContextRunner`: seeds a request-scoped id, builds the agent, and prints the observed state
- `ContextEchoTool`: a discovered `@StrandsTool` method that receives `ToolInvocationContext`
- `DemoRequestContext`: a tiny thread-local holder used to make propagation visible in-process
- `ExecutionContextPropagation` bean: captures the current request id and restores it around parallel tool tasks

This is the intended separation of responsibilities:

- use `ToolInvocationContext` when the tool implementation needs logical invocation metadata or `AgentState`
- use `ExecutionContextPropagation` when work crosses an executor boundary and you need thread-local execution state restored

## Configuration

The sample ships with this default configuration in `src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: arachne-tool-execution-context-sample
  main:
    banner-mode: off
    web-application-type: none
```

No Bedrock model configuration is required because the sample provides its own deterministic `Model` bean.
