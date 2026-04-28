package com.mahitotsu.arachne.samples.delivery.customerservice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.jdbc.DataJdbcTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.mahitotsu.arachne.samples.delivery.customerservice.infrastructure.CustomerAccountRepository;
import com.mahitotsu.arachne.samples.delivery.customerservice.infrastructure.JdbcCustomerAccountRepository;

@DataJdbcTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:customer-repository;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(JdbcCustomerAccountRepository.class)
class CustomerAccountRepositoryTest {

    @Autowired
    private CustomerAccountRepository repository;

    @Test
    void seedsAccountsIdempotentlyAndReadsAuthAndProfileViews() {
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

        repository.seedDemoAccounts(passwordEncoder);
        repository.seedDemoAccounts(passwordEncoder);

        assertThat(repository.findByLoginId("demo"))
                .hasValueSatisfying(account -> {
                    assertThat(account.customerId()).isEqualTo("cust-demo-001");
                    assertThat(passwordEncoder.matches("demo-pass", account.passwordHash())).isTrue();
                });

        assertThat(repository.findProfile("cust-demo-001"))
                .hasValueSatisfying(profile -> {
                    assertThat(profile.loginId()).isEqualTo("demo");
                    assertThat(profile.displayName()).isEqualTo("Aoi Sato");
                    assertThat(profile.scopes()).containsExactly("orders.read", "orders.write", "profile.read");
                });
    }
}