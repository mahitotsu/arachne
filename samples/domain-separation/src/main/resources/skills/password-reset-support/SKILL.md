---
name: password-reset-support
description: Use this skill when the requested operationType is PASSWORD_RESET_SUPPORT.
compatibility: java-25
metadata:
  allowed-tools:
    - prepare_account_operation
    - execute_account_operation
  operation-type: PASSWORD_RESET_SUPPORT
  workflow-family: account-operations
---
Follow this business procedure for password reset support requests.

1. Confirm that the request is asking for password reset support rather than another account operation.
2. Use prepare_account_operation to gather the current account state before execution.
3. Review the preparation details to ensure the request can proceed safely.
4. Use execute_account_operation only after the preparation step returns the required context.
5. Return the final workflow summary with both preparation and execution details.