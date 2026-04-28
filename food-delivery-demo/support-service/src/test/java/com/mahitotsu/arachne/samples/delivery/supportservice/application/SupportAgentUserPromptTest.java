package com.mahitotsu.arachne.samples.delivery.supportservice.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.mahitotsu.arachne.samples.delivery.supportservice.api.SupportChatRequest;

class SupportAgentUserPromptTest {

    @Test
    void rendersInquiryAndCustomerIdAsNamedFields() {
        String rendered = SupportAgentUserPrompt.from(
                new SupportChatRequest("session-1", "配送状況を知りたいです"),
                "cust-demo-001")
                .render();

        assertThat(rendered).isEqualTo("""
                inquiry=配送状況を知りたいです
                customerId=cust-demo-001""");
    }
}