# Stateful Backend Operations Sample

This sample shows a backend-oriented custom tool pattern for state-changing operations.

It focuses on four concerns that matter for real backend tool implementations:

- idempotent operation keys
- explicit transaction ownership in the service layer
- logical invocation metadata through `ToolInvocationContext`
- session-visible workflow state through `AgentState`

The sample is deterministic and Bedrock-free.

## What This Sample Teaches

- use a stable `operationKey` to make tool-triggered mutations replay-safe
- keep transaction ownership in a Spring service rather than inside the model loop
- use `ToolInvocationContext` for correlation data such as tool use ids
- use `AgentState` for safe workflow memo data, not for secrets or thread-local state

## Operation Flow

The deterministic model performs this sequence:

1. `prepare_account_update`
2. `execute_account_update`
3. `execute_account_update` again with the same `operationKey`
4. `get_operation_status`

The second execution demonstrates idempotent replay: the same result is returned without applying the mutation twice.

## Prerequisites

- Java 25
- Maven

The sample depends on the local `io.arachne:arachne` snapshot, so install the library module first:

```bash
mvn -pl arachne -am install
```

## Run The Demo

```bash
cd samples/stateful-backend-operations
mvn spring-boot:run
```

Expected output shape:

```text
Arachne stateful backend operations sample
request> Unlock account acct-007 with operation key unlock-acct-007.
final.reply> account update prepared, executed, replay-checked, and verified
state.operationKey> unlock-acct-007
state.toolTrace> [...]
db.accountStatus> UNLOCKED
db.operationRecord> OperationRecord[...]
```

## Design Notes

- the tools are thin orchestration shells over `AccountOperationService`
- `AccountOperationService` owns the transaction boundary and idempotent mutation rules
- `AgentState` stores workflow memo data such as the last `operationKey` and tool trace entries
- `AgentState` does not store database handles, transaction state, or secrets