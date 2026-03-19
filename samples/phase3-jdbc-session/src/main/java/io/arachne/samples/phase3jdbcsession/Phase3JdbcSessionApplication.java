package io.arachne.samples.phase3jdbcsession;

import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;

import io.arachne.strands.agent.Agent;
import io.arachne.strands.model.Model;
import io.arachne.strands.model.ModelEvent;
import io.arachne.strands.spring.AgentFactory;
import io.arachne.strands.types.ContentBlock;
import io.arachne.strands.types.Message;

@SpringBootApplication
@EnableJdbcHttpSession(tableName = "SPRING_SESSION")
public class Phase3JdbcSessionApplication {

    public static void main(String[] args) {
        SpringApplication.run(Phase3JdbcSessionApplication.class, args);
    }

    @Bean
    @SuppressWarnings("unused")
    Agent jdbcSessionAgent(AgentFactory agentFactory) {
        return agentFactory.builder()
                .systemPrompt("Track the user's latest destination and answer with one sentence.")
                .build();
    }

    @Bean
    @SuppressWarnings("unused")
    Model demoModel() {
        return (messages, tools) -> {
            String prompt = lastUserPrompt(messages);
            long userTurns = messages.stream()
                    .filter(message -> message.role() == Message.Role.USER)
                    .count();
            return List.of(
                    new ModelEvent.TextDelta("Turn " + userTurns + " stored for prompt: " + prompt),
                    new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
        };
    }

    private static String lastUserPrompt(List<Message> messages) {
        return messages.stream()
                .filter(message -> message.role() == Message.Role.USER)
                .reduce((first, second) -> second)
                .flatMap(message -> message.content().stream()
                        .filter(ContentBlock.Text.class::isInstance)
                        .map(ContentBlock.Text.class::cast)
                        .map(ContentBlock.Text::text)
                        .findFirst())
                .orElse("(missing prompt)");
    }
}