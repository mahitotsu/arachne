package com.mahitotsu.arachne.strands.session;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.transaction.support.TransactionTemplate;

import com.mahitotsu.arachne.strands.agent.Agent;
import com.mahitotsu.arachne.strands.model.Model;
import com.mahitotsu.arachne.strands.model.ModelEvent;
import com.mahitotsu.arachne.strands.spring.AgentFactory;
import com.mahitotsu.arachne.strands.spring.ArachneProperties;

class JdbcSessionManagerIntegrationTest {

    @Test
    void jdbcBackedSpringSessionRestoresMessagesAndStateAcrossNewAgents() {
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

            ArachneProperties properties = new ArachneProperties();
            properties.getAgent().getSession().setId("shared-jdbc-session");
            properties.getAgent().getConversation().setWindowSize(8);

            AgentFactory factory = new AgentFactory(
                    properties,
                    stubModel(),
                    List.of(),
                    com.mahitotsu.arachne.strands.tool.BeanValidationSupport.defaultValidator(),
                    new SpringSessionManager(sessionRepository));

            Agent first = factory.builder().build();
            first.getState().put("city", "Kyoto");
            first.run("Remember that I am traveling to Kyoto.");

            Agent restored = factory.builder().build();

            assertThat(restored.getMessages()).hasSize(2);
            assertThat(restored.getMessages().getFirst().content().getFirst())
                    .isEqualTo(com.mahitotsu.arachne.strands.types.ContentBlock.text("Remember that I am traveling to Kyoto."));
            assertThat(restored.getMessages().get(1).content().getFirst())
                    .isEqualTo(com.mahitotsu.arachne.strands.types.ContentBlock.text("Stub reply to: Remember that I am traveling to Kyoto."));
            assertThat(restored.getState().get("city")).isEqualTo("Kyoto");
        } finally {
            database.shutdown();
        }
    }

    private Model stubModel() {
        return (messages, tools) -> {
            String prompt = messages.getLast().content().stream()
                    .filter(com.mahitotsu.arachne.strands.types.ContentBlock.Text.class::isInstance)
                    .map(com.mahitotsu.arachne.strands.types.ContentBlock.Text.class::cast)
                    .map(com.mahitotsu.arachne.strands.types.ContentBlock.Text::text)
                    .findFirst()
                    .orElse("(missing prompt)");
            return List.of(
                    new ModelEvent.TextDelta("Stub reply to: " + prompt),
                    new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
        };
    }
}