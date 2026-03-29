---
name: account-deletion
description: Use this skill when the requested operationType is ACCOUNT_DELETION.
compatibility: java-25
metadata:
  allowed-tools:
    - prepare_account_operation
    - execute_account_operation
  operation-type: ACCOUNT_DELETION
  workflow-family: account-operations
---
Follow this business procedure for account deletion requests.

1. Confirm that the request is specifically asking to delete an account.
2. Use prepare_account_operation before any mutation so the executor can inspect the current account state.
3. Review the preparation result and make sure the returned details are sufficient before proceeding.
4. Use execute_account_operation only after the preparation step is complete.
5. Return the final workflow summary with both preparation and execution details.