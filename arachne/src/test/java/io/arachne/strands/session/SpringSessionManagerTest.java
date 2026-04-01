package io.arachne.strands.session;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
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
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.arachne.strands.agent.AgentInterrupt;
import io.arachne.strands.types.Message;

class SpringSessionManagerTest {

    @Test
    void loadReturnsNullWhenSessionDoesNotExist() {
        SpringSessionManager manager = new SpringSessionManager(new MapSessionRepository(new ConcurrentHashMap<>()));

        assertThat(manager.load("missing-session")).isNull();
    }

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
        assertThat((Object) stored.getAttribute(SpringSessionManager.PENDING_INTERRUPTS_ATTRIBUTE)).isInstanceOf(String.class);
    }

    @Test
    void saveAndLoadRoundTripsPendingInterrupts() {
        SpringSessionManager manager = new SpringSessionManager(new MapSessionRepository(new ConcurrentHashMap<>()));
        AgentSession session = new AgentSession(
                List.of(Message.user("hello")),
                Map.of("tenant", "acme"),
                Map.of("windowSize", 4),
                List.of(new AgentInterrupt(
                        "interrupt-1",
                        "approval",
                        Map.of("message", "need approval"),
                        "tool-1",
                        "approvalTool",
                        Map.of("caseId", "case-1"),
                        null)));

        manager.save("support-789", session);

        AgentSession restored = manager.load("support-789");

        assertThat(restored).isNotNull();
        assertThat(restored.pendingInterrupts()).singleElement().satisfies(interrupt -> {
            assertThat(interrupt.id()).isEqualTo("interrupt-1");
            assertThat(interrupt.toolUseId()).isEqualTo("tool-1");
        });
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
    void loadReadsNativeMessageAndStateAttributes() {
        MapSessionRepository repository = new MapSessionRepository(new ConcurrentHashMap<>());
        MapSession session = new MapSession("native-session");
        session.setAttribute(SpringSessionManager.MESSAGES_ATTRIBUTE, List.of(Message.user("hello")));
        session.setAttribute(SpringSessionManager.STATE_ATTRIBUTE, Map.of("tenant", "acme"));
        session.setAttribute(SpringSessionManager.CONVERSATION_MANAGER_STATE_ATTRIBUTE, Map.of("windowSize", 4));
        repository.save(session);

        SpringSessionManager manager = new SpringSessionManager(repository);
        AgentSession restored = manager.load("native-session");

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

    @Test
    void loadReturnsEmptyStructuresWhenSessionPayloadsAreMissing() {
        MapSessionRepository repository = new MapSessionRepository(new ConcurrentHashMap<>());
        repository.save(new MapSession("empty-session"));

        SpringSessionManager manager = new SpringSessionManager(repository);
        AgentSession restored = manager.load("empty-session");

        assertThat(restored).isNotNull();
        assertThat(restored.messages()).isEmpty();
        assertThat(restored.state()).isEmpty();
        assertThat(restored.conversationManagerState()).isEmpty();
    }

    @Test
    void loadWrapsDeserializationFailuresWithAttributeName() {
        MapSessionRepository repository = new MapSessionRepository(new ConcurrentHashMap<>());
        MapSession session = new MapSession("broken-session");
        session.setAttribute(SpringSessionManager.STATE_ATTRIBUTE, "{not-json}");
        repository.save(session);

        SpringSessionManager manager = new SpringSessionManager(repository);

        assertThatThrownBy(() -> manager.load("broken-session"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(SpringSessionManager.STATE_ATTRIBUTE);
    }

    @Test
    void saveRejectsRepositoriesThatCannotHonorExplicitSessionIds() {
        SessionRepository<Session> repository = new SessionRepository<>() {
            @Override
            public Session createSession() {
                return new NonMapSession("generated-id");
            }

            @Override
            public void save(Session session) {
            }

            @Override
            public Session findById(String id) {
                return null;
            }

            @Override
            public void deleteById(String id) {
            }
        };

        SpringSessionManager manager = new SpringSessionManager(repository);

        assertThatThrownBy(() -> manager.save("explicit-id", new AgentSession(List.of(), Map.of(), Map.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported Spring Session repository");
    }

    @Test
    void saveRejectsRepositoriesThatReturnNullSessions() {
        SessionRepository<Session> repository = new SessionRepository<>() {
            @Override
            public Session createSession() {
                return null;
            }

            @Override
            public void save(Session session) {
            }

            @Override
            public Session findById(String id) {
                return null;
            }

            @Override
            public void deleteById(String id) {
            }
        };

        SpringSessionManager manager = new SpringSessionManager(repository);

        assertThatThrownBy(() -> manager.save("explicit-id", new AgentSession(List.of(), Map.of(), Map.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("createSession() must not return null");
    }

    @Test
    void saveAcceptsRepositoriesThatAlreadyUseRequestedSessionId() {
        AtomicReference<Session> savedSession = new AtomicReference<>();
        SessionRepository<Session> repository = new SessionRepository<>() {
            @Override
            public Session createSession() {
                return new NonMapSession("explicit-id");
            }

            @Override
            public void save(Session session) {
                savedSession.set(session);
            }

            @Override
            public Session findById(String id) {
                return null;
            }

            @Override
            public void deleteById(String id) {
            }
        };

        SpringSessionManager manager = new SpringSessionManager(repository);
        manager.save("explicit-id", new AgentSession(List.of(Message.user("hello")), Map.of("tenant", "acme"), Map.of()));

        assertThat(savedSession.get()).isNotNull();
        assertThat(savedSession.get().getId()).isEqualTo("explicit-id");
        assertThat((Object) savedSession.get().getAttribute(SpringSessionManager.MESSAGES_ATTRIBUTE)).isInstanceOf(String.class);
    }

    private static final class NonMapSession implements Session {

        private final Map<String, Object> attributes = new ConcurrentHashMap<>();
        private final java.time.Instant creationTime = java.time.Instant.now();
        private final String id;

        private NonMapSession(String id) {
            this.id = id;
        }

        @Override
        public java.time.Instant getCreationTime() {
            return creationTime;
        }

        @Override
        public void setLastAccessedTime(java.time.Instant lastAccessedTime) {
        }

        @Override
        public java.time.Instant getLastAccessedTime() {
            return java.time.Instant.EPOCH;
        }

        @Override
        public void setMaxInactiveInterval(java.time.Duration interval) {
        }

        @Override
        public java.time.Duration getMaxInactiveInterval() {
            return java.time.Duration.ZERO;
        }

        @Override
        public boolean isExpired() {
            return false;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String changeSessionId() {
            return id;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T getAttribute(String attributeName) {
            return (T) attributes.get(attributeName);
        }

        @Override
        public java.util.Set<String> getAttributeNames() {
            return attributes.keySet();
        }

        @Override
        public void setAttribute(String attributeName, Object attributeValue) {
            attributes.put(attributeName, attributeValue);
        }

        @Override
        public void removeAttribute(String attributeName) {
            attributes.remove(attributeName);
        }
    }
}