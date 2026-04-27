package com.mahitotsu.arachne.samples.delivery.menuservice;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

final class SecurityAccessors {

    private SecurityAccessors() {
    }

    static String requiredAccessToken() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            return jwtAuthenticationToken.getToken().getTokenValue();
        }
        throw new IllegalStateException("No access token is available in the security context");
    }
}