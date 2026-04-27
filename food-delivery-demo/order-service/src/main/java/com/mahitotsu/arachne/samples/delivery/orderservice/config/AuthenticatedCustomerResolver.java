package com.mahitotsu.arachne.samples.delivery.orderservice.config;

import org.springframework.stereotype.Component;

@Component
public class AuthenticatedCustomerResolver {

    public String currentCustomerId() {
        return SecurityAccessors.currentCustomerId();
    }
}