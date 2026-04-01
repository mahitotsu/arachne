package com.mahitotsu.arachne.samples.domainseparation.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.mahitotsu.arachne.samples.domainseparation.domain.AccountLookupResult;
import com.mahitotsu.arachne.samples.domainseparation.domain.AccountMutationResult;

@Service
public class AccountDirectoryService {

    private static final Logger log = LoggerFactory.getLogger(AccountDirectoryService.class);

    private final TransactionTemplate transactionTemplate;
    private final Map<String, String> accountStates = new ConcurrentHashMap<>();

    public AccountDirectoryService(PlatformTransactionManager transactionManager) {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        resetDemoState();
    }

    public void resetDemoState() {
        accountStates.clear();
        accountStates.put("acct-007", "LOCKED");
        log.info("system.trace> account directory demo state reset: acct-007=LOCKED");
    }

    public AccountLookupResult findAccount(String accountId, String operatorId) {
        String currentStatus = accountStates.getOrDefault(accountId, "NOT_FOUND");
        log.info(
                "system.trace> account directory lookup accountId={} observedStatus={} operatorId={}",
                accountId,
                currentStatus,
                operatorId);
        return new AccountLookupResult(accountId, currentStatus, operatorId);
    }

    public AccountMutationResult unlockAccount(String accountId, String reason, String operatorId) {
        return transactionTemplate.execute(status -> {
            String currentStatus = accountStates.get(accountId);
            if (currentStatus == null) {
                log.info(
                        "system.trace> account directory unlock skipped accountId={} operatorId={} reason=account not found",
                        accountId,
                        operatorId);
                return new AccountMutationResult(
                        accountId,
                        "NOT_FOUND",
                        "audit: unlock skipped because account was not found",
                        operatorId);
            }
            if (!"LOCKED".equals(currentStatus)) {
                log.info(
                    "system.trace> account directory unlock skipped accountId={} operatorId={} currentStatus={}",
                    accountId,
                    operatorId,
                    currentStatus);
                return new AccountMutationResult(
                        accountId,
                        currentStatus,
                        "audit: unlock skipped because account was already in status " + currentStatus,
                        operatorId);
            }
            accountStates.put(accountId, "UNLOCKED");
                    log.info(
                        "system.trace> account directory unlock applied accountId={} fromStatus={} toStatus=UNLOCKED operatorId={} reason={}",
                        accountId,
                        currentStatus,
                        operatorId,
                        reason == null || reason.isBlank() ? "no reason provided" : reason);
            return new AccountMutationResult(
                    accountId,
                    "UNLOCKED",
                    "audit: account unlocked for " + accountId + " because "
                            + (reason == null || reason.isBlank() ? "no reason provided" : reason),
                    operatorId);
        });
    }
}