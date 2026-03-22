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

    private final Agent agent;

    public ConversationBasicsRunner(AgentFactory factory) {
        this.agent = factory.builder().build();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (args.containsOption("demo")) {
            runDemoConversation();
            return;
        }

        runInteractiveConversation();
    }

    private void runDemoConversation() {
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

        printHistorySummary();
    }

    private void runInteractiveConversation() {
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
                    printHistorySummary();
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
        System.out.println();
    }

    private void printHistorySummary() {
        System.out.println("history> total messages=" + agent.getMessages().size());
        System.out.println();
    }
}