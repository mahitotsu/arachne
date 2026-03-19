package io.arachne.strands.session;

import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import io.arachne.strands.agent.Agent;
import io.arachne.strands.model.Model;
import io.arachne.strands.model.ModelEvent;
import io.arachne.strands.spring.AgentFactory;
import io.arachne.strands.spring.ArachneProperties;

class RedisSessionManagerIntegrationTest {

    @Test
    void redisBackedSpringSessionRestoresMessagesAndStateAcrossNewAgents() {
        Assumptions.assumeTrue(dockerAvailable(),
                "Docker is required for the Redis Testcontainers integration test.");

        GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"));
        redis.withExposedPorts(6379);
        try (redis) {
            redis.start();

            LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(
                Objects.requireNonNull(redis.getHost()),
                    redis.getFirstMappedPort());
            connectionFactory.afterPropertiesSet();

            RedisIndexedSessionRepository sessionRepository = null;
            try {
                sessionRepository = redisIndexedSessionRepository(connectionFactory);

                ArachneProperties properties = new ArachneProperties();
                properties.getAgent().getSession().setId("shared-redis-session");
                properties.getAgent().getConversation().setWindowSize(8);

                AgentFactory factory = new AgentFactory(
                        properties,
                        stubModel(),
                        List.of(),
                        io.arachne.strands.tool.BeanValidationSupport.defaultValidator(),
                        new SpringSessionManager(sessionRepository));

                Agent first = factory.builder().build();
                first.getState().put("city", "Kyoto");
                first.run("Remember that I am traveling to Kyoto.");

                Agent restored = factory.builder().build();

                assertThat(restored.getMessages()).hasSize(2);
                assertThat(restored.getMessages().getFirst().content().getFirst())
                        .isEqualTo(io.arachne.strands.types.ContentBlock.text("Remember that I am traveling to Kyoto."));
                assertThat(restored.getMessages().get(1).content().getFirst())
                        .isEqualTo(io.arachne.strands.types.ContentBlock.text("Stub reply to: Remember that I am traveling to Kyoto."));
                assertThat(restored.getState().get("city")).isEqualTo("Kyoto");
            } finally {
                if (sessionRepository != null) {
                    sessionRepository.destroy();
                }
                connectionFactory.destroy();
            }
        }
    }

    private boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private RedisIndexedSessionRepository redisIndexedSessionRepository(LettuceConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        redisTemplate.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        redisTemplate.afterPropertiesSet();

        RedisIndexedSessionRepository sessionRepository = new RedisIndexedSessionRepository(redisTemplate);
        sessionRepository.setRedisKeyNamespace("arachne:test:redis-session");
        sessionRepository.afterPropertiesSet();
        return sessionRepository;
    }

    private Model stubModel() {
        return (messages, tools) -> {
            String prompt = messages.getLast().content().stream()
                    .filter(io.arachne.strands.types.ContentBlock.Text.class::isInstance)
                    .map(io.arachne.strands.types.ContentBlock.Text.class::cast)
                    .map(io.arachne.strands.types.ContentBlock.Text::text)
                    .findFirst()
                    .orElse("(missing prompt)");
            return List.of(
                    new ModelEvent.TextDelta("Stub reply to: " + prompt),
                    new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
        };
    }
}