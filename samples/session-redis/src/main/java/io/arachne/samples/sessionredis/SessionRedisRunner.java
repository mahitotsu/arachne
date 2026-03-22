package io.arachne.samples.sessionredis;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import io.arachne.strands.agent.Agent;
import io.arachne.strands.agent.AgentResult;
import io.arachne.strands.spring.AgentFactory;

@Component
public class SessionRedisRunner implements ApplicationRunner {

    private final AgentFactory agentFactory;
    private final String sessionId;

    public SessionRedisRunner(
            AgentFactory agentFactory,
            @Value("${arachne.strands.agent.session.id}") String sessionId) {
        this.agentFactory = agentFactory;
        this.sessionId = sessionId;
    }

    @Override
    public void run(ApplicationArguments args) {
        Agent agent = agentFactory.builder().build();
        int runCountBefore = readRunCount(agent);
        String prompt = prompt(args);

        System.out.println("Arachne Redis session sample");
        System.out.println("sessionId> " + sessionId);
        System.out.println("restored.messages.before> " + agent.getMessages().size());
        System.out.println("restored.runCount.before> " + runCountBefore);
        System.out.println("prompt> " + prompt);

        agent.getState().put("runCount", runCountBefore + 1);
        AgentResult result = agent.run(prompt);

        System.out.println("reply> " + result.text());
        System.out.println("persisted.messages.after> " + result.messages().size());
        System.out.println("persisted.runCount.after> " + readRunCount(agent));
        System.out.println("next> Run the same command again to see Redis-backed restore.");
    }

    private int readRunCount(Agent agent) {
        Object value = agent.getState().get("runCount");
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private String prompt(ApplicationArguments args) {
        List<String> promptValues = args.getOptionValues("prompt");
        if (promptValues != null && !promptValues.isEmpty()) {
            return promptValues.getFirst();
        }
        return "Remember that my destination is Kyoto.";
    }
}