# Approval Workflow Sample

This sample is a runnable, Bedrock-free demo for a hook-driven approval workflow.

It demonstrates these current-main concepts together:

- a runtime `Plugin` contributes a tool and a hook through `builder().plugins(...)`
- a Spring-managed `@ArachneHook` bean interrupts tool execution before the tool runs
- the application receives observation-only `ArachneLifecycleApplicationEvent` notifications from Spring
- the runner resumes the paused invocation with `AgentResult.resume(...)`

The sample uses a deterministic in-process `Model` bean, so you can verify the hook and interrupt flow without AWS credentials.

## Prerequisites

- Java 25
- Maven

The sample depends on the local `com.mahitotsu.arachne:arachne` snapshot, so install the library module first. The sample itself now uses the `com.mahitotsu.arachne.samples` namespace until the core library coordinates are migrated in a later step:

```bash
mvn -pl arachne -am install
```

## Run The Demo

```bash
cd samples/approval-workflow
mvn spring-boot:run
```

Expected output shape:

```text
Arachne approval workflow sample
request> Book a Kyoto trip that needs operator approval.
initial.stopReason> interrupt
interrupt.name> operatorApproval
interrupt.toolName> approvalTool
interrupt.input> {destination=Kyoto, nights=2}
resume.response> {approved=true, operator=demo-operator}
final.stopReason> end_turn
final.reply> Approval recorded for Kyoto: approved=true by demo-operator
state.workflow> approval-workflow-demo
state.approvalRequested> true
lifecycle.events> beforeInvocation, messageAdded, beforeModelCall, ...
```

You can override the prompt or approval decision:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments='--prompt=Approve an Osaka trip --approval=false --operator=alice'
```

## What To Look For

The sample is centered on four pieces:

- `ApprovalWorkflowRunner`: owns the runtime, triggers the first invocation, and resumes it after an interrupt
- `ApprovalWorkflowPlugin`: contributes the tool surface and a small runtime hook
- `ApprovalGateHook`: a Spring `@ArachneHook` bean that pauses before tool execution
- `LifecycleEventCollector`: receives the observation-only Spring event bridge and prints the event order

This intentionally shows the current control-flow boundaries.

- hooks can change control flow
- Spring lifecycle events are observation-only
- `resume(...)` feeds an external response back as the pending tool result instead of re-entering the original tool automatically

## Configuration

The sample ships with this default configuration in `src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: arachne-approval-workflow
  main:
    web-application-type: none
```

No Bedrock model configuration is required because the sample provides its own deterministic `Model` bean.