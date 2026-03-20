package io.arachne.strands.session;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.session.MapSessionRepository;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.transaction.support.TransactionTemplate;

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