# Streaming And Steering Sample

This sample is a runnable, Bedrock-free demo for callback streaming plus runtime-local steering.

It demonstrates these current-main concepts together:

- callback-based streaming through `agent.stream(...)`
- tool steering with `Guide`, which skips a risky tool call before the tool runs
- model steering with `Guide`, which discards a provisional response and retries with a guidance message
- builder-level Spring integration through `AgentFactory.builder().steeringHandlers(...)`

The sample uses a deterministic in-process `StreamingModel`, so you can verify the flow without AWS credentials.

## Prerequisites

- Java 25
- Maven

The sample depends on the local `io.arachne:arachne` snapshot, so install the library module first:

```bash
mvn -pl arachne -am install
```

## Run The Demo

```bash
cd samples/streaming-steering
mvn spring-boot:run
```

Expected output shape:

```text
Arachne streaming and steering sample
request> Can I refund an unopened item?
stream.1> text=Checking the refund guidance...
stream.2> toolUse=policy_lookup {topic=refunds}
stream.3> toolResult=error Use the cached refund policy summary instead of the live lookup.
stream.4> text=The risky live lookup was blocked.
stream.5> retry=Provide the cached refund policy summary directly.
stream.6> text=Cached refund policy: unopened items can be returned within 30 days with the original receipt.
stream.7> complete=end_turn
final.stopReason> end_turn
final.reply> Cached refund policy: unopened items can be returned within 30 days with the original receipt.
tool.invocations> 0
conversation.guidancePresent> true
model.invocations> 3
```

You can override the prompt:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments='--prompt=What is the safe refund summary?'
```

## What To Look For

The sample is centered on four pieces:

- `StreamingSteeringRunner`: builds the runtime, subscribes to stream events, and prints the final result
- `DemoStreamingSteeringModel`: deterministic `StreamingModel` that emits a tool request, a provisional response, and a final retry response
- `DemoSteeringHandler`: applies tool guidance and guided model retry through the steering contract
- `PolicyLookupTool`: a tool surface that would have been called without steering, used here to prove the guide path skips execution

This intentionally shows the current streaming and steering boundaries.

- streaming is callback-based and one-way output only
- tool guidance becomes a tool-result error payload that the model can react to
- model retry emits a `Retry` stream event and appends a guidance user message before the next model turn
- steering remains runtime-local and opt-in on the builder

## Configuration

The sample ships with this default configuration in `src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: arachne-streaming-steering
  main:
    banner-mode: off
    web-application-type: none

arachne:
  strands:
    agent:
      system-prompt: "You are a support assistant. Prefer safe cached guidance over risky live lookups."
```

No Bedrock model configuration is required because the sample provides its own deterministic `StreamingModel` bean.