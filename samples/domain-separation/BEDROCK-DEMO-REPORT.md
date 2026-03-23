# Domain Separation Bedrock Demo Report

## Demo Purpose

This report records the Bedrock-backed demonstration run of the `domain-separation` sample.

The purpose of the demo is to show that the sample is not only a deterministic structural reference, but also a real AI-agent application in which:

- a Bedrock-backed coordinator agent activates a packaged skill
- the coordinator chooses capability-oriented tools
- work is delegated into a specialist executor agent
- the executor agent chooses concrete system tools
- the Spring-managed system layer actually performs the read and mutation work
- the workflow pauses at an approval boundary and later resumes to completion

## Application Architecture Prepared For The Demo

The demo application is a Spring Boot backend with two role-specific agent runtimes inside one process.

### Coordinator Side

- `operations-coordinator` is built at workflow execution time by `DomainSeparationWorkflowService`
- the coordinator receives packaged workflow skills from `domainSeparationCoordinatorSkills`
- the coordinator exposes a small capability-oriented tool surface:
  - `prepare_account_operation`
  - `execute_account_operation`
- the coordinator owns workflow state, approval handling, and session continuity

Relevant implementation files:

- `src/main/java/io/arachne/samples/domainseparation/service/DomainSeparationWorkflowService.java`
- `src/main/java/io/arachne/samples/domainseparation/runner/DomainSeparationRunner.java`
- `src/main/resources/skills/account-unlock/SKILL.md`

### Executor Side

- `operations-executor` is created by the coordinator-facing delegation tool when preparation or execution is needed
- the executor uses concrete system tools:
  - `find_account`
  - `unlock_account`
- those tools call the Spring-managed `AccountDirectoryService`

Relevant implementation files:

- `src/main/java/io/arachne/samples/domainseparation/tool/AccountOperationDelegationTool.java`
- `src/main/java/io/arachne/samples/domainseparation/tool/AccountSystemTool.java`
- `src/main/java/io/arachne/samples/domainseparation/service/AccountDirectoryService.java`

### Observability Used For The Demo

The demo emits three classes of logs.

- `llm.trace>`: what the assistant requested or said
- `demo.trace>`: the visible workflow control flow
- `system.trace>`: actual system reads and mutations performed by the Spring service layer

Relevant implementation files:

- `src/main/java/io/arachne/samples/domainseparation/observation/DomainSeparationDemoLoggingListener.java`
- `src/main/java/io/arachne/samples/domainseparation/workflow/DomainSeparationApprovalHook.java`
- `src/main/java/io/arachne/samples/domainseparation/service/AccountDirectoryService.java`

## How The Application Was Executed

The Bedrock-backed demo was run from the sample directory with the Bedrock profile enabled.

```bash
cd /home/akring/arachne/samples/domain-separation
mvn -Dstyle.color=never spring-boot:run -Dspring-boot.run.profiles=bedrock > bedrock-demo-capture.txt 2>&1
```

Environment assumptions for this run:

- Java 21
- Maven
- AWS credentials resolvable by the AWS SDK default credentials chain
- access to the configured Bedrock model in the target region

## Actual Execution Result

The following is the actual console output from the Bedrock demo run, copied without omission from the captured command output.

