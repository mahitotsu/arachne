package com.mahitotsu.arachne.samples.delivery.customerservice;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.mahitotsu.arachne.samples.delivery.customerservice.domain.CustomerTypes.AccessTokenResponse;
import com.mahitotsu.arachne.samples.delivery.customerservice.domain.CustomerTypes.CustomerProfileResponse;
import com.mahitotsu.arachne.samples.delivery.customerservice.domain.CustomerTypes.SignInRequest;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;

@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:customers;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.datasource.username=sa",
                "spring.datasource.password="
        })
class CustomerServiceApiTest {

    @Autowired
    private TestRestTemplate restTemplate;

        @Test
        void exposesOpenApiContractWithoutAuthentication() {
                ResponseEntity<String> response = restTemplate.getForEntity("/v3/api-docs", String.class);

                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(response.getBody())
                                .contains("/api/auth/sign-in")
                                .contains("/api/customers/me")
                                .contains("/oauth2/jwks")
                                .contains("Sign in with demo credentials");
        }

    @Test
    void signsInWithDemoCredentialsAndExposesAProfileEndpoint() throws Exception {
        ResponseEntity<AccessTokenResponse> signIn = restTemplate.postForEntity(
                "/api/auth/sign-in",
                new SignInRequest("demo", "demo-pass"),
                AccessTokenResponse.class);

        assertThat(signIn.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(signIn.getBody()).isNotNull();
        assertThat(signIn.getBody().subject()).isEqualTo("cust-demo-001");
        assertThat(signIn.getBody().displayName()).isEqualTo("Aoi Sato");
        assertThat(signIn.getBody().scopes()).contains("orders.read", "orders.write", "profile.read");

        String jwks = restTemplate.getForObject("/oauth2/jwks", String.class);
        assertThat(jwks).isNotBlank();

        JWKSet jwkSet = JWKSet.parse(jwks);
        RSAKey publicKey = (RSAKey) jwkSet.getKeys().getFirst();
        SignedJWT jwt = SignedJWT.parse(signIn.getBody().accessToken());

        assertThat(jwt.verify(new RSASSAVerifier(publicKey.toRSAPublicKey()))).isTrue();
        assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo("cust-demo-001");
        assertThat(jwt.getJWTClaimsSet().getStringClaim("preferred_username")).isEqualTo("demo");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(signIn.getBody().accessToken());
        ResponseEntity<CustomerProfileResponse> profile = restTemplate.exchange(
                "/api/customers/me",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                CustomerProfileResponse.class);

        assertThat(profile.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(profile.getBody()).isNotNull();
        assertThat(profile.getBody().customerId()).isEqualTo("cust-demo-001");
        assertThat(profile.getBody().loginId()).isEqualTo("demo");
    }

    @Test
    void rejectsInvalidCredentials() {
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/auth/sign-in",
                new SignInRequest("demo", "wrong-pass"),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void rejectsUnauthenticatedProfileRequests() {
        ResponseEntity<String> response = restTemplate.getForEntity("/api/customers/me", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}