package com.mahitotsu.arachne.samples.statefulbackendoperations;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.mahitotsu.arachne.strands.tool.ToolInvocationContext;
import com.mahitotsu.arachne.strands.tool.annotation.StrandsTool;
import com.mahitotsu.arachne.strands.tool.annotation.ToolParam;

@Service
public class AccountUpdateToolService {

    private final AccountOperationService accountOperationService;

    public AccountUpdateToolService(AccountOperationService accountOperationService) {
        this.accountOperationService = accountOperationService;
    }

    @StrandsTool(name = "prepare_account_update", description = "Prepare a replay-safe backend account update operation.")
    public PreparedAccountUpdate prepareAccountUpdate(
            @ToolParam(description = "Stable idempotent operation key") String operationKey,
            @ToolParam(description = "Account id to update") String accountId,
            @ToolParam(description = "Target account status") String targetStatus,
            @ToolParam(description = "Reason for the update") String reason,
            ToolInvocationContext context) {
        PreparedAccountUpdate prepared = accountOperationService.prepare(operationKey, accountId, targetStatus, reason, context.toolUseId());
        context.state().put("operationKey", prepared.operationKey());
        appendTrace(context, context.toolUseId() + ":prepare:" + prepared.executionState());
        return prepared;
    }

    @StrandsTool(name = "execute_account_update", description = "Execute a prepared backend account update operation.")
    public AccountUpdateResult executeAccountUpdate(
            @ToolParam(description = "Stable idempotent operation key") String operationKey,
            ToolInvocationContext context) {
        AccountUpdateResult result = accountOperationService.execute(operationKey);
        context.state().put("lastExecutionOutcome", result.outcome());
        appendTrace(context, context.toolUseId() + ":execute:" + result.outcome() + ":replayed=" + result.replayed());
        return result;
    }

    @StrandsTool(name = "get_operation_status", description = "Get the current status of a prepared backend operation.")
    public OperationStatusView getOperationStatus(
            @ToolParam(description = "Stable idempotent operation key") String operationKey,
            ToolInvocationContext context) {
        OperationStatusView status = accountOperationService.status(operationKey);
        appendTrace(context, context.toolUseId() + ":status:" + status.executionState());
        return status;
    }

    private void appendTrace(ToolInvocationContext context, String entry) {
        synchronized (context.state()) {
            List<String> values = new ArrayList<>();
            Object existing = context.state().get("toolTrace");
            if (existing instanceof List<?> list) {
                for (Object item : list) {
                    values.add(String.valueOf(item));
                }
            }
            values.add(entry);
            context.state().put("toolTrace", List.copyOf(values));
        }
    }
}