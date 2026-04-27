package com.mahitotsu.arachne.samples.delivery.orderservice;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

final class SecurityAccessors {

    private SecurityAccessors() {
    }

    static String currentCustomerId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            return jwtAuthenticationToken.getToken().getSubject();
        }
        throw new IllegalStateException("No authenticated customer is available in the security context");
    }

    static Optional<String> currentAccessToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            return Optional.of(jwtAuthenticationToken.getToken().getTokenValue());
        }
        return Optional.empty();
    }

    static String requiredAccessToken() {
        return currentAccessToken()
                .orElseThrow(() -> new IllegalStateException("No access token is available in the security context"));
    }
}