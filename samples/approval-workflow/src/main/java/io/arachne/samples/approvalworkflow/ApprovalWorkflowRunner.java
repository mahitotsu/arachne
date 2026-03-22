package io.arachne.samples.approvalworkflow;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import io.arachne.strands.agent.Agent;
import io.arachne.strands.agent.AgentInterrupt;
import io.arachne.strands.agent.AgentResult;
import io.arachne.strands.agent.InterruptResponse;
import io.arachne.strands.spring.AgentFactory;

@Component
public class ApprovalWorkflowRunner implements ApplicationRunner {

    private final AgentFactory agentFactory;
    private final LifecycleEventCollector lifecycleEventCollector;

    public ApprovalWorkflowRunner(AgentFactory agentFactory, LifecycleEventCollector lifecycleEventCollector) {
        this.agentFactory = agentFactory;
        this.lifecycleEventCollector = lifecycleEventCollector;
    }

    @Override
    public void run(ApplicationArguments args) {
        Agent agent = agentFactory.builder()
                .plugins(new ApprovalWorkflowPlugin())
                .useDiscoveredTools(false)
                .build();
        String prompt = prompt(args);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("approved", approval(args));
        response.put("operator", operator(args));

        lifecycleEventCollector.reset();

        System.out.println("Arachne approval workflow sample");
        System.out.println("request> " + prompt);

        AgentResult interrupted = agent.run(prompt);
        AgentInterrupt interrupt = interrupted.interrupts().getFirst();

        System.out.println("initial.stopReason> " + interrupted.stopReason());
        System.out.println("interrupt.name> " + interrupt.name());
        System.out.println("interrupt.toolName> " + interrupt.toolName());
        System.out.println("interrupt.input> " + interrupt.input());
        System.out.println("resume.response> " + response);

        AgentResult resumed = interrupted.resume(new InterruptResponse(interrupt.id(), response));

        System.out.println("final.stopReason> " + resumed.stopReason());
        System.out.println("final.reply> " + resumed.text());
        System.out.println("state.workflow> " + agent.getState().get("workflow"));
        System.out.println("state.approvalRequested> " + agent.getState().get("approvalRequested"));
        System.out.println("lifecycle.events> " + formatEventTypes(lifecycleEventCollector.types()));
    }

    private String prompt(ApplicationArguments args) {
        List<String> promptValues = args.getOptionValues("prompt");
        if (promptValues != null && !promptValues.isEmpty()) {
            return promptValues.getFirst();
        }
        return "Book a Kyoto trip that needs operator approval.";
    }

    private boolean approval(ApplicationArguments args) {
        List<String> approvalValues = args.getOptionValues("approval");
        if (approvalValues != null && !approvalValues.isEmpty()) {
            return Boolean.parseBoolean(approvalValues.getFirst());
        }
        return true;
    }

    private String operator(ApplicationArguments args) {
        List<String> operatorValues = args.getOptionValues("operator");
        if (operatorValues != null && !operatorValues.isEmpty()) {
            return operatorValues.getFirst();
        }
        return "demo-operator";
    }

    private String formatEventTypes(List<String> eventTypes) {
        return eventTypes.stream().collect(Collectors.joining(", "));
    }
}