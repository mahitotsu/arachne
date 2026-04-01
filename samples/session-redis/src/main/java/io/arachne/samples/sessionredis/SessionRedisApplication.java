package com.mahitotsu.arachne.samples.sessionredis;

import java.util.List;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisIndexedHttpSession;

import io.arachne.strands.model.Model;
import io.arachne.strands.model.ModelEvent;
import io.arachne.strands.types.ContentBlock;
import io.arachne.strands.types.Message;

@SpringBootApplication
@EnableRedisIndexedHttpSession(redisNamespace = "arachne:session-redis:sample")
public class SessionRedisApplication {

    public static void main(String[] args) {
        SpringApplication.run(SessionRedisApplication.class, args);
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