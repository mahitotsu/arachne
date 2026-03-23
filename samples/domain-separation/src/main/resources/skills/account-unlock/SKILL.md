---
name: account-unlock
description: Use this skill when the requested operationType is ACCOUNT_UNLOCK.
allowed-tools:
  - prepare_account_operation
  - execute_account_operation
compatibility: java-21
metadata:
  operation-type: ACCOUNT_UNLOCK
  workflow-family: account-operations
---
Follow this business procedure for account unlock requests.

1. Confirm that the request is specifically asking to unlock an existing account.
2. Use prepare_account_operation before any mutation so the executor can inspect the current account state.
3. Review the preparation result and proceed only when the preparation step reports that the account is locked.
4. Use execute_account_operation only after preparation confirms the unlock can proceed.
5. Return the final workflow summary with both preparation and execution details.