---
name: account-creation
description: Use this skill when the requested operationType is ACCOUNT_CREATION.
allowed-tools:
  - prepare_account_operation
  - execute_account_operation
compatibility: java-21
metadata:
  operation-type: ACCOUNT_CREATION
  workflow-family: account-operations
---
Follow this business procedure for account creation requests.

1. Confirm that the request is specifically asking for a new account to be created.
2. Use prepare_account_operation before any mutation so the executor can inspect the target state.
3. Review the preparation result and verify that the returned details are sufficient for creation.
4. Use execute_account_operation only after preparation is complete.
5. Return the final workflow summary with both preparation and execution details.