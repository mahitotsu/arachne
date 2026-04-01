package com.mahitotsu.arachne.samples.marketplace.notificationservice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
class NotificationServiceApiTest {

  private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
      .withDatabaseName("marketplace_notification_service")
      .withUsername("marketplace")
      .withPassword("marketplace");

  static {
    postgres.start();
  }

  @DynamicPropertySource
  static void registerPostgres(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

    @Autowired
    private MockMvc mockMvc;

  @Autowired
  private NotificationRepository repository;

  @BeforeEach
  void resetRepository() {
    repository.deleteAll();
  }

    @Test
    void caseOutcomeQueuesNotification() throws Exception {
        mockMvc.perform(post("/internal/notifications/case-outcome")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caseId": "case-1",
                                  "outcomeType": "CONTINUED_HOLD_RECORDED",
                                  "outcomeStatus": "SUCCEEDED",
                                  "settlementReference": "hold-case-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dispatchStatus").value("QUEUED"))
                .andExpect(jsonPath("$.deliveryStatus").value("PENDING_DELIVERY"));

                    var record = repository.findBySettlementReference("hold-case-1");
                    assertThat(record).isPresent();
                    assertThat(record.orElseThrow().caseId()).isEqualTo("case-1");
                    assertThat(record.orElseThrow().deliveryStatus()).isEqualTo("PENDING_DELIVERY");
    }
}