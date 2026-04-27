package com.mahitotsu.arachne.samples.delivery.orderservice;

import org.springframework.stereotype.Component;

@Component
class AuthenticatedCustomerResolver {

    String currentCustomerId() {
        return SecurityAccessors.currentCustomerId();
    }
}