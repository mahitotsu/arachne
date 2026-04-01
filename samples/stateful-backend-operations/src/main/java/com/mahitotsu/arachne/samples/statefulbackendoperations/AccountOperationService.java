package com.mahitotsu.arachne.samples.statefulbackendoperations;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AccountOperationService {

    private final AccountOperationRepository repository;
    private final TransactionTemplate transactionTemplate;

    public AccountOperationService(AccountOperationRepository repository, PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void resetDemoState() {
        repository.resetDemoState();
    }

    public PreparedAccountUpdate prepare(String operationKey, String accountId, String targetStatus, String reason, String toolUseId) {
        return transactionTemplate.execute(status -> {
            OperationRecord existing = repository.findOperation(operationKey).orElse(null);
            String currentStatus = repository.accountStatus(accountId).orElse("NOT_FOUND");
            if (existing != null) {
                return new PreparedAccountUpdate(
                        existing.operationKey(),
                        existing.accountId(),
                        currentStatus,
                        existing.targetStatus(),
                        existing.executionState());
            }
            repository.insertPreparedOperation(operationKey, accountId, targetStatus, reason, toolUseId);
            return new PreparedAccountUpdate(operationKey, accountId, currentStatus, targetStatus, "PREPARED");
        });
    }

    public AccountUpdateResult execute(String operationKey) {
        return transactionTemplate.execute(status -> {
            OperationRecord operation = repository.findOperation(operationKey)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown operation key: " + operationKey));
            if ("COMPLETED".equals(operation.executionState())) {
                return new AccountUpdateResult(
                        operation.operationKey(),
                        operation.accountId(),
                        operation.finalStatus(),
                        "replayed",
                        true,
                        operation.auditMessage());
            }
            String currentStatus = repository.accountStatus(operation.accountId()).orElse("NOT_FOUND");
            if ("NOT_FOUND".equals(currentStatus)) {
                repository.markOperationCompleted(operationKey, "NOT_FOUND", "audit: account not found");
                return new AccountUpdateResult(operationKey, operation.accountId(), "NOT_FOUND", "not_found", false, "audit: account not found");
            }
            repository.updateAccountStatus(operation.accountId(), operation.targetStatus());
            String auditMessage = "audit: account moved from " + currentStatus + " to " + operation.targetStatus();
            repository.markOperationCompleted(operationKey, operation.targetStatus(), auditMessage);
            return new AccountUpdateResult(operationKey, operation.accountId(), operation.targetStatus(), "applied", false, auditMessage);
        });
    }

    public OperationStatusView status(String operationKey) {
        OperationRecord operation = repository.findOperation(operationKey)
                .orElseThrow(() -> new IllegalArgumentException("Unknown operation key: " + operationKey));
        return new OperationStatusView(
                operation.operationKey(),
                operation.accountId(),
                operation.executionState(),
                operation.targetStatus(),
                operation.finalStatus());
    }

    public String currentAccountStatus(String accountId) {
        return repository.accountStatus(accountId).orElse("NOT_FOUND");
    }

    public OperationRecord operationRecord(String operationKey) {
        return repository.findOperation(operationKey)
                .orElseThrow(() -> new IllegalArgumentException("Unknown operation key: " + operationKey));
    }
}