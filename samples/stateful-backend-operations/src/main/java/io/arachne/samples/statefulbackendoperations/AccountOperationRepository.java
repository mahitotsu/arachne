package io.arachne.samples.statefulbackendoperations;

import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AccountOperationRepository {

    private final JdbcTemplate jdbcTemplate;

    public AccountOperationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void resetDemoState() {
        jdbcTemplate.update("DELETE FROM ACCOUNT_OPERATION");
        jdbcTemplate.update("DELETE FROM ACCOUNT_RECORD");
        jdbcTemplate.update("INSERT INTO ACCOUNT_RECORD (ACCOUNT_ID, STATUS) VALUES (?, ?)", "acct-007", "LOCKED");
    }

    public Optional<String> accountStatus(String accountId) {
        return jdbcTemplate.query(
                        "SELECT STATUS FROM ACCOUNT_RECORD WHERE ACCOUNT_ID = ?",
                        (rs, rowNum) -> rs.getString("STATUS"),
                        accountId)
                .stream()
                .findFirst();
    }

    public void insertPreparedOperation(String operationKey, String accountId, String targetStatus, String reason, String toolUseId) {
        jdbcTemplate.update(
                "INSERT INTO ACCOUNT_OPERATION (OPERATION_KEY, ACCOUNT_ID, TARGET_STATUS, REASON, EXECUTION_STATE, TOOL_USE_ID, AUDIT_MESSAGE) VALUES (?, ?, ?, ?, ?, ?, ?)",
                operationKey,
                accountId,
                targetStatus,
                reason,
                "PREPARED",
                toolUseId,
                "audit: operation prepared");
    }

    public Optional<OperationRecord> findOperation(String operationKey) {
        return jdbcTemplate.query(
                        "SELECT OPERATION_KEY, ACCOUNT_ID, TARGET_STATUS, REASON, EXECUTION_STATE, FINAL_STATUS, TOOL_USE_ID, AUDIT_MESSAGE FROM ACCOUNT_OPERATION WHERE OPERATION_KEY = ?",
                        (rs, rowNum) -> new OperationRecord(
                                rs.getString("OPERATION_KEY"),
                                rs.getString("ACCOUNT_ID"),
                                rs.getString("TARGET_STATUS"),
                                rs.getString("REASON"),
                                rs.getString("EXECUTION_STATE"),
                                rs.getString("FINAL_STATUS"),
                                rs.getString("TOOL_USE_ID"),
                                rs.getString("AUDIT_MESSAGE")),
                        operationKey)
                .stream()
                .findFirst();
    }

    public void updateAccountStatus(String accountId, String status) {
        jdbcTemplate.update("UPDATE ACCOUNT_RECORD SET STATUS = ? WHERE ACCOUNT_ID = ?", status, accountId);
    }

    public void markOperationCompleted(String operationKey, String finalStatus, String auditMessage) {
        jdbcTemplate.update(
                "UPDATE ACCOUNT_OPERATION SET EXECUTION_STATE = ?, FINAL_STATUS = ?, AUDIT_MESSAGE = ? WHERE OPERATION_KEY = ?",
                "COMPLETED",
                finalStatus,
                auditMessage,
                operationKey);
    }
}