```text
[INFO] Scanning for projects...
[INFO] 
[INFO] ----------------< io.arachne.samples:domain-separation >----------------
[INFO] Building Arachne Domain Separation Sample 0.1.0-SNAPSHOT
[INFO]   from pom.xml
[INFO] --------------------------------[ jar ]---------------------------------
[INFO] 
[INFO] >>> spring-boot:3.5.12:run (default-cli) > test-compile @ domain-separation >>>
[INFO] 
[INFO] --- resources:3.3.1:resources (default-resources) @ domain-separation ---
[INFO] Copying 2 resources from src/main/resources to target/classes
[INFO] Copying 4 resources from src/main/resources to target/classes
[INFO] 
[INFO] --- compiler:3.14.1:compile (default-compile) @ domain-separation ---
[INFO] Nothing to compile - all classes are up to date.
[INFO] 
[INFO] --- resources:3.3.1:testResources (default-testResources) @ domain-separation ---
[INFO] skip non existing resourceDirectory /home/akring/arachne/samples/domain-separation/src/test/resources
[INFO] 
[INFO] --- compiler:3.14.1:testCompile (default-testCompile) @ domain-separation ---
[INFO] Nothing to compile - all classes are up to date.
[INFO] 
[INFO] <<< spring-boot:3.5.12:run (default-cli) < test-compile @ domain-separation <<<
[INFO] 
[INFO] 
[INFO] --- spring-boot:3.5.12:run (default-cli) @ domain-separation ---
[INFO] Attaching agents: []
2026-03-24T01:09:41.804+09:00  INFO 755087 --- [arachne-domain-separation-sample] [           main] i.a.s.d.DomainSeparationApplication      : Starting DomainSeparationApplication using Java 21.0.10 with PID 755087 (/home/akring/arachne/samples/domain-separation/target/classes started by akring in /home/akring/arachne/samples/domain-separation)
2026-03-24T01:09:41.806+09:00  INFO 755087 --- [arachne-domain-separation-sample] [           main] i.a.s.d.DomainSeparationApplication      : The following 1 profile is active: "bedrock"
2026-03-24T01:09:43.257+09:00  INFO 755087 --- [arachne-domain-separation-sample] [           main] i.a.s.d.service.AccountDirectoryService  : system.trace> account directory demo state reset: acct-007=LOCKED
2026-03-24T01:09:43.451+09:00  INFO 755087 --- [arachne-domain-separation-sample] [           main] i.a.s.d.DomainSeparationApplication      : Started DomainSeparationApplication in 2.201 seconds (process running for 2.565)
2026-03-24T01:09:43.454+09:00  INFO 755087 --- [arachne-domain-separation-sample] [           main] i.a.s.d.service.AccountDirectoryService  : system.trace> account directory demo state reset: acct-007=LOCKED
2026-03-24T01:09:43.454+09:00  INFO 755087 --- [arachne-domain-separation-sample] [           main] i.a.s.d.runner.DomainSeparationRunner    : Arachne domain separation sample
2026-03-24T01:09:43.454+09:00  INFO 755087 --- [arachne-domain-separation-sample] [           main] i.a.s.d.runner.DomainSeparationRunner    : phase> approval-backed session workflow
2026-03-24T01:09:43.454+09:00  INFO 755087 --- [arachne-domain-separation-sample] [           main] i.a.s.d.runner.DomainSeparationRunner    : supported.operations> [ACCOUNT_CREATION, PASSWORD_RESET_SUPPORT, ACCOUNT_UNLOCK, ACCOUNT_DELETION]
2026-03-24T01:09:43.455+09:00  INFO 755087 --- [arachne-domain-separation-sample] [           main] i.a.s.d.runner.DomainSeparationRunner    : workflow.id> account-unlock-approval-001
2026-03-24T01:09:45.930+09:00  INFO 755087 --- [arachne-domain-separation-sample] [           main] .d.o.DomainSeparationDemoLoggingListener : llm.trace> assistant requested tools: activate_skill
2026-03-24T01:09:45.944+09:00  INFO 755087 --- [arachne-domain-separation-sample] [           main] .d.o.DomainSeparationDemoLoggingListener : demo.trace> coordinator requests skill activation: account-unlock
2026-03-24T01:09:45.968+09:00  INFO 755087 --- [arachne-domain-separation-sample] [     virtual-36] .d.o.DomainSeparationDemoLoggingListener : demo.trace> skill activated: account-unlock
2026-03-24T01:09:46.617+09:00  INFO 755087 --- [arachne-domain-separation-sample] [           main] .d.o.DomainSeparationDemoLoggingListener : llm.trace> assistant requested tools: prepare_account_operation
2026-03-24T01:09:46.618+09:00  INFO 755087 --- [arachne-domain-separation-sample] [           main] .d.o.DomainSeparationDemoLoggingListener : demo.trace> coordinator calls prepare_account_operation for ACCOUNT_UNLOCK acct-007
2026-03-24T01:09:46.946+09:00  INFO 755087 --- [arachne-domain-separation-sample] [     virtual-41] i.a.s.d.t.AccountOperationDelegationTool : demo.trace> delegating prepare request to operations-executor for ACCOUNT_UNLOCK acct-007
2026-03-24T01:09:47.549+09:00  INFO 755087 --- [arachne-domain-separation-sample] [     virtual-41] .d.o.DomainSeparationDemoLoggingListener : llm.trace> assistant requested tools: find_account
2026-03-24T01:09:47.550+09:00  INFO 755087 --- [arachne-domain-separation-sample] [     virtual-41] .d.o.DomainSeparationDemoLoggingListener : demo.trace> executor runs find_account for acct-007
2026-03-24T01:09:47.592+09:00  INFO 755087 --- [arachne-domain-separation-sample] [     virtual-49] i.a.s.d.service.AccountDirectoryService  : system.trace> account directory lookup accountId=acct-007 observedStatus=LOCKED operatorId=operator-7
2026-03-24T01:09:47.638+09:00  INFO 755087 --- [arachne-domain-separation-sample] [     virtual-49] .d.o.DomainSeparationDemoLoggingListener : demo.trace> executor observed account status LOCKED
2026-03-24T01:09:48.488+09:00  INFO 755087 --- [arachne-domain-separation-sample] [     virtual-41] .d.o.DomainSeparationDemoLoggingListener : llm.trace> assistant requested tools: structured_output
2026-03-24T01:09:48.544+09:00  INFO 755087 --- [arachne-domain-separation-sample] [     virtual-41] .d.o.DomainSeparationDemoLoggingListener : demo.trace> preparation returned status READY
2026-03-24T01:09:49.140+09:00  INFO 755087 --- [arachne-domain-separation-sample] [           main] .d.o.DomainSeparationDemoLoggingListener : llm.trace> assistant requested tools: execute_account_operation
2026-03-24T01:09:49.141+09:00  INFO 755087 --- [arachne-domain-separation-sample] [           main] i.a.s.d.w.DomainSeparationApprovalHook   : demo.trace> approval required before execute_account_operation can run; workflow interrupted
2026-03-24T01:09:49.142+09:00  INFO 755087 --- [arachne-domain-separation-sample] [           main] .d.o.DomainSeparationDemoLoggingListener : demo.trace> coordinator calls execute_account_operation for ACCOUNT_UNLOCK acct-007
2026-03-24T01:09:49.195+09:00  INFO 755087 --- [arachne-domain-separation-sample] [           main] i.a.s.d.runner.DomainSeparationRunner    : initial.status> PENDING_APPROVAL
2026-03-24T01:09:49.195+09:00  INFO 755087 --- [arachne-domain-separation-sample] [           main] i.a.s.d.runner.DomainSeparationRunner    : initial.approval.status> PENDING
2026-03-24T01:09:49.195+09:00  INFO 755087 --- [arachne-domain-separation-sample] [           main] i.a.s.d.runner.DomainSeparationRunner    : summary.operationType> ACCOUNT_UNLOCK
2026-03-24T01:09:49.196+09:00  INFO 755087 --- [arachne-domain-separation-sample] [           main] i.a.s.d.runner.DomainSeparationRunner    : summary.accountId> acct-007
2026-03-24T01:09:49.196+09:00  INFO 755087 --- [arachne-domain-separation-sample] [           main] i.a.s.d.runner.DomainSeparationRunner    : summary.preparation.status> READY
2026-03-24T01:09:49.210+09:00  INFO 755087 --- [arachne-domain-separation-sample] [           main] i.a.s.d.runner.DomainSeparationRunner    : session.restored.messages.beforeResume> 6
2026-03-24T01:09:49.213+09:00  INFO 755087 --- [arachne-domain-separation-sample] [           main] i.a.s.d.w.DomainSeparationApprovalHook   : demo.trace> workflow resumed with external approval: approved=true approverId=operator-approver-2
2026-03-24T01:09:49.782+09:00  INFO 755087 --- [arachne-domain-separation-sample] [           main] .d.o.DomainSeparationDemoLoggingListener : llm.trace> assistant requested tools: execute_account_operation
2026-03-24T01:09:49.783+09:00  INFO 755087 --- [arachne-domain-separation-sample] [           main] .d.o.DomainSeparationDemoLoggingListener : demo.trace> coordinator calls execute_account_operation for ACCOUNT_UNLOCK acct-007
2026-03-24T01:09:49.788+09:00  INFO 755087 --- [arachne-domain-separation-sample] [     virtual-51] i.a.s.d.t.AccountOperationDelegationTool : demo.trace> delegating execution request to operations-executor for ACCOUNT_UNLOCK acct-007
2026-03-24T01:09:50.376+09:00  INFO 755087 --- [arachne-domain-separation-sample] [     virtual-51] .d.o.DomainSeparationDemoLoggingListener : llm.trace> assistant requested tools: unlock_account
2026-03-24T01:09:50.376+09:00  INFO 755087 --- [arachne-domain-separation-sample] [     virtual-51] .d.o.DomainSeparationDemoLoggingListener : demo.trace> executor runs unlock_account for acct-007
2026-03-24T01:09:50.383+09:00  INFO 755087 --- [arachne-domain-separation-sample] [     virtual-52] i.a.s.d.service.AccountDirectoryService  : system.trace> account directory unlock applied accountId=acct-007 fromStatus=LOCKED toStatus=UNLOCKED operatorId=operator-7 reason=Manual review completed
2026-03-24T01:09:50.388+09:00  INFO 755087 --- [arachne-domain-separation-sample] [     virtual-52] .d.o.DomainSeparationDemoLoggingListener : demo.trace> executor mutation outcome UNLOCKED
2026-03-24T01:09:51.072+09:00  INFO 755087 --- [arachne-domain-separation-sample] [     virtual-51] .d.o.DomainSeparationDemoLoggingListener : llm.trace> assistant requested tools: structured_output
2026-03-24T01:09:51.086+09:00  INFO 755087 --- [arachne-domain-separation-sample] [     virtual-51] .d.o.DomainSeparationDemoLoggingListener : demo.trace> execution returned outcome UNLOCKED
2026-03-24T01:09:51.788+09:00  INFO 755087 --- [arachne-domain-separation-sample] [           main] .d.o.DomainSeparationDemoLoggingListener : llm.trace> assistant text begin
2026-03-24T01:09:51.788+09:00  INFO 755087 --- [arachne-domain-separation-sample] [           main] .d.o.DomainSeparationDemoLoggingListener : llm.trace> | Workflow Summary:
2026-03-24T01:09:51.789+09:00  INFO 755087 --- [arachne-domain-separation-sample] [           main] .d.o.DomainSeparationDemoLoggingListener : llm.trace> | - Preparation: Account acct-007 is currently LOCKED. Unlock operation is approved for operator-7.
2026-03-24T01:09:51.789+09:00  INFO 755087 --- [arachne-domain-separation-sample] [           main] .d.o.DomainSeparationDemoLoggingListener : llm.trace> | - Execution: Account acct-007 was successfully UNLOCKED.
2026-03-24T01:09:51.789+09:00  INFO 755087 --- [arachne-domain-separation-sample] [           main] .d.o.DomainSeparationDemoLoggingListener : llm.trace> | - Audit: audit: account unlocked for acct-007 because Manual review completed
2026-03-24T01:09:51.789+09:00  INFO 755087 --- [arachne-domain-separation-sample] [           main] .d.o.DomainSeparationDemoLoggingListener : llm.trace> | 
2026-03-24T01:09:51.789+09:00  INFO 755087 --- [arachne-domain-separation-sample] [           main] .d.o.DomainSeparationDemoLoggingListener : llm.trace> | ✅ Account unlock request for acct-007 completed successfully.
2026-03-24T01:09:51.789+09:00  INFO 755087 --- [arachne-domain-separation-sample] [           main] .d.o.DomainSeparationDemoLoggingListener : llm.trace> assistant text end
2026-03-24T01:09:51.792+09:00  INFO 755087 --- [arachne-domain-separation-sample] [           main] i.a.s.d.runner.DomainSeparationRunner    : final.status> COMPLETED
2026-03-24T01:09:51.793+09:00  INFO 755087 --- [arachne-domain-separation-sample] [           main] i.a.s.d.runner.DomainSeparationRunner    : final.approval.status> APPROVED
2026-03-24T01:09:51.793+09:00  INFO 755087 --- [arachne-domain-separation-sample] [           main] i.a.s.d.runner.DomainSeparationRunner    : summary.operationType> ACCOUNT_UNLOCK
2026-03-24T01:09:51.793+09:00  INFO 755087 --- [arachne-domain-separation-sample] [           main] i.a.s.d.runner.DomainSeparationRunner    : summary.accountId> acct-007
2026-03-24T01:09:51.793+09:00  INFO 755087 --- [arachne-domain-separation-sample] [           main] i.a.s.d.runner.DomainSeparationRunner    : summary.preparation.status> READY
2026-03-24T01:09:51.793+09:00  INFO 755087 --- [arachne-domain-separation-sample] [           main] i.a.s.d.runner.DomainSeparationRunner    : summary.execution.outcome> UNLOCKED
2026-03-24T01:09:51.793+09:00  INFO 755087 --- [arachne-domain-separation-sample] [           main] i.a.s.d.runner.DomainSeparationRunner    : summary.execution.authorizedOperator> operator-7
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  13.021 s
[INFO] Finished at: 2026-03-24T01:09:52+09:00
[INFO] ------------------------------------------------------------------------
```

