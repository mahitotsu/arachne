package com.mahitotsu.arachne.samples.delivery.customerservice.domain;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public final class CustomerTypes {

    private CustomerTypes() {
    }

    @Schema(description = "Sign-in request for the demo customer account service.")
    public record SignInRequest(
            @Schema(description = "Demo login id.", example = "demo") String loginId,
            @Schema(description = "Password for the supplied login id.", example = "demo-pass") String password) {
    }

    @Schema(description = "Bearer token response returned after successful sign-in.")
    public record AccessTokenResponse(
            String tokenType,
            String accessToken,
            long expiresIn,
            String subject,
            String displayName,
            String locale,
            List<String> scopes) {
    }

        @Schema(description = "Authenticated customer profile response.")
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