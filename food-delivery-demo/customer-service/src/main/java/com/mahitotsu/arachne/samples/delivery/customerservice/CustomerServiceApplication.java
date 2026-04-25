package com.mahitotsu.arachne.samples.delivery.customerservice;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

@SpringBootApplication
public class CustomerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomerServiceApplication.class, args);
    }

    @Bean
    SecurityFilterChain customerSecurity(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/info", "/oauth2/jwks", "/api/auth/sign-in").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder)))
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    JwtEncoder jwtEncoder(SigningKeyManager signingKeyManager) {
        JWKSource<SecurityContext> source = new ImmutableJWKSet<>(new JWKSet(signingKeyManager.rsaKey()));
        return new NimbusJwtEncoder(source);
    }

    @Bean
    JwtDecoder jwtDecoder(SigningKeyManager signingKeyManager) {
        return NimbusJwtDecoder.withPublicKey(signingKeyManager.publicKey()).build();
    }

    @Bean
    ApplicationRunner demoCustomers(CustomerAccountRepository repository, PasswordEncoder passwordEncoder) {
        return args -> repository.seedDemoAccounts(passwordEncoder);
    }
}

@RestController
@RequestMapping(path = "/api/auth", produces = MediaType.APPLICATION_JSON_VALUE)
class CustomerAuthController {

    private final CustomerAuthenticationService authenticationService;

    CustomerAuthController(CustomerAuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/sign-in")
    AccessTokenResponse signIn(@RequestBody SignInRequest request) {
        return authenticationService.signIn(request);
    }
}

@RestController
class CustomerJwksController {

    private final SigningKeyManager signingKeyManager;

    CustomerJwksController(SigningKeyManager signingKeyManager) {
        this.signingKeyManager = signingKeyManager;
    }

    @GetMapping(path = "/oauth2/jwks", produces = MediaType.APPLICATION_JSON_VALUE)
    Map<String, Object> jwks() {
        return signingKeyManager.publicJwkSet().toJSONObject();
    }
}

@RestController
@RequestMapping(path = "/api/customers", produces = MediaType.APPLICATION_JSON_VALUE)
class CustomerProfileController {

    private final CustomerAccountRepository repository;

    CustomerProfileController(CustomerAccountRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/me")
    CustomerProfileResponse me(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new InvalidCredentialsException();
        }
        return repository.findProfile(jwt.getSubject())
                .orElseThrow(InvalidCredentialsException::new);
    }
}

@Service
class CustomerAuthenticationService {

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
            @Value("${delivery.auth.issuer}") String issuer) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.signingKeyManager = signingKeyManager;
        this.issuer = issuer;
    }

    AccessTokenResponse signIn(SignInRequest request) {
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

@Component
class CustomerAccountRepository {

    private final JdbcClient jdbcClient;

    CustomerAccountRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    Optional<CustomerAccount> findByLoginId(String loginId) {
        return jdbcClient.sql("""
                select customer_id, login_id, password_hash, display_name, default_locale, scopes
                from customer_accounts
                where login_id = :loginId
                """)
                .param("loginId", loginId)
                .query(this::mapAccount)
                .optional();
    }

    Optional<CustomerProfileResponse> findProfile(String customerId) {
        return jdbcClient.sql("""
                select customer_id, login_id, display_name, default_locale, scopes
                from customer_accounts
                where customer_id = :customerId
                """)
                .param("customerId", customerId)
                .query((rs, rowNum) -> new CustomerProfileResponse(
                        rs.getString("customer_id"),
                        rs.getString("login_id"),
                        rs.getString("display_name"),
                        rs.getString("default_locale"),
                        parseScopes(rs.getString("scopes"))))
                .optional();
    }

    void seedDemoAccounts(PasswordEncoder passwordEncoder) {
        insertAccount("cust-demo-001", "demo", "Aoi Sato", "ja-JP", "orders.read orders.write profile.read",
                passwordEncoder.encode("demo-pass"));
        insertAccount("cust-demo-002", "family", "ファミリーアカウント", "ja-JP", "orders.read orders.write profile.read",
                passwordEncoder.encode("family-pass"));
        insertAccount("cust-solo-001", "solo", "Hina Nakamura", "ja-JP", "orders.read orders.write profile.read",
                passwordEncoder.encode("solo-pass"));
        insertAccount("cust-corp-001", "corporate", "法人アカウント", "ja-JP", "orders.read orders.write profile.read",
                passwordEncoder.encode("corp-pass"));
    }

    private void insertAccount(
            String customerId,
            String loginId,
            String displayName,
            String defaultLocale,
            String scopes,
            String passwordHash) {
        jdbcClient.sql("""
                insert into customer_accounts (customer_id, login_id, password_hash, display_name, default_locale, scopes)
                values (:customerId, :loginId, :passwordHash, :displayName, :defaultLocale, :scopes)
                on conflict do nothing
                """)
                .param("customerId", customerId)
                .param("loginId", loginId)
                .param("passwordHash", passwordHash)
                .param("displayName", displayName)
                .param("defaultLocale", defaultLocale)
                .param("scopes", scopes)
                .update();
    }

    private CustomerAccount mapAccount(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new CustomerAccount(
                rs.getString("customer_id"),
                rs.getString("login_id"),
                rs.getString("password_hash"),
                rs.getString("display_name"),
                rs.getString("default_locale"),
                rs.getString("scopes"));
    }

    private static List<String> parseScopes(String scopes) {
        return java.util.Arrays.stream(scopes.split("\\s+"))
                .filter(scope -> !scope.isBlank())
                .toList();
    }
}

@Component
class SigningKeyManager {

    private final RSAKey rsaKey;
    private final RSAPublicKey publicKey;

    SigningKeyManager() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            KeyPair pair = generator.generateKeyPair();
            this.publicKey = (RSAPublicKey) pair.getPublic();
            RSAPrivateKey privateKey = (RSAPrivateKey) pair.getPrivate();
            this.rsaKey = new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyID("customer-key-" + UUID.randomUUID().toString().substring(0, 8))
                    .build();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate the customer-service RSA key pair", ex);
        }
    }

    RSAKey rsaKey() {
        return rsaKey;
    }

    RSAPublicKey publicKey() {
        return publicKey;
    }

    String keyId() {
        return rsaKey.getKeyID();
    }

    JWKSet publicJwkSet() {
        return new JWKSet(rsaKey.toPublicJWK());
    }
}

@ResponseStatus(HttpStatus.UNAUTHORIZED)
class InvalidCredentialsException extends RuntimeException {

    InvalidCredentialsException() {
        super("Invalid login ID or password");
    }
}

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