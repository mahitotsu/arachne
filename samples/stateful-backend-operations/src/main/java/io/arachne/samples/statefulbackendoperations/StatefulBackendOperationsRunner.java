package io.arachne.samples.statefulbackendoperations;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import io.arachne.strands.agent.Agent;
import io.arachne.strands.spring.AgentFactory;

@Component
public class StatefulBackendOperationsRunner implements ApplicationRunner {

    private final AgentFactory agentFactory;
    private final AccountOperationService accountOperationService;

    public StatefulBackendOperationsRunner(AgentFactory agentFactory, AccountOperationService accountOperationService) {
        this.agentFactory = agentFactory;
        this.accountOperationService = accountOperationService;
    }

    @Override
    public void run(ApplicationArguments args) {
        accountOperationService.resetDemoState();

        Agent agent = agentFactory.builder()
                .toolExecutionMode(io.arachne.strands.tool.ToolExecutionMode.SEQUENTIAL)
                .build();

        String prompt = "Unlock account acct-007 with operation key unlock-acct-007.";
        String reply = agent.run(prompt).text();

        System.out.println("Arachne stateful backend operations sample");
        System.out.println("request> " + prompt);
        System.out.println("final.reply> " + reply);
        System.out.println("state.operationKey> " + agent.getState().get("operationKey"));
        System.out.println("state.lastExecutionOutcome> " + agent.getState().get("lastExecutionOutcome"));
        System.out.println("state.toolTrace> " + agent.getState().get("toolTrace"));
        System.out.println("db.accountStatus> " + accountOperationService.currentAccountStatus("acct-007"));
        System.out.println("db.operationRecord> " + accountOperationService.operationRecord("unlock-acct-007"));
    }
}