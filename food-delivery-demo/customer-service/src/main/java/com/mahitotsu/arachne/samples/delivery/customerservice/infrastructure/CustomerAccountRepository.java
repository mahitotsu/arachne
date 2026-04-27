package com.mahitotsu.arachne.samples.delivery.customerservice.infrastructure;

import static com.mahitotsu.arachne.samples.delivery.customerservice.domain.CustomerTypes.*;

import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;

public interface CustomerAccountRepository {

    Optional<CustomerAccount> findByLoginId(String loginId);

    Optional<CustomerProfileResponse> findProfile(String customerId);

    void seedDemoAccounts(PasswordEncoder passwordEncoder);
}