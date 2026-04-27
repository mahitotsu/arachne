package com.mahitotsu.arachne.samples.delivery.customerservice.domain;

import java.util.List;

public final class CustomerTypes {

    private CustomerTypes() {
    }

    public record SignInRequest(String loginId, String password) {
    }

    public record AccessTokenResponse(
            String tokenType,
            String accessToken,
            long expiresIn,
            String subject,
            String displayName,
            String locale,
            List<String> scopes) {
    }

    public record CustomerProfileResponse(
            String customerId,
            String loginId,
            String displayName,
            String locale,
            List<String> scopes) {
    }

    public record CustomerAccount(
            String customerId,
            String loginId,
            String passwordHash,
            String displayName,
            String defaultLocale,
            String scopes) {
    }
}