package com.mahitotsu.arachne.samples.marketplace.shipmentservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
class ShipmentServiceApiTest {

  private static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
      .withDatabaseName("marketplace_shipment_service")
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
  private ShipmentRepository repository;

  @BeforeEach
  void resetRepository() {
    repository.deleteAll();
  }

    @Test
    void evidenceSummaryReturnsTrackingContext() throws Exception {
        mockMvc.perform(post("/internal/shipment/evidence-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caseId": "case-1",
                                  "caseType": "ITEM_NOT_RECEIVED",
                                  "orderId": "order-1001"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value("TRACK-order-1001"))
                .andExpect(jsonPath("$.deliveryConfidence").value("LOW"));
    }

    @Test
    void evidenceSummaryReturnsPersistedShipmentFacts() throws Exception {
        mockMvc.perform(post("/internal/shipment/evidence-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caseId": "case-2",
                                  "caseType": "ITEM_NOT_RECEIVED",
                                  "orderId": "order-2001"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value("TRACK-order-2001"));

        mockMvc.perform(post("/internal/shipment/evidence-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caseId": "case-2",
                                  "caseType": "ITEM_NOT_RECEIVED",
                                  "orderId": "order-9999"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value("TRACK-order-2001"))
                .andExpect(jsonPath("$.shippingExceptionSummary").value("Shipment remains in a not-delivered state for the current case."));
    }

    @Test
    void evidenceSummaryReflectsDeliveredButDamagedScenario() throws Exception {
        mockMvc.perform(post("/internal/shipment/evidence-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caseId": "case-damaged",
                                  "caseType": "DELIVERED_BUT_DAMAGED",
                                  "disputeSummary": "Buyer says the package arrived crushed and wet.",
                                  "orderId": "order-dmg-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deliveryConfidence").value("HIGH"))
                .andExpect(jsonPath("$.shippingExceptionSummary").value("Shipment was delivered, but the package exterior shows impact damage and moisture exposure."));
    }

    @Test
    void evidenceSummaryUsesSeededDemoTemplatesForPresetOrders() throws Exception {
        mockMvc.perform(post("/internal/shipment/evidence-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caseId": "case-demo-1",
                                  "caseType": "ITEM_NOT_RECEIVED",
                                  "orderId": "ord-demo-001"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value("TRACK-ord-demo-001"))
                .andExpect(jsonPath("$.deliveryConfidence").value("LOW"));

        mockMvc.perform(post("/internal/shipment/evidence-summary")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caseId": "case-demo-2",
                                  "caseType": "HIGH_RISK_SETTLEMENT_HOLD",
                                  "orderId": "order-risk-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingNumber").value("TRACK-order-risk-1"))
                .andExpect(jsonPath("$.deliveryConfidence").value("MEDIUM"))
                .andExpect(jsonPath("$.shippingExceptionSummary").value("Shipment completed, but delivery evidence is unusual enough to support a settlement hold pending risk review."));
    }

    @Test
    void specialistReviewUsesShipmentAgentInsideShipmentService() throws Exception {
        mockMvc.perform(post("/internal/shipment/specialist-review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "caseId": "case-review-1",
                                  "caseType": "ITEM_NOT_RECEIVED",
                                  "disputeSummary": "Buyer reports item not received.",
                                  "orderId": "order-1001",
                                  "instruction": "Please summarize the shipment evidence."
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value(org.hamcrest.Matchers.containsString("shipment-agent reviewed the operator instruction")))
                .andExpect(jsonPath("$.summary").value(org.hamcrest.Matchers.containsString("Tracking number: TRACK-order-1001")));
    }
}