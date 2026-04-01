package com.mahitotsu.arachne.samples.sessionjdbc;

import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession;

import com.mahitotsu.arachne.strands.model.Model;
import com.mahitotsu.arachne.strands.model.ModelEvent;
import com.mahitotsu.arachne.strands.types.ContentBlock;
import com.mahitotsu.arachne.strands.types.Message;

@SpringBootApplication
@EnableJdbcHttpSession(tableName = "SPRING_SESSION")
public class SessionJdbcApplication {

    public static void main(String[] args) {
        SpringApplication.run(SessionJdbcApplication.class, args);
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