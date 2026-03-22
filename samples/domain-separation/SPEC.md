# Domain Separation Sample Specification

This file records the concrete design decisions for the `domain-separation` sample.

Use this file for the sample-specific design that becomes true as Phase 1 decisions are made.
Use `README.md` for user-facing purpose and usage.
Use `ROADMAP.md` for temporary task and progress tracking.

## Status

This specification is being introduced before the runnable sample exists.

At this stage, it should be treated as the design record for Phase 1: skill and role design.

## Scope

The sample is intended to demonstrate an account-operations backend workflow inside one Spring Boot application.

The sample should combine these current-main Arachne capabilities:

- named agents for role-specific runtime defaults
- `agent as tool` delegation from a coordinator to specialist runtimes through stable capability-oriented tools
- packaged skills as the business-procedure boundary
- approval pause and resume
- session-backed workflow continuity
- execution-context propagation for request-scoped authorization state

The sample should not require deferred distributed capabilities such as A2A, MCP, or remote orchestration.

## Scenario

The sample domain is account operations for a backend application.

The initial business scope includes these workflow variants:

- account creation
- password reset support
- account unlock
- account deletion

The first implementation may still execute a smaller subset first, but the specification should treat this sample as a multi-workflow account-operations shell rather than a single isolated operation.

Working shape:

1. an incoming request asks for an account-management operation
2. the coordinator selects and activates the relevant business-procedure skill for that workflow
3. the coordinator calls a stable capability-oriented tool to prepare the requested account operation
4. that tool delegates to a specialist runtime that knows the account-management system details
5. the coordinator assembles a structured summary, including any required approval checks
6. if the workflow requires approval, the workflow pauses for approval
7. the workflow resumes with an external approval decision when needed
8. the coordinator calls a stable capability-oriented tool to execute the approved or approval-free operation
9. that tool delegates to the specialist runtime and returns the final result

## Role Model

### `operations-coordinator`

Responsibility:
Own the top-level workflow, select the relevant skill, call stable capability-oriented tools, and control the approval boundary.

Expected behavior:

- receives the top-level request
- activates the relevant packaged skill when needed
- calls capability-oriented tools when preparation or execution detail is needed
- assembles the structured result used for approval
- pauses and resumes the workflow around the approval boundary

Examples of coordinator concerns:

- decide whether the request is account creation, password reset support, account unlock, or account deletion
- follow the workflow-specific checklist carried by the active skill
- decide whether the active workflow requires approval before execution
- present the final result in a workflow-appropriate way

Security context concerns:

- receive the operator request together with access-token-backed authorization context
- pass that authorization context into delegated tool execution through Spring-appropriate propagation
- present authorization failures as explicit system outcomes rather than implicit model behavior

### `operations-executor`

Responsibility:
Own the system-operation side of the workflow: interpret a requested capability, inspect the current system state, and use concrete tools to prepare or execute the requested account operation.

Expected behavior:

- receives a focused capability request from the coordinator-side tool
- uses operation-specific knowledge and concrete tools to inspect or change system state
- returns a typed preparation or execution result
- does not own the business workflow, skill selection, or approval boundary

Examples of executor concerns:

- check whether an account already exists
- inspect whether an account is locked or deleted
- determine whether a password reset can be issued
- apply the concrete account mutation through deterministic tools
- record audit information for the performed action
- enforce capability-specific authorization checks before concrete mutations run

AI usage boundary:

- the executor may use LLM capability where interpretation or summarization adds value
- security-sensitive state changes should still be executed through deterministic tools
- operations that demand strict correctness should not rely on free-form model output alone
- when needed, the implementation may force concrete tool usage or keep AI outside the final mutation path

Transaction boundary:

- concrete mutation tools should be transaction-aware
- transaction ownership should live in the deterministic service or tool implementation that performs the update
- the sample should not rely on model-side reasoning as the place where transactional correctness is enforced

## Business Knowledge Boundary

Business procedure knowledge should live primarily in packaged skills.

Skills should contain:

- workflow-specific business steps
- business-side decision hints
- checklist-style guidance about what information to gather before execution or approval
- references to the stable capability-oriented tools that should be used at each stage

Runtime prompts and Java wiring should contain:

- role definition
- delegation rules
- stable capability-oriented tool usage rules
- typed output expectations
- approval and session handling behavior
- security-context propagation wiring
- transaction-boundary wiring for concrete update operations

## Security And Authorization Boundary

Spring-specific authorization handling is part of the value of this sample.

The intended shape is:

- the incoming operator request carries an access token or equivalent authorization state
- that authorization state is available to the coordinator-facing entry point
- delegated tool execution propagates the relevant authorization context across executor boundaries
- concrete executor tools use that propagated context for permission checks before state-changing operations run

Implementation guidance:

- use `ExecutionContextPropagation` for executor-boundary propagation of request-scoped authorization context
- keep logical tool-call metadata and security context separate
- prefer Spring-native authorization state such as `SecurityContext` or a sample-local request authorization holder over ad hoc prompt content
- treat authorization failures as deterministic system outcomes, not as model interpretation problems

This means the sample should demonstrate not only that business workflow varies by skill, but also that Spring-style security context can follow the request into delegated tool execution.

## Transaction Boundary

The sample should treat state-changing account operations as transaction-aware work.

The intended shape is:

- read-only preparation steps may remain non-transactional or explicitly read-only
- mutation steps such as account creation, unlock, deletion, or password reset issuance should execute inside explicit transaction boundaries
- transaction ownership should sit with the deterministic concrete tool or the Spring service it calls
- the coordinator and skill layer should not own transaction semantics

