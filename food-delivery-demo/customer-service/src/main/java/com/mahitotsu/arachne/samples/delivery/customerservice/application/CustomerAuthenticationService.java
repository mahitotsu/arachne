package com.mahitotsu.arachne.samples.delivery.customerservice.application;

import static com.mahitotsu.arachne.samples.delivery.customerservice.domain.CustomerTypes.*;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.mahitotsu.arachne.samples.delivery.customerservice.domain.InvalidCredentialsException;
import com.mahitotsu.arachne.samples.delivery.customerservice.config.CustomerServiceProperties;
import com.mahitotsu.arachne.samples.delivery.customerservice.infrastructure.CustomerAccountRepository;
import com.mahitotsu.arachne.samples.delivery.customerservice.infrastructure.SigningKeyManager;

@Service
public class CustomerAuthenticationService {

    private static final long ACCESS_TOKEN_TTL_SECONDS = 3600;

    private final CustomerAccountRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final SigningKeyManager signingKeyManager;
    private final String issuer;

    CustomerAuthenticationService(
            CustomerAccountRepository repository,
            PasswordEncoder passwordEncoder,
            JwtEncoder jwtEncoder,
            SigningKeyManager signingKeyManager,
            CustomerServiceProperties properties) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.signingKeyManager = signingKeyManager;
        this.issuer = properties.getAuth().getIssuer();
    }

    public AccessTokenResponse signIn(SignInRequest request) {
        String loginId = safeTrim(request.loginId());
        String password = Objects.requireNonNullElse(request.password(), "");
        CustomerAccount account = repository.findByLoginId(loginId)
                .orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(password, account.passwordHash())) {
            throw new InvalidCredentialsException();
        }

        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(ACCESS_TOKEN_TTL_SECONDS);
        List<String> scopes = parseScopes(account.scopes());

        JwsHeader headers = JwsHeader.with(SignatureAlgorithm.RS256)
                .type("JWT")
                .keyId(signingKeyManager.keyId())
                .build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(account.customerId())
                .audience(List.of("food-delivery-demo"))
                .claim("scope", String.join(" ", scopes))
                .claim("preferred_username", account.loginId())
                .claim("name", account.displayName())
                .claim("locale", account.defaultLocale())
                .build();
        String accessToken = jwtEncoder.encode(JwtEncoderParameters.from(headers, claims)).getTokenValue();

        return new AccessTokenResponse(
                "Bearer",
                accessToken,
                ACCESS_TOKEN_TTL_SECONDS,
                account.customerId(),
                account.displayName(),
                account.defaultLocale(),
                scopes);
    }

    private static String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private static List<String> parseScopes(String scopes) {
        return java.util.Arrays.stream(scopes.split("\\s+"))
                .filter(scope -> !scope.isBlank())
                .toList();
    }
}