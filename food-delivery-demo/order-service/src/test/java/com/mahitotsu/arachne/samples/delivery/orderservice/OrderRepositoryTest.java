package com.mahitotsu.arachne.samples.delivery.orderservice;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;

import com.mahitotsu.arachne.samples.delivery.orderservice.domain.OrderTypes.OrderLineItem;
import com.mahitotsu.arachne.samples.delivery.orderservice.infrastructure.OrderRepository;

@DataJdbcTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:order-repository;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(OrderRepository.class)
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void savesAndReadsConfirmedOrdersThroughSpringDataJdbc() {
        String orderId = orderRepository.saveConfirmedOrder(
                "cust-jdbc-001",
                List.of(
                        new OrderLineItem("Teriyaki Chicken Box", 2, new BigDecimal("920.00"), "repeat"),
                        new OrderLineItem("Lemon Soda", 1, new BigDecimal("240.00"), "repeat")),
                new BigDecimal("2080.00"),
                new BigDecimal("2380.00"),
                "18 min via Partner Standard",
                "CHARGED");

        assertThat(orderRepository.findLatestOrderForUser("cust-jdbc-001"))
                .hasValueSatisfying(order -> {
                    assertThat(order.orderId()).isEqualTo(orderId);
                    assertThat(order.itemSummary()).isEqualTo("2x Teriyaki Chicken Box, 1x Lemon Soda");
                    assertThat(order.total()).isEqualByComparingTo("2380.00");
                });

        assertThat(orderRepository.findRecentOrdersForUser("cust-jdbc-001", 5))
                .singleElement()
                .satisfies(summary -> {
                    assertThat(summary.orderId()).isEqualTo(orderId);
                    assertThat(summary.paymentStatus()).isEqualTo("CHARGED");
                    assertThat(summary.createdAt()).isNotBlank();
                });
    }
}