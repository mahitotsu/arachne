package com.mahitotsu.arachne.samples.delivery.customerservice.domain;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public final class CustomerTypes {

    private CustomerTypes() {
    }

    @Schema(description = "デモ用 customer-service のサインイン要求です。")
    public record SignInRequest(
            @Schema(description = "デモ用ログイン ID。", example = "demo") String loginId,
            @Schema(description = "指定したログイン ID のパスワード。", example = "demo-pass") String password) {
    }

    @Schema(description = "サインイン成功時に返す Bearer トークン応答です。")
    public record AccessTokenResponse(
            String tokenType,
            String accessToken,
            long expiresIn,
            String subject,
            String displayName,
            String locale,
            List<String> scopes) {
    }

        @Schema(description = "認証済み customer のプロフィール応答です。")
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