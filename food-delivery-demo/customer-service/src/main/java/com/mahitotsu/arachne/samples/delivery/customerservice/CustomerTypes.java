package com.mahitotsu.arachne.samples.delivery.customerservice;

import java.util.List;

record SignInRequest(String loginId, String password) {
}

record AccessTokenResponse(
        String tokenType,
        String accessToken,
        long expiresIn,
        String subject,
        String displayName,
        String locale,
        List<String> scopes) {
}

record CustomerProfileResponse(
        String customerId,
        String loginId,
        String displayName,
        String locale,
        List<String> scopes) {
}

record CustomerAccount(
        String customerId,
        String loginId,
        String passwordHash,
        String displayName,
        String defaultLocale,
        String scopes) {
}