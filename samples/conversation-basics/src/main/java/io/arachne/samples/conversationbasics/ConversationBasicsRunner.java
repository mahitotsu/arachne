package io.arachne.samples.conversationbasics;

import java.util.List;
import java.util.Scanner;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import io.arachne.strands.agent.Agent;
import io.arachne.strands.agent.AgentResult;
import io.arachne.strands.spring.AgentFactory;

@Component
public class ConversationBasicsRunner implements ApplicationRunner {

    private static final String CACHE_DEMO_SYSTEM_PROMPT = """
            You are a concise assistant for an Amazon Bedrock prompt-caching demonstration. Preserve user facts across turns. Answer directly from conversation memory unless the user explicitly asks for the sample reference tool. Keep answers short and concrete. This policy block is intentionally static so repeated requests can reuse Bedrock cache checkpoints. Reference rule set: preserve stated facts, prefer direct answers, avoid unnecessary tool use, keep the wording compact, and treat prior user statements as durable conversation facts for the duration of the session.
            """.repeat(24);

    private final AgentFactory factory;

    public ConversationBasicsRunner(AgentFactory factory) {
        this.factory = factory;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (args.containsOption("cache-demo")) {
            runCacheDemoConversation();
            return;
        }
        if (args.containsOption("demo")) {
            runDemoConversation();
            return;
        }

        runInteractiveConversation();
    }

    private void runDemoConversation() {
        Agent agent = createDefaultAgent();
        System.out.println("Arachne conversation basics demo");
        System.out.println("One runner-owned Agent runtime is reused across turns.");
        System.out.println();

        List<String> prompts = List.of(
                "この会話では、私の好きな色は青です。覚えてください。",
                "私の好きな色は何ですか？ 一語で答えてください。");

        for (String prompt : prompts) {
            AgentResult result = agent.run(prompt);
            printTurn(prompt, result);
        }

        printHistorySummary(agent);
    }

    private void runCacheDemoConversation() {
        Agent agent = factory.builder()
                .systemPrompt(CACHE_DEMO_SYSTEM_PROMPT)
                .build();

        System.out.println("Arachne Bedrock prompt caching demo");
        System.out.println("This run keeps one runner-owned Agent across turns and prints Bedrock usage metrics.");
        System.out.println("The system prompt is intentionally long and static so Bedrock cache checkpoints can activate.");
        System.out.println();

        List<String> prompts = List.of(
                "この会話では、私の好きな色は青です。覚えてください。",
                "私の好きな色は何ですか？ 一語で答えてください。");

        for (int index = 0; index < prompts.size(); index++) {
            String prompt = prompts.get(index);
            AgentResult result = agent.run(prompt);
            System.out.println("turn> " + (index + 1));
            printTurn(prompt, result);
        }

        System.out.println("cacheHint> first turn should usually write cache tokens; the second turn should usually read cached tokens when the selected Bedrock model supports prompt caching.");
        System.out.println();
        printHistorySummary(agent);
    }

    private void runInteractiveConversation() {
        Agent agent = createDefaultAgent();
        System.out.println("Arachne conversation basics REPL");
        System.out.println("Enter a prompt and press Enter.");
        System.out.println("Commands: :history, :quit");
        System.out.println();

        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("you> ");
                if (!scanner.hasNextLine()) {
                    System.out.println();
                    return;
                }

                String line = scanner.nextLine().trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (":quit".equals(line)) {
                    return;
                }
                if (":history".equals(line)) {
                    printHistorySummary(agent);
                    continue;
                }

                AgentResult result = agent.run(line);
                printTurn(line, result);
            }
        }
    }

    private void printTurn(String prompt, AgentResult result) {
        System.out.println("user> " + prompt);
        System.out.println("agent> " + result.text());
        System.out.println("stopReason> " + result.stopReason());
        System.out.println("messageCount> " + result.messages().size());
        System.out.println("metrics.inputTokens> " + result.metrics().usage().inputTokens());
        System.out.println("metrics.outputTokens> " + result.metrics().usage().outputTokens());
        System.out.println("metrics.cacheReadInputTokens> " + result.metrics().usage().cacheReadInputTokens());
        System.out.println("metrics.cacheWriteInputTokens> " + result.metrics().usage().cacheWriteInputTokens());
        System.out.println();
    }

    private void printHistorySummary(Agent agent) {
        System.out.println("history> total messages=" + agent.getMessages().size());
        System.out.println();
    }

    private Agent createDefaultAgent() {
        return factory.builder().build();
    }
}