This keeps the transactional guarantee attached to actual state mutation rather than to high-level workflow narration.

## Tool Surface Boundary

The sample should not register one tool per business workflow.

Instead, the coordinator should see a small, stable tool surface that represents reusable system capabilities. Business variation should come from skills, while system capability variation should come from tools and the specialist runtime behind them.

Initial rule:

- do not add tools named after individual workflows
- prefer capability-oriented tool names that remain stable as business skills grow
- let those tools delegate to the named specialist runtime rather than embedding all system behavior directly in the coordinator

### Planned Capability-Oriented Tools

#### `prepare_account_operation`

Responsibility:
Delegate to `operations-executor` to inspect the requested account operation, gather required execution details, and return a typed preparation result for execution or approval.

#### `execute_account_operation`

Responsibility:
Delegate to `operations-executor` to apply the requested account operation through concrete system tools and return a typed final result.

### Planned Concrete Executor Tools

These are lower-level deterministic tools behind the executor role. They represent concrete system capabilities rather than business workflows.

- `find_account`
- `create_account`
- `issue_password_reset`
- `unlock_account`
- `delete_account`
- `record_account_audit`

Expected transactional posture:

- `find_account` is read-only
- `create_account`, `issue_password_reset`, `unlock_account`, and `delete_account` are transaction-aware mutations
- `record_account_audit` should participate in the intended mutation boundary or explicit post-action recording policy

## Initial Skill Design

The first version uses packaged skills only on the coordinator side.

Reasoning:

- this keeps business-procedure ownership concentrated in one place
- it keeps specialist runtimes narrow and easy to understand
- it makes the distinction between workflow knowledge and specialist judgment explicit

Initial rule:

- `operations-coordinator` uses packaged skills
- `operations-executor` does not use packaged skills in the first version

### Planned Skills

#### `account-creation`

Responsibility:
Guide the coordinator through the business procedure for creating a new account safely and consistently.

Expected content:

- define the information required before account creation
- define checks that should happen before execution
- tell the coordinator when to call `prepare_account_operation`
- define when approval is required for account creation in this sample
- tell the coordinator when to call `execute_account_operation`

Expected trigger:

- the incoming request asks to create a new account

#### `password-reset-support`

Responsibility:
Guide the coordinator through the business procedure for password reset handling.

Expected content:

- define the identity and safety checks required before password reset handling
- tell the coordinator when to call `prepare_account_operation`
- define when approval is or is not required
- tell the coordinator when to call `execute_account_operation`

Expected trigger:

- the incoming request asks for password reset support

#### `account-unlock`

Responsibility:
Guide the coordinator through the business procedure for unlocking an account.

Expected content:

- define the checks required before unlocking
- tell the coordinator when to call `prepare_account_operation`
- define when approval is required for unlock operations
- tell the coordinator when to call `execute_account_operation`

Expected trigger:

- the incoming request asks to unlock an account

#### `account-deletion`

Responsibility:
Guide the coordinator through the business procedure for deleting an account.

Expected content:

- define the checks required before deletion
- define why deletion is treated as a sensitive operation in this sample
- tell the coordinator when to call `prepare_account_operation`
- require an approval summary before deletion is executed
- tell the coordinator when to call `execute_account_operation` after approval

Expected trigger:

- the incoming request asks to delete an account

## Interaction Boundaries

The first version uses `agent as tool` behind stable capability-oriented tools rather than exposing workflow-specific tools directly to the coordinator.

Interactions handled through `agent as tool`:

- `operations-coordinator` -> `prepare_account_operation` -> `operations-executor`
- `operations-coordinator` -> `execute_account_operation` -> `operations-executor`

Steps that remain plain application-service logic:

- accepting the external request into the sample runner or controller boundary
- passing the external approval decision into `resume(...)`
- any persistence or transport details for session restore

Spring-specific infrastructure concerns that should remain explicit:

- access-token-backed authorization extraction at the entry boundary
- authorization context propagation configuration
- transaction annotations or service-layer transaction configuration

The coordinator still owns the end-to-end workflow shape. The executor owns system-operation behavior behind the stable capability-oriented tools.

### Structured Result Types

#### `OperationPreparation`

Produced by:
`operations-executor` through `prepare_account_operation`

Expected fields:

- `operationType`
- `actionSummary`
- `requiredInputs`
- `operatorImpact`
- `rollbackNotes`
- `executionAllowed`

#### `ApprovalSummary`

Produced by:
`operations-coordinator`

Expected fields:

- `operationType`
- `requestSummary`
- `operationPreparation`
- `approvalChecks`
- `approvalReason`

#### `FinalWorkflowResult`

Produced by:
`operations-coordinator`, using the execution result returned from `execute_account_operation`

Expected fields:

- `operationType`
- `approved`
- `decisionSummary`
- `finalActionStatement`
- `followUpNotes`

#### `OperationExecutionResult`

Produced by:
`operations-executor` through `execute_account_operation`

Expected fields:

- `operationType`
- `executed`
- `executionSummary`
- `changedResources`
- `rollbackReference`

## Session And Approval Boundary

This section will be implemented in later phases, but the intended boundary is already clear.

- workflow continuity should survive the intended session boundary
- approval should remain an explicit pause/resume step
- the coordinator should remain the owner of approval state

The first version should treat the approval decision itself as external input rather than trying to model the approver as another agent.

In the account-operations domain, approval should be workflow-dependent rather than universal. For example, account deletion may always require approval in the sample, while password reset handling may not.

## Notes

This sample may present a workflow-oriented application architecture.

That should not be treated as a claim that Arachne core now provides a generic workflow engine abstraction. The sample is an application-level composition of existing Arachne features.