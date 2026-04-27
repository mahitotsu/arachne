package com.mahitotsu.arachne.samples.delivery.customerservice.infrastructure;

import static com.mahitotsu.arachne.samples.delivery.customerservice.domain.CustomerTypes.*;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class JdbcCustomerAccountRepository implements CustomerAccountRepository {

    private final JdbcClient jdbcClient;

    JdbcCustomerAccountRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Optional<CustomerAccount> findByLoginId(String loginId) {
        return jdbcClient.sql("""
                select customer_id, login_id, password_hash, display_name, default_locale, scopes
                from customer_accounts
                where login_id = :loginId
                """)
                .param("loginId", loginId)
                .query(this::mapAccount)
                .optional();
    }

    @Override
    public Optional<CustomerProfileResponse> findProfile(String customerId) {
        return jdbcClient.sql("""
                select customer_id, login_id, display_name, default_locale, scopes
                from customer_accounts
                where customer_id = :customerId
                """)
                .param("customerId", customerId)
                .query((rs, rowNum) -> new CustomerProfileResponse(
                        rs.getString("customer_id"),
                        rs.getString("login_id"),
                        rs.getString("display_name"),
                        rs.getString("default_locale"),
                        parseScopes(rs.getString("scopes"))))
                .optional();
    }

    @Override
    public void seedDemoAccounts(PasswordEncoder passwordEncoder) {
        insertAccount("cust-demo-001", "demo", "Aoi Sato", "ja-JP", "orders.read orders.write profile.read",
                passwordEncoder.encode("demo-pass"));
        insertAccount("cust-demo-002", "family", "ファミリーアカウント", "ja-JP", "orders.read orders.write profile.read",
                passwordEncoder.encode("family-pass"));
        insertAccount("cust-solo-001", "solo", "Hina Nakamura", "ja-JP", "orders.read orders.write profile.read",
                passwordEncoder.encode("solo-pass"));
        insertAccount("cust-corp-001", "corporate", "法人アカウント", "ja-JP", "orders.read orders.write profile.read",
                passwordEncoder.encode("corp-pass"));
    }

    private void insertAccount(
            String customerId,
            String loginId,
            String displayName,
            String defaultLocale,
            String scopes,
            String passwordHash) {
        jdbcClient.sql("""
                insert into customer_accounts (customer_id, login_id, password_hash, display_name, default_locale, scopes)
                values (:customerId, :loginId, :passwordHash, :displayName, :defaultLocale, :scopes)
                on conflict do nothing
                """)
                .param("customerId", customerId)
                .param("loginId", loginId)
                .param("passwordHash", passwordHash)
                .param("displayName", displayName)
                .param("defaultLocale", defaultLocale)
                .param("scopes", scopes)
                .update();
    }

    private CustomerAccount mapAccount(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new CustomerAccount(
                rs.getString("customer_id"),
                rs.getString("login_id"),
                rs.getString("password_hash"),
                rs.getString("display_name"),
                rs.getString("default_locale"),
                rs.getString("scopes"));
    }

    private static List<String> parseScopes(String scopes) {
        return java.util.Arrays.stream(scopes.split("\\s+"))
                .filter(scope -> !scope.isBlank())
                .toList();
    }
}