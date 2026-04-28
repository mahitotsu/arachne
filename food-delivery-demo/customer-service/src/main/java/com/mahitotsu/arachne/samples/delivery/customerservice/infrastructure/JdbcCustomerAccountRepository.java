package com.mahitotsu.arachne.samples.delivery.customerservice.infrastructure;

import java.util.List;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.mahitotsu.arachne.samples.delivery.customerservice.domain.CustomerTypes.CustomerAccount;
import com.mahitotsu.arachne.samples.delivery.customerservice.domain.CustomerTypes.CustomerProfileResponse;

@Component
public class JdbcCustomerAccountRepository implements CustomerAccountRepository {

    private final CustomerAccountJdbcRepository jdbcRepository;

    JdbcCustomerAccountRepository(CustomerAccountJdbcRepository jdbcRepository) {
        this.jdbcRepository = jdbcRepository;
    }

    @Override
    public Optional<CustomerAccount> findByLoginId(String loginId) {
        return jdbcRepository.findByLoginId(loginId)
                .map(this::toCustomerAccount);
    }

    @Override
    public Optional<CustomerProfileResponse> findProfile(String customerId) {
        return jdbcRepository.findById(customerId)
                .map(this::toProfileResponse);
    }

    @Override
    public void seedDemoAccounts(PasswordEncoder passwordEncoder) {
        saveIfMissing("cust-demo-001", "demo", "Aoi Sato", "ja-JP", "orders.read orders.write profile.read",
                passwordEncoder.encode("demo-pass"));
        saveIfMissing("cust-demo-002", "family", "ファミリーアカウント", "ja-JP", "orders.read orders.write profile.read",
                passwordEncoder.encode("family-pass"));
        saveIfMissing("cust-solo-001", "solo", "Hina Nakamura", "ja-JP", "orders.read orders.write profile.read",
                passwordEncoder.encode("solo-pass"));
        saveIfMissing("cust-corp-001", "corporate", "法人アカウント", "ja-JP", "orders.read orders.write profile.read",
                passwordEncoder.encode("corp-pass"));
    }

    private void saveIfMissing(
            String customerId,
            String loginId,
            String displayName,
            String defaultLocale,
            String scopes,
            String passwordHash) {
        if (jdbcRepository.existsById(customerId)) {
            return;
        }
        jdbcRepository.save(new CustomerAccountAggregate(
                customerId,
                loginId,
                passwordHash,
                displayName,
                defaultLocale,
                scopes));
    }

    private CustomerAccount toCustomerAccount(CustomerAccountAggregate aggregate) {
        return new CustomerAccount(
                aggregate.customerId(),
                aggregate.loginId(),
                aggregate.passwordHash(),
                aggregate.displayName(),
                aggregate.defaultLocale(),
                aggregate.scopes());
    }

    private CustomerProfileResponse toProfileResponse(CustomerAccountAggregate aggregate) {
        return new CustomerProfileResponse(
                aggregate.customerId(),
                aggregate.loginId(),
                aggregate.displayName(),
                aggregate.defaultLocale(),
                parseScopes(aggregate.scopes()));
    }

    private static List<String> parseScopes(String scopes) {
        return java.util.Arrays.stream(scopes.split("\\s+"))
                .filter(scope -> !scope.isBlank())
                .toList();
    }
}