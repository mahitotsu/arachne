package com.mahitotsu.arachne.samples.delivery.orderservice;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

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

    @Test
    void suggestAddsRecentOrderContextForRepeatRequests() {
        OrderSessionStore sessionStore = mock(OrderSessionStore.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        MenuGateway menuGateway = mock(MenuGateway.class);
        DeliveryGateway deliveryGateway = mock(DeliveryGateway.class);
        PaymentGateway paymentGateway = mock(PaymentGateway.class);
        SupportGateway supportGateway = mock(SupportGateway.class);
        AuthenticatedCustomerResolver customerResolver = mock(AuthenticatedCustomerResolver.class);

        when(sessionStore.load(anyString())).thenReturn(Optional.empty());
        when(customerResolver.currentCustomerId()).thenReturn("demo-user");
        when(orderRepository.findLatestOrderForUser("demo-user"))
                .thenReturn(Optional.of(new StoredOrder("ord-1", "2x Teriyaki Chicken Box", BigDecimal.TEN, BigDecimal.TEN, "18 min", "CHARGED")));
        when(menuGateway.suggest(any(), anyString())).thenReturn(new MenuSuggestionResponse(
                "menu-service",
                "menu-agent",
                "headline",
                "summary",
                List.of(new MenuItemView("combo-teriyaki", "Teriyaki Chicken Box", "", new BigDecimal("920.00"), 1)),
                14,
                new KitchenTraceView("kitchen ok", List.of())));

        OrderApplicationService service = new OrderApplicationService(
                sessionStore,
                orderRepository,
                menuGateway,
                deliveryGateway,
                paymentGateway,
                supportGateway,
                customerResolver);

        service.suggest(new SuggestOrderRequest("session-1", "いつものやつで", "ja-JP", null));

        ArgumentCaptor<MenuSuggestionRequest> captor = ArgumentCaptor.forClass(MenuSuggestionRequest.class);
        verify(menuGateway).suggest(captor.capture(), anyString());
        assertThat(captor.getValue().message()).contains("recent_order=2x Teriyaki Chicken Box");
    }

    @Test
    void confirmItemsUsesSelectedProposalSubsetWhenProvided() {
        OrderSessionStore sessionStore = mock(OrderSessionStore.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        MenuGateway menuGateway = mock(MenuGateway.class);
        DeliveryGateway deliveryGateway = mock(DeliveryGateway.class);
        PaymentGateway paymentGateway = mock(PaymentGateway.class);
        SupportGateway supportGateway = mock(SupportGateway.class);
        AuthenticatedCustomerResolver customerResolver = mock(AuthenticatedCustomerResolver.class);

        when(customerResolver.currentCustomerId()).thenReturn("demo-user");
        when(sessionStore.load("session-1")).thenReturn(Optional.of(new OrderSession(
                "session-1",
                "item-selection",
                new OrderDraft("PROPOSAL_READY", List.of(), BigDecimal.ZERO, BigDecimal.ZERO, "", "PENDING", "", ""),
                new PendingProposal(
                        "飲み物で",
                        "ja-JP",
                        "summary",
                        List.of(
                                new ProposalItem("drink-latte", "Iced Latte", 1, new BigDecimal("320.00"), "cool drink"),
                                new ProposalItem("drink-lemon", "Lemon Soda", 1, new BigDecimal("240.00"), "light drink")),
                        4,
                        new KitchenTraceView("kitchen ok", List.of())),
                null,
                null)));
        when(deliveryGateway.quote(any(), anyString())).thenReturn(new DeliveryQuoteResponse(
                "delivery-service",
                "delivery-agent",
                "headline",
                "summary",
                List.of(new DeliveryOptionView("standard", "Partner Standard", 27, new BigDecimal("180.00"))),
                "standard",
                "「安く」の文脈なので最安候補を優先しました。"));

        OrderApplicationService service = new OrderApplicationService(
                sessionStore,
                orderRepository,
                menuGateway,
                deliveryGateway,
                paymentGateway,
                supportGateway,
                customerResolver);

        ConfirmItemsResponse response = service.confirmItems(
                new ConfirmItemsRequest("session-1", List.of(new SelectedProposalItem("drink-latte"))));

        assertThat(response.workflowStep()).isEqualTo("delivery-selection");
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.name()).isEqualTo("Iced Latte");
            assertThat(item.unitPrice()).isEqualByComparingTo("320.00");
        });
    }

    @Test
    void confirmPaymentPersistsConfirmedOrder() {
        OrderSessionStore sessionStore = mock(OrderSessionStore.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        MenuGateway menuGateway = mock(MenuGateway.class);
        DeliveryGateway deliveryGateway = mock(DeliveryGateway.class);
        PaymentGateway paymentGateway = mock(PaymentGateway.class);
        SupportGateway supportGateway = mock(SupportGateway.class);
        AuthenticatedCustomerResolver customerResolver = mock(AuthenticatedCustomerResolver.class);

        when(customerResolver.currentCustomerId()).thenReturn("demo-user");
        when(sessionStore.load("session-1")).thenReturn(Optional.of(new OrderSession(
                "session-1",
                "payment",
                new OrderDraft(
                        "PAYMENT_READY",
                        List.of(new OrderLineItem("Teriyaki Chicken Box", 2, new BigDecimal("920.00"), "reason")),
                        new BigDecimal("1840.00"),
                        new BigDecimal("2020.00"),
                        "Partner Standard / 27 min",
                        "READY",
                        "Saved Visa ending in 2048",
                        ""),
                null,
                null,
                new DeliveryOptionChoice("standard", "Partner Standard", 27, new BigDecimal("180.00"), "summary", false))));
        when(paymentGateway.prepare(any(), anyString())).thenReturn(new PaymentPrepareResponse(
                "payment-service",
                "payment-service",
                "headline",
                "summary",
                "Saved Visa ending in 2048",
                new BigDecimal("2020.00"),
                "CHARGED",
                true,
                "pay-1"));
        when(orderRepository.saveConfirmedOrder(anyString(), any(), any(), any(), anyString(), anyString())).thenReturn("ord-1234");
        when(supportGateway.recordFeedback(any(), anyString())).thenReturn(Optional.of(new SupportFeedbackResponse(
                "support-service",
                "support-agent",
                "support-agent が問い合わせを受け付けました",
                "GENERAL 問い合わせとして記録しました。",
                "GENERAL",
                false)));

        OrderApplicationService service = new OrderApplicationService(
                sessionStore,
                orderRepository,
                menuGateway,
                deliveryGateway,
                paymentGateway,
                supportGateway,
                customerResolver);

        ConfirmPaymentResponse response = service.confirmPayment(new ConfirmPaymentRequest("session-1"));

        assertThat(response.workflowStep()).isEqualTo("completed");
        assertThat(response.draft().orderId()).isEqualTo("ord-1234");
        assertThat(response.trace()).extracting(ServiceTrace::service)
                .contains("payment-service", "support-service", "order-service");
        verify(orderRepository).saveConfirmedOrder(anyString(), any(), any(), any(), anyString(), anyString());
        ArgumentCaptor<SupportFeedbackRequestPayload> supportCaptor = ArgumentCaptor.forClass(SupportFeedbackRequestPayload.class);
        verify(supportGateway).recordFeedback(supportCaptor.capture(), anyString());
        assertThat(supportCaptor.getValue().orderId()).isEqualTo("ord-1234");
        assertThat(supportCaptor.getValue().message()).contains("注文 ord-1234 が確定しました");
    }

    @Test
    void confirmPaymentDoesNotNotifySupportBeforeChargeCompletes() {
        OrderSessionStore sessionStore = mock(OrderSessionStore.class);
        OrderRepository orderRepository = mock(OrderRepository.class);
        MenuGateway menuGateway = mock(MenuGateway.class);
        DeliveryGateway deliveryGateway = mock(DeliveryGateway.class);
        PaymentGateway paymentGateway = mock(PaymentGateway.class);
        SupportGateway supportGateway = mock(SupportGateway.class);
        AuthenticatedCustomerResolver customerResolver = mock(AuthenticatedCustomerResolver.class);

        when(customerResolver.currentCustomerId()).thenReturn("demo-user");
        when(sessionStore.load("session-1")).thenReturn(Optional.of(new OrderSession(
                "session-1",
                "payment",
                new OrderDraft(
                        "PAYMENT_READY",
                        List.of(new OrderLineItem("Teriyaki Chicken Box", 2, new BigDecimal("920.00"), "reason")),
                        new BigDecimal("1840.00"),
                        new BigDecimal("2020.00"),
                        "Partner Standard / 27 min",
                        "READY",
                        "Saved Visa ending in 2048",
                        ""),
                null,
                null,
                new DeliveryOptionChoice("standard", "Partner Standard", 27, new BigDecimal("180.00"), "summary", false))));
        when(paymentGateway.prepare(any(), anyString())).thenReturn(new PaymentPrepareResponse(
                "payment-service",
                "payment-service",
                "headline",
                "summary",
                "Saved Visa ending in 2048",
                new BigDecimal("2020.00"),
                "READY",
                false,
                ""));

        OrderApplicationService service = new OrderApplicationService(
                sessionStore,
                orderRepository,
                menuGateway,
                deliveryGateway,
                paymentGateway,
                supportGateway,
                customerResolver);

        ConfirmPaymentResponse response = service.confirmPayment(new ConfirmPaymentRequest("session-1"));

        assertThat(response.workflowStep()).isEqualTo("payment");
        verify(supportGateway, never()).recordFeedback(any(), anyString());
    }
}