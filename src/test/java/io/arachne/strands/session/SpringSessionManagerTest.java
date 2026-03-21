package io.arachne.strands.session;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.session.MapSession;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.arachne.strands.types.Message;

class SpringSessionManagerTest {

    @Test
    void saveAndLoadRoundTripsThroughMapSessionRepository() {
        SpringSessionManager manager = new SpringSessionManager(new MapSessionRepository(new ConcurrentHashMap<>()));
        AgentSession session = new AgentSession(
                List.of(Message.user("hello"), Message.assistant("world")),
                Map.of("city", "Tokyo"),
                Map.of("type", "SlidingWindowConversationManager", "windowSize", 4));

        manager.save("trip-planner", session);

        AgentSession restored = manager.load("trip-planner");

        assertThat(restored).isNotNull();
        assertThat(restored.messages()).hasSize(2);
        assertThat(restored.state()).containsEntry("city", "Tokyo");
        assertThat(restored.conversationManagerState()).containsEntry("windowSize", 4);
    }

    @Test
    void saveStoresJsonPayloadsUnderRequestedSessionId() {
        MapSessionRepository repository = new MapSessionRepository(new ConcurrentHashMap<>());
        SpringSessionManager manager = new SpringSessionManager(repository);

        manager.save("support-123", new AgentSession(
                List.of(Message.user("hello")),
                Map.of("tenant", "acme"),
                Map.of("windowSize", 8)));

        MapSession stored = repository.findById("support-123");

        assertThat(stored).isNotNull();
        assertThat((Object) stored.getAttribute(SpringSessionManager.MESSAGES_ATTRIBUTE)).isInstanceOf(String.class);
        assertThat((Object) stored.getAttribute(SpringSessionManager.STATE_ATTRIBUTE)).isInstanceOf(String.class);
        assertThat((Object) stored.getAttribute(SpringSessionManager.CONVERSATION_MANAGER_STATE_ATTRIBUTE)).isInstanceOf(String.class);
    }

    @Test
    void loadReadsJsonEncodedAttributes() {
        MapSessionRepository repository = new MapSessionRepository(new ConcurrentHashMap<>());
        MapSession session = new MapSession("support-456");
        ObjectMapper objectMapper = new ObjectMapper();
        session.setAttribute(SpringSessionManager.MESSAGES_ATTRIBUTE, objectMapper.valueToTree(List.of(Message.user("hello"))).toString());
        session.setAttribute(SpringSessionManager.STATE_ATTRIBUTE, objectMapper.valueToTree(Map.of("tenant", "acme")).toString());
        session.setAttribute(SpringSessionManager.CONVERSATION_MANAGER_STATE_ATTRIBUTE, objectMapper.valueToTree(Map.of("windowSize", 4)).toString());
        repository.save(session);

        SpringSessionManager manager = new SpringSessionManager(repository);
        AgentSession restored = manager.load("support-456");

        assertThat(restored).isNotNull();
        assertThat(restored.messages()).containsExactly(Message.user("hello"));
        assertThat(restored.state()).containsEntry("tenant", "acme");
        assertThat(restored.conversationManagerState()).containsEntry("windowSize", 4);
    }

    @Test
    void saveWrapsSerializationFailures() {
        ObjectMapper failingObjectMapper = new ObjectMapper() {
            @Override
            public String writeValueAsString(Object value) {
                throw new IllegalStateException("boom");
            }
        };
        SpringSessionManager manager = new SpringSessionManager(new MapSessionRepository(new ConcurrentHashMap<>()), failingObjectMapper);

        assertThatThrownBy(() -> manager.save("broken", new AgentSession(List.of(), Map.of(), Map.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to serialize Spring Session payload");
    }

    @Test
    void saveAndLoadRoundTripsThroughJdbcIndexedSessionRepository() {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                .generateUniqueName(true)
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:org/springframework/session/jdbc/schema-h2.sql")
                .build();
        try {
            JdbcIndexedSessionRepository sessionRepository = new JdbcIndexedSessionRepository(
                    new JdbcTemplate(database),
                    new TransactionTemplate(new DataSourceTransactionManager(database)));
            sessionRepository.setCleanupCron(Scheduled.CRON_DISABLED);
            sessionRepository.afterPropertiesSet();

            SpringSessionManager manager = new SpringSessionManager(sessionRepository);
            AgentSession session = new AgentSession(
                    List.of(Message.user("hello"), Message.assistant("world")),
                    Map.of("city", "Tokyo"),
                    Map.of("type", "SlidingWindowConversationManager", "windowSize", 4));

            manager.save("trip-planner", session);

            AgentSession restored = manager.load("trip-planner");

            assertThat(restored).isNotNull();
            assertThat(restored.messages()).hasSize(2);
            assertThat(restored.state()).containsEntry("city", "Tokyo");
            assertThat(restored.conversationManagerState()).containsEntry("windowSize", 4);
        } finally {
            database.shutdown();
        }
    }
}