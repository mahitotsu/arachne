package com.mahitotsu.arachne.samples.delivery.paymentservice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@SpringBootApplication
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }

    @Bean
    SecurityFilterChain paymentSecurity(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
                .build();
    }

    @Bean
    ApplicationRunner registerPaymentService(
            RestClient.Builder restClientBuilder,
            @Value("${DELIVERY_REGISTRY_BASE_URL:}") String registryBaseUrl,
            @Value("${DELIVERY_PAYMENT_ENDPOINT:http://payment-service:8080}") String serviceEndpoint) {
        return args -> {
            if (registryBaseUrl.isBlank()) {
                return;
            }
            restClientBuilder.baseUrl(registryBaseUrl).build().post()
                    .uri("/registry/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "serviceName", "payment-service",
                            "endpoint", serviceEndpoint,
                            "capability", "支払い手段の提案、支払い準備、請求確定を扱う。",
                            "agentName", "payment-service",
                            "systemPrompt", "決定的な支払い手段選択と請求処理を提供する。",
                            "skills", List.of(Map.of("name", "payment-profile", "content", "支払いプロファイルの選択と請求確定")),
                            "requestMethod", "POST",
                            "requestPath", "/internal/payment/prepare",
                            "healthEndpoint", serviceEndpoint + "/actuator/health",
                            "status", "AVAILABLE"))
                    .retrieve()
                    .toBodilessEntity();
        };
    }
}

@RestController
@RequestMapping(path = "/internal/payment", produces = MediaType.APPLICATION_JSON_VALUE)
class PaymentController {

    private final PaymentApplicationService applicationService;

    PaymentController(PaymentApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/prepare")
    PaymentPrepareResponse prepare(@RequestBody PaymentPrepareRequest request) {
        return applicationService.prepare(request);
    }
}

@Service
class PaymentApplicationService {

    private final PaymentProfileRepository repository;

    PaymentApplicationService(PaymentProfileRepository repository) {
        this.repository = repository;
    }

    PaymentPrepareResponse prepare(PaymentPrepareRequest request) {
        PaymentProfile profile = repository.profileFor(request.message());
        boolean charged = request.confirmRequested();
        String authorizationId = charged ? "pay-" + UUID.randomUUID().toString().substring(0, 8) : null;
        String paymentStatus = charged ? "CHARGED" : "READY";
        String summary = repository.summarize(profile, charged);
        return new PaymentPrepareResponse(
                "payment-service",
                "payment-service",
                charged ? "支払い処理が完了しました" : "支払方法の準備が完了しました",
                summary,
                profile.methodLabel(),
                request.total().setScale(2, RoundingMode.HALF_UP),
                paymentStatus,
                charged,
                authorizationId);
    }
}

@Component
class PaymentProfileRepository {

    PaymentProfile profileFor(String message) {
        if (message != null && (message.toLowerCase().contains("apple") || message.contains("アップル"))) {
            return new PaymentProfile("apple-pay", "Apple Pay", "メインの iPhone のウォレットに登録済みです。");
        }
        if (message != null && message.contains("現金")) {
            return new PaymentProfile("cash", "代金引換", "現金払いは処理に少し時間がかかりますが、ご利用いただけます。");
        }
        return new PaymentProfile("card-default", "登録済み Visa（下4桁: 2048）", "デフォルトカードはワンタップ確認済みです。");
    }

    String summarize(PaymentProfile profile, boolean charged) {
        if (charged) {
            return profile.methodLabel() + " で請求を完了しました。" + profile.note();
        }
        return profile.methodLabel() + " を選択しました。" + profile.note();
    }
}

record PaymentPrepareRequest(String sessionId, String message, BigDecimal total, boolean confirmRequested) {}

record PaymentPrepareResponse(
        String service,
        String agent,
        String headline,
        String summary,
        String selectedMethod,
        BigDecimal total,
        String paymentStatus,
        boolean charged,
        String authorizationId) {}

record PaymentProfile(String methodCode, String methodLabel, String note) {}