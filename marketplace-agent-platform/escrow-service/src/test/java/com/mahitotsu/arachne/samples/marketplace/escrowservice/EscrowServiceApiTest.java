package com.mahitotsu.arachne.samples.marketplace.escrowservice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.testcontainers.containers.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
class EscrowServiceApiTest {

  private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
      .withDatabaseName("marketplace_escrow_service")
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
  private EscrowRepository repository;

  @BeforeEach
  void resetRepository() {
    repository.deleteAll();
  }

    @Test
    void evidenceSummaryReturnsHeldFunds() throws Exception {
        mockMvc.perform(post("/internal/escrow/evidence-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caseId": "case-1",
                                  "caseType": "ITEM_NOT_RECEIVED",
                                  "orderId": "order-1001",
                                  "amount": 149.95,
                                  "currency": "USD",
                                  "operatorId": "operator-1",
                                  "operatorRole": "CASE_OPERATOR"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holdState").value("HELD"))
                .andExpect(jsonPath("$.priorSettlementStatus").value("NO_PRIOR_REFUND"));
    }

    @Test
    void settlementActionRequiresFinanceControl() throws Exception {
        mockMvc.perform(post("/internal/escrow/settlement-actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caseId": "case-1",
                                  "action": "CONTINUED_HOLD",
                                  "actorId": "operator-1",
                                  "actorRole": "CASE_OPERATOR",
                                  "amount": 149.95,
                                  "currency": "USD"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void settlementActionPersistsEscrowBusinessTruth() throws Exception {
        mockMvc.perform(post("/internal/escrow/evidence-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caseId": "case-2",
                                  "caseType": "ITEM_NOT_RECEIVED",
                                  "orderId": "order-1002",
                                  "amount": 200.50,
                                  "currency": "EUR",
                                  "operatorId": "operator-1",
                                  "operatorRole": "CASE_OPERATOR"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holdState").value("HELD"))
                .andExpect(jsonPath("$.priorSettlementStatus").value("NO_PRIOR_REFUND"));

        mockMvc.perform(post("/internal/escrow/settlement-actions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caseId": "case-2",
                                  "action": "CONTINUED_HOLD",
                                  "actorId": "finance-1",
                                  "actorRole": "FINANCE_CONTROL",
                                  "amount": 200.50,
                                  "currency": "EUR"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcomeType").value("CONTINUED_HOLD_RECORDED"));

        mockMvc.perform(post("/internal/escrow/evidence-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caseId": "case-2",
                                  "caseType": "ITEM_NOT_RECEIVED",
                                  "orderId": "order-1002",
                                  "amount": 999.99,
                                  "currency": "USD",
                                  "operatorId": "operator-1",
                                  "operatorRole": "CASE_OPERATOR"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holdState").value("HELD"))
                .andExpect(jsonPath("$.amount").value(200.50))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.priorSettlementStatus").value("CONTINUED_HOLD_RECORDED"));
    }
}