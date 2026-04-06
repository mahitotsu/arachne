package com.mahitotsu.arachne.samples.marketplace.riskservice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class RiskServiceApiTest {

  private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
      .withDatabaseName("marketplace_risk_service")
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
  private RiskRepository repository;

  @BeforeEach
  void resetRepository() {
    repository.deleteAll();
  }

    @Test
    void caseReviewReturnsManualReviewFlag() throws Exception {
        mockMvc.perform(post("/internal/risk/case-review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caseId": "case-1",
                                  "caseType": "ITEM_NOT_RECEIVED",
                                  "orderId": "order-1001",
                                  "operatorRole": "CASE_OPERATOR"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.manualReviewRequired").value(true))
                .andExpect(jsonPath("$.policyFlags[0]").value("FINANCE_CONTROL_REVIEW_REQUIRED"));
    }

    @Test
    void caseReviewReturnsPersistedRiskAssessment() throws Exception {
        mockMvc.perform(post("/internal/risk/case-review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caseId": "case-2",
                                  "caseType": "ITEM_NOT_RECEIVED",
                                  "orderId": "order-1001",
                                  "operatorRole": "CASE_OPERATOR"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indicatorSummary").value("No elevated fraud signal detected for the current order."));

        mockMvc.perform(post("/internal/risk/case-review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caseId": "case-2",
                                  "caseType": "ITEM_NOT_RECEIVED",
                                  "orderId": "order-9999",
                                  "operatorRole": "FINANCE_CONTROL"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indicatorSummary").value("No elevated fraud signal detected for the current order."))
                .andExpect(jsonPath("$.policyFlags[0]").value("FINANCE_CONTROL_REVIEW_REQUIRED"));
    }

    @Test
    void caseReviewReflectsHighRiskSettlementScenario() throws Exception {
        mockMvc.perform(post("/internal/risk/case-review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caseId": "case-risk",
                                  "caseType": "HIGH_RISK_SETTLEMENT_HOLD",
                                  "orderId": "order-risk-1",
                                  "disputeSummary": "Risk controls flagged unusual account activity.",
                                  "operatorRole": "CASE_OPERATOR"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.manualReviewRequired").value(true))
                .andExpect(jsonPath("$.policyFlags[0]").value("HIGH_RISK_SETTLEMENT_HOLD"))
                .andExpect(jsonPath("$.indicatorSummary").value("Elevated fraud and account-takeover indicators are present for the current order."));
    }

    @Test
    void specialistReviewUsesRiskAgentInsideRiskService() throws Exception {
        mockMvc.perform(post("/internal/risk/specialist-review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caseId": "case-risk-review",
                                  "caseType": "ITEM_NOT_RECEIVED",
                                  "orderId": "order-1001",
                                  "disputeSummary": "Buyer reports item not received.",
                                  "operatorRole": "CASE_OPERATOR",
                                  "instruction": "Please summarize the risk view."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value(org.hamcrest.Matchers.containsString("risk-agent reviewed the operator instruction")))
                .andExpect(jsonPath("$.summary").value(org.hamcrest.Matchers.containsString("FINANCE_CONTROL_REVIEW_REQUIRED")));
    }
}