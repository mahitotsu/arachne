package com.mahitotsu.arachne.samples.delivery.orderservice;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
            serviceWith(builder).chat(new ChatRequest(null, "何か食べたい"));

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
            serviceWith(builder).chat(new ChatRequest(null, "I want food"));

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
            serviceWith(builder).chat(new ChatRequest(null, "Je veux manger"));

            ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
            verify(builder).systemPrompt(promptCaptor.capture());
            assertThat(promptCaptor.getValue()).contains("Reply in " + expected + " by default");
        } finally {
            Locale.setDefault(saved);
        }
    }

    /**
     * The system prompt must require explicit customer confirmation before
     * check_kitchen is called, so items are never silently added to the draft.
     */
    @Test
    void systemPromptRequiresExplicitConfirmationBeforeAddingItemsToDraft() {
        AgentFactory.Builder builder = mockBuilder();
        serviceWith(builder).chat(new ChatRequest(null, "おすすめは？"));

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(builder).systemPrompt(promptCaptor.capture());
        String prompt = promptCaptor.getValue();

        assertThat(prompt).contains("NEVER added to the draft without the customer's explicit confirmation");
        assertThat(prompt).contains("Do NOT call check_kitchen in the same turn");
        assertThat(prompt).contains("ONLY after the customer has explicitly confirmed");
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

        when(sessionStore.load(any())).thenReturn(Optional.empty());
        when(agentFactory.builder()).thenReturn(builder);

        // Gateways and OrderRepository are null-safe here because the mock agent
        // never invokes any tools, so the gateway lambdas are never entered.
        return new OrderApplicationService(
                sessionStore, null, null, null, null, null, agentFactory, recentOrderLookupTool);
    }
}
