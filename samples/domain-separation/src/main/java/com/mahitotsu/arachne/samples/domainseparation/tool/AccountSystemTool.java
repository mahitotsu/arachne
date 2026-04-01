package com.mahitotsu.arachne.samples.domainseparation.tool;

import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import com.mahitotsu.arachne.samples.domainseparation.domain.AccountLookupResult;
import com.mahitotsu.arachne.samples.domainseparation.domain.AccountMutationResult;
import com.mahitotsu.arachne.samples.domainseparation.domain.AccountOperationType;
import com.mahitotsu.arachne.samples.domainseparation.security.OperatorAuthorizationContext;
import com.mahitotsu.arachne.samples.domainseparation.security.OperatorAuthorizationContextHolder;
import com.mahitotsu.arachne.samples.domainseparation.service.AccountDirectoryService;
import com.mahitotsu.arachne.strands.tool.annotation.StrandsTool;
import com.mahitotsu.arachne.strands.tool.annotation.ToolParam;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Service
@Validated
public class AccountSystemTool {

    private final OperatorAuthorizationContextHolder authorizationContextHolder;
    private final AccountDirectoryService accountDirectoryService;

    public AccountSystemTool(
            OperatorAuthorizationContextHolder authorizationContextHolder,
            AccountDirectoryService accountDirectoryService) {
        this.authorizationContextHolder = authorizationContextHolder;
        this.accountDirectoryService = accountDirectoryService;
    }

    @StrandsTool(
            name = "find_account",
            description = "Inspect the current account state before any mutation is attempted.",
            qualifiers = "operations-executor")
    public AccountLookupResult findAccount(
            @ToolParam(name = "operationType", description = "Requested account operation type") @NotNull
            AccountOperationType operationType,
            @ToolParam(name = "accountId", description = "Target account identifier") @NotBlank
            String accountId,
            @ToolParam(name = "requestedBy", description = "Operator requesting the action") @NotBlank
            String requestedBy,
            @ToolParam(name = "reason", description = "Business reason for the operation", required = false)
            String reason) {
        OperatorAuthorizationContext authorization = currentAuthorization();
        if (operationType != AccountOperationType.ACCOUNT_UNLOCK) {
            throw new IllegalArgumentException("Phase 3 sample only supports ACCOUNT_UNLOCK.");
        }
        if (authorization == null) {
            return new AccountLookupResult(accountId, "AUTHORIZATION_CONTEXT_MISSING", "unknown-operator");
        }
        if (!authorization.permissions().contains("account:unlock")) {
            return new AccountLookupResult(accountId, "AUTHORIZATION_FAILED", authorization.operatorId());
        }
        return accountDirectoryService.findAccount(accountId, authorization.operatorId());
    }

    @StrandsTool(
            name = "unlock_account",
            description = "Perform the concrete account unlock mutation after preparation is complete.",
            qualifiers = "operations-executor")
    public AccountMutationResult unlockAccount(
            @ToolParam(name = "operationType", description = "Requested account operation type") @NotNull
            AccountOperationType operationType,
            @ToolParam(name = "accountId", description = "Target account identifier") @NotBlank
            String accountId,
            @ToolParam(name = "requestedBy", description = "Operator requesting the action") @NotBlank
            String requestedBy,
            @ToolParam(name = "reason", description = "Business reason for the operation", required = false)
            String reason) {
        OperatorAuthorizationContext authorization = currentAuthorization();
        if (operationType != AccountOperationType.ACCOUNT_UNLOCK) {
            throw new IllegalArgumentException("Phase 3 sample only supports ACCOUNT_UNLOCK.");
        }
        if (authorization == null) {
            return new AccountMutationResult(
                    accountId,
                    "AUTHORIZATION_CONTEXT_MISSING",
                    "audit: unlock denied because the operator authorization context was not propagated",
                    "unknown-operator");
        }
        if (!authorization.permissions().contains("account:unlock")) {
            return new AccountMutationResult(
                    accountId,
                    "AUTHORIZATION_FAILED",
                    "audit: unlock denied for " + accountId + " because operator lacks account:unlock",
                    authorization.operatorId());
        }
        return accountDirectoryService.unlockAccount(accountId, reason, authorization.operatorId());
    }

    private OperatorAuthorizationContext currentAuthorization() {
        return authorizationContextHolder.current();
    }
}