package com.mahitotsu.arachne.samples.delivery.customerservice;

import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;

interface CustomerAccountRepository {

    Optional<CustomerAccount> findByLoginId(String loginId);

    Optional<CustomerProfileResponse> findProfile(String customerId);

    void seedDemoAccounts(PasswordEncoder passwordEncoder);
}