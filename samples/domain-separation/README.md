# Domain Separation Sample

This sample is the higher-level backend reference for Arachne.

Use it when you want to see how the currently shipped features fit together in one Spring Boot application rather than in isolated feature demos.

## What This Sample Demonstrates Now

The current sample combines these behaviors in one workflow:

- named coordinator and executor agent runtimes built from `AgentFactory`
- local agent-to-agent delegation through capability-oriented tools
- packaged skills for workflow-specific business procedure knowledge
- approval pause/resume before the final execution step
- coordinator-owned session restore across the approval boundary
- deterministic authorization and account-state changes in application services
- executor-boundary operator-context propagation during tool execution

The default mode is deterministic and repeatable. An optional Bedrock mode runs the same workflow shape with a real model.

## When To Use This Sample

Choose this sample if you want to study:

- a backend workflow that is larger than a single tool or a single prompt
- the split between coordinating logic and specialist execution logic
- how skills, approval interrupts, session restore, and delegation work together
- how to keep runtime wiring separate from business procedure content

If you only need one isolated feature, start with the narrower samples under `samples/` first.

## Prerequisites

- Java 25
- Maven

For the optional Bedrock mode you also need:

- AWS credentials resolvable by the AWS SDK default credentials chain
- access to the configured Bedrock model in the target AWS region

The sample depends on the local `com.mahitotsu.arachne:arachne` snapshot, so install the library module first. The sample itself now uses the `com.mahitotsu.arachne.samples` namespace until the core library coordinates are migrated in a later step:

```bash
mvn -pl arachne -am install
```

## Run The Demo

### Deterministic Mode

```bash
cd samples/domain-separation
mvn spring-boot:run
```

Expected output shape:

```text
Arachne domain separation sample
phase> approval-backed session workflow
workflow.id> account-unlock-approval-001
initial.status> PENDING_APPROVAL
initial.approval.status> PENDING
summary.operationType> ACCOUNT_UNLOCK
summary.accountId> acct-007
summary.preparation.status> LOCKED
session.restored.messages.beforeResume> 6
final.status> COMPLETED
final.approval.status> APPROVED
summary.execution.outcome> UNLOCKED
summary.execution.authorizedOperator> operator-7
```

The run also emits these trace groups:

- `demo.trace>` for workflow transitions such as skill activation, delegation, approval pause, and approved resume
- `system.trace>` for concrete account reads and mutations in the application layer
- `llm.trace>` for model responses and tool decisions

### Bedrock Mode

```bash
cd samples/domain-separation
mvn spring-boot:run -Dspring-boot.run.profiles=bedrock
```

Optional overrides:

```bash
cd samples/domain-separation
mvn spring-boot:run \
  -Dspring-boot.run.profiles=bedrock \
  -Darachne.integration.bedrock.region=ap-northeast-1 \
  -Darachne.integration.bedrock.model-id=jp.amazon.nova-2-lite-v1:0
```

In Bedrock mode, free-form text and exact message counts can vary, but you should still see the same control-flow checkpoints:

- `demo.trace> coordinator requests skill activation: account-unlock`
- `demo.trace> delegating prepare request to operations-executor`
- `demo.trace> executor runs find_account`
- `demo.trace> approval required before execute_account_operation can run`
- `demo.trace> workflow resumed with external approval`
- `demo.trace> delegating execution request to operations-executor`
- `demo.trace> executor runs unlock_account`

## What To Look For In The Code

- `DomainSeparationRunner`: starts the workflow, shows the approval boundary, and resumes it with external approval input
- `DomainSeparationWorkflowService`: rebuilds the coordinator runtime per workflow turn with a stable `sessionId`
- `DomainSeparationApprovalHook`: records approval-related state, interrupts execution, and accepts later resume input
- `AccountOperationDelegationTool`: exposes coordinator-facing capability tools and delegates to the executor runtime
- `AccountSystemTool`: exposes executor-facing concrete system tools and delegates state changes into the service layer
- `AccountDirectoryService`: owns deterministic account state and the sample-local transaction boundary
- `domainSeparationCoordinatorSkills`: registers packaged workflow skills for the coordinator runtime
- `OperatorAuthorizationContextPropagationConfiguration`: restores the current operator context around tool-execution threads

## Boundaries To Notice

- the coordinator owns workflow state and approval flow
- the executor owns concrete system-operation behavior
- business procedure knowledge is carried by packaged skills, not by one expanding central prompt
- delegation stays local and explicit; this sample does not introduce a new orchestration layer or remote protocol boundary
- approval input returns through the existing `resume(...)` path

## Relation To Other Samples

This sample builds on ideas shown separately elsewhere:

- `tool-delegation`: named agents and local agent delegation
- `approval-workflow`: interrupt and resume
- `skill-activation`: packaged skills
- `session-jdbc` / `session-redis`: restored state across runtime boundaries
- `stateful-backend-operations`: deterministic backend-side state changes

Read those first if you want the narrower capability breakdown before studying the composed backend shape.