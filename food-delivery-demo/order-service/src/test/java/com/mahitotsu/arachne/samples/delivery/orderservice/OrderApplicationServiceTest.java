package com.mahitotsu.arachne.samples.delivery.orderservice;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.mahitotsu.arachne.strands.agent.Agent;
import com.mahitotsu.arachne.strands.agent.AgentResult;
import com.mahitotsu.arachne.strands.spring.AgentFactory;
import com.mahitotsu.arachne.strands.tool.Tool;

/**
 * Unit tests for {@link OrderApplicationService}.
 *
 * <p>Tests verify service behavior without a Spring context, using Mockito to
 * isolate the {@link AgentFactory} chain.
 */
class OrderApplicationServiceTest {

    @BeforeEach
    void setAuthentication() {
        Jwt jwt = new Jwt(
                "test-access-token",
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of("alg", "none"),
                Map.of("sub", "demo-user"));
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    /**
     * The default language injected into the system prompt must equal
     * {@code Locale.getDefault().getDisplayLanguage(Locale.ENGLISH)} at
     * construction time — verified here using the Japanese locale.
     */
    @Test
    void systemPromptContainsJvmDefaultLocaleLanguage_japanese() {
        Locale saved = Locale.getDefault();
        try {
            Locale.setDefault(Locale.JAPANESE);

            AgentFactory.Builder builder = mockBuilder();
            serviceWith(builder).chat(new ChatRequest(null, "何か食べたい", null));

            ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
            verify(builder).systemPrompt(promptCaptor.capture());
            assertThat(promptCaptor.getValue()).contains("Reply in Japanese by default");
        } finally {
            Locale.setDefault(saved);
        }
    }

    /**
     * Same contract verified for the English locale.
     */
    @Test
    void systemPromptContainsJvmDefaultLocaleLanguage_english() {
        Locale saved = Locale.getDefault();
        try {
            Locale.setDefault(Locale.ENGLISH);

            AgentFactory.Builder builder = mockBuilder();
            serviceWith(builder).chat(new ChatRequest(null, "I want food", null));

            ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
            verify(builder).systemPrompt(promptCaptor.capture());
            assertThat(promptCaptor.getValue()).contains("Reply in English by default");
        } finally {
            Locale.setDefault(saved);
        }
    }

    /**
     * The language token must equal {@code Locale.getDefault().getDisplayLanguage(Locale.ENGLISH)}
     * for any locale, not just the two above.
     */
    @Test
    void systemPromptLanguageTokenMatchesLocaleDisplayLanguage() {
        Locale saved = Locale.getDefault();
        try {
            Locale.setDefault(Locale.FRENCH);
            String expected = Locale.FRENCH.getDisplayLanguage(Locale.ENGLISH); // "French"

            AgentFactory.Builder builder = mockBuilder();
            serviceWith(builder).chat(new ChatRequest(null, "Je veux manger", null));

            ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
            verify(builder).systemPrompt(promptCaptor.capture());
            assertThat(promptCaptor.getValue()).contains("Reply in " + expected + " by default");
        } finally {
            Locale.setDefault(saved);
        }
    }

    /**
     * The system prompt must require explicit customer confirmation before
     * items are added to the draft. Kitchen status must be shown to the
     * customer in the proposal turn so they can make an informed decision.
     */
    @Test
    void systemPromptRequiresExplicitConfirmationBeforeAddingItemsToDraft() {
        AgentFactory.Builder builder = mockBuilder();
        serviceWith(builder).chat(new ChatRequest(null, "おすすめは？", null));

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(builder).systemPrompt(promptCaptor.capture());
        String prompt = promptCaptor.getValue();

        assertThat(prompt).contains("NEVER added to the draft without the customer's explicit confirmation");
        assertThat(prompt).contains("Do NOT call quote_delivery in the same turn");
        assertThat(prompt).contains("ONLY after the customer has explicitly confirmed");
        assertThat(prompt).contains("one cloud kitchen only");
        assertThat(prompt).contains("Partner Standard");
        assertThat(prompt).contains("In-house Express");
        // Kitchen status must be surfaced to the customer in the proposal turn
        assertThat(prompt).contains("present the proposed items AND the kitchen status");
    }

    @Test
    void chatExposesProposalSkillRoutingForRecommendationRequests() {
        AgentFactory.Builder builder = mockBuilder();

        ChatResponse response = serviceWith(builder).chat(new ChatRequest(null, "子ども向けのおすすめを見せて", null));

        assertThat(response.routing()).isNotNull();
        assertThat(response.routing().intent()).isEqualTo("menu-discovery");
        assertThat(response.routing().selectedSkill()).isEqualTo("proposal-skill");
        assertThat(response.routing().entryStep()).isEqualTo("menu-suggestion");
        assertThat(response.trace())
                .singleElement()
                .satisfies(trace -> {
                    assertThat(trace.service()).isEqualTo("order-service");
                    assertThat(trace.routing()).isEqualTo(response.routing());
                });
    }

    @Test
    void requestLocaleOverridesJvmDefaultLanguageToken() {
        Locale saved = Locale.getDefault();
        try {
            Locale.setDefault(Locale.ENGLISH);

            AgentFactory.Builder builder = mockBuilder();
            serviceWith(builder).chat(new ChatRequest(null, "何か食べたい", "ja-JP"));

            ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
            verify(builder).systemPrompt(promptCaptor.capture());
            assertThat(promptCaptor.getValue()).contains("Reply in Japanese by default");
        } finally {
            Locale.setDefault(saved);
        }
    }

    @Test
    void positivePendingProposalChoiceAddsItemsToDraftWithoutAgentRun() {
        OrderSessionStore sessionStore = mock(OrderSessionStore.class);
        AgentFactory agentFactory = mock(AgentFactory.class);
        Tool recentOrderLookupTool = mock(Tool.class);
        DeliveryGateway deliveryGateway = mock(DeliveryGateway.class);
        AuthenticatedCustomerResolver authenticatedCustomerResolver = mock(AuthenticatedCustomerResolver.class);

        when(authenticatedCustomerResolver.currentCustomerId()).thenReturn("demo-user");

        OrderChatSession existingSession = new OrderChatSession(
                "session-1234",
                List.of(new ConversationMessage("assistant", "おすすめです")),
                new OrderDraft("EMPTY", List.of(), java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, "", "PENDING", "", ""),
                new PendingProposal(
                        List.of(new OrderLineItem("Curly Fries", 1, new java.math.BigDecimal("330.00"), "kitchen ready")),
                "おすすめです"),
            null);
        when(sessionStore.load("session-1234")).thenReturn(Optional.of(existingSession));
                when(deliveryGateway.quote(any(), anyString())).thenReturn(new DeliveryQuoteResponse(
                    "delivery-service",
                    "delivery-agent",
                    "delivery-agent prioritised the express lane",
                    "delivery-agent prepared an express route.",
                    List.of(
                        new DeliveryOptionView("express", "In-house Express", 18, new java.math.BigDecimal("300.00")),
                        new DeliveryOptionView("standard", "Partner Standard", 27, new java.math.BigDecimal("180.00")))));

        OrderApplicationService service = new OrderApplicationService(
                    sessionStore, null, null, null, deliveryGateway, null, agentFactory,
                    authenticatedCustomerResolver, recentOrderLookupTool);

        ChatResponse response = service.chat(new ChatRequest("session-1234", "これを追加", "ja-JP"));

        assertThat(response.draft().status()).isEqualTo("DRAFT_READY");
        assertThat(response.draft().items())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.name()).isEqualTo("Curly Fries");
                    assertThat(item.quantity()).isEqualTo(1);
                });
        assertThat(response.routing().intent()).isEqualTo("draft-confirmation");
        assertThat(response.assistantMessage()).contains("配送候補を確認しました").contains("In-house Express");
        assertThat(response.choices()).containsExactly(
                "In-house Express (18分・¥300)",
                "Partner Standard (27分・¥180)");
        verify(sessionStore).save(any(OrderChatSession.class));
    }

        @Test
        void proposalConfirmationChoiceAddsItemsToDraftWithoutRestartingProposalFlow() {
        OrderSessionStore sessionStore = mock(OrderSessionStore.class);
        AgentFactory agentFactory = mock(AgentFactory.class);
        Tool recentOrderLookupTool = mock(Tool.class);
        DeliveryGateway deliveryGateway = mock(DeliveryGateway.class);
        AuthenticatedCustomerResolver authenticatedCustomerResolver = mock(AuthenticatedCustomerResolver.class);

        when(authenticatedCustomerResolver.currentCustomerId()).thenReturn("demo-user");

        OrderChatSession existingSession = new OrderChatSession(
            "session-5678",
            List.of(new ConversationMessage("assistant", "おすすめです")),
            new OrderDraft("EMPTY", List.of(), java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, "", "PENDING", "", ""),
            new PendingProposal(
                List.of(new OrderLineItem("Crispy Chicken Box", 1, new java.math.BigDecimal("980.00"), "kitchen ready")),
                "おすすめです"),
            null);
        when(sessionStore.load("session-5678")).thenReturn(Optional.of(existingSession));
        when(deliveryGateway.quote(any(), anyString())).thenReturn(new DeliveryQuoteResponse(
                "delivery-service",
                "delivery-agent",
                "delivery-agent prioritised the express lane",
                "delivery-agent prepared an express route.",
                List.of(
                        new DeliveryOptionView("express", "In-house Express", 18, new java.math.BigDecimal("300.00")),
                        new DeliveryOptionView("standard", "Partner Standard", 27, new java.math.BigDecimal("180.00")))));

        OrderApplicationService service = new OrderApplicationService(
            sessionStore, null, null, null, deliveryGateway, null, agentFactory,
            authenticatedCustomerResolver, recentOrderLookupTool);

        ChatResponse response = service.chat(new ChatRequest("session-5678", "はい、この内容で注文します", "ja-JP"));

        assertThat(response.routing()).isNotNull();
        assertThat(response.routing().intent()).isEqualTo("draft-confirmation");
        assertThat(response.routing().entryStep()).isEqualTo("delivery-quote");
        assertThat(response.draft().status()).isEqualTo("DRAFT_READY");
        assertThat(response.draft().items())
            .singleElement()
            .satisfies(item -> assertThat(item.name()).isEqualTo("Crispy Chicken Box"));
        assertThat(response.assistantMessage()).contains("配送候補を確認しました").contains("Partner Standard");
        assertThat(response.choices()).containsExactly(
                "In-house Express (18分・¥300)",
                "Partner Standard (27分・¥180)");
        verify(sessionStore).save(any(OrderChatSession.class));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private AgentFactory.Builder mockBuilder() {
        AgentFactory.Builder builder = mock(AgentFactory.Builder.class, RETURNS_SELF);
        Agent agent = mock(Agent.class);
        when(builder.build()).thenReturn(agent);
        when(agent.run(any(String.class)))
                .thenReturn(new AgentResult("stub reply", List.of(), "end_turn"));
        return builder;
    }

    private OrderApplicationService serviceWith(AgentFactory.Builder builder) {
        OrderSessionStore sessionStore = mock(OrderSessionStore.class);
        AgentFactory agentFactory = mock(AgentFactory.class);
        Tool recentOrderLookupTool = mock(Tool.class);
        AuthenticatedCustomerResolver authenticatedCustomerResolver = mock(AuthenticatedCustomerResolver.class);

        when(sessionStore.load(any())).thenReturn(Optional.empty());
        when(agentFactory.builder()).thenReturn(builder);
        when(authenticatedCustomerResolver.currentCustomerId()).thenReturn("demo-user");

        // Gateways and OrderRepository are null-safe here because the mock agent
        // never invokes any tools, so the gateway lambdas are never entered.
        return new OrderApplicationService(
            sessionStore, null, null, null, null, null, agentFactory,
            authenticatedCustomerResolver, recentOrderLookupTool);
    }
}