## What Can Be Read From The Execution Result

### 1. Bedrock-backed AI behavior is actually involved in the flow

The log contains repeated `llm.trace>` entries that show assistant-side tool decisions.

- the coordinator requests `activate_skill`
- then requests `prepare_account_operation`
- the executor requests `find_account`
- after approval resume, the coordinator requests `execute_account_operation`
- the executor requests `unlock_account`
- finally the assistant emits a textual workflow summary

This means the run is not merely replaying a fixed, deterministic script at the application edge. A real model is participating in skill and tool selection.

### 2. The application preserves the intended role separation

The visible flow still matches the intended architecture.

- coordinator-side decisions are visible as coordinator tool calls
- executor-side work is visible as delegated calls from `AccountOperationDelegationTool`
- concrete operations are visible on the executor tool and service side

The run therefore supports the claim that one Spring Boot application can keep responsibilities separated without collapsing back to one large prompt.

### 3. Real system work happened in the Spring-managed service layer

The `system.trace>` lines show concrete system reads and mutation work in `AccountDirectoryService`.

- the account state is read as `LOCKED`
- later the account state is mutated from `LOCKED` to `UNLOCKED`

This is important because it demonstrates that the sample is not presenting tool calls as empty ceremony. The service layer did the actual state transition.

### 4. Approval pause and resume are visible and effective

The log shows a real stop at the approval boundary.

- before execution, the workflow enters `PENDING_APPROVAL`
- only after external approval does the workflow continue to the final unlock mutation

This supports the sample's claim that approval is a first-class control-flow boundary rather than a textual convention.

### 5. Bedrock output introduces natural variation while keeping the contract intact

In this run the preparation status appears as `READY` instead of `LOCKED` in the coordinator-visible summary.

That variation is a normal effect of model summarization. Even with that variation, the workflow still behaves correctly:

- initial status becomes `PENDING_APPROVAL`
- final status becomes `COMPLETED`
- the concrete mutation result is `UNLOCKED`

This is a useful demonstration point in its own right: the sample tolerates model phrasing variation while keeping deterministic system outcomes.

## Summary

The Bedrock version of the `domain-separation` demo provides materially stronger evidence than the deterministic-only path.

It demonstrates all of the following in one run:

- a real LLM-backed coordinator activates a packaged skill
- the coordinator chooses capability-oriented tools
- work is delegated to a specialist executor agent
- the executor chooses concrete system tools
- a Spring-managed service performs the actual lookup and mutation
- the workflow pauses for approval and later resumes
- the final result is completed successfully with `UNLOCKED` as the concrete outcome

The deterministic mode remains useful for repeatable local verification, but the Bedrock mode is the persuasive demo path when the goal is to show that Arachne is actually functioning as an AI-agent application rather than as a fixed workflow simulator.