package com.mahitotsu.arachne.samples.delivery.customerservice.api;

import static com.mahitotsu.arachne.samples.delivery.customerservice.domain.CustomerTypes.*;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mahitotsu.arachne.samples.delivery.customerservice.domain.InvalidCredentialsException;
import com.mahitotsu.arachne.samples.delivery.customerservice.infrastructure.CustomerAccountRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(path = "/api/customers", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Customer Profile", description = "認証済み customer のプロフィール取得エンドポイントです。")
public class CustomerProfileController {

    private final CustomerAccountRepository repository;

    CustomerProfileController(CustomerAccountRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/me")
    @Operation(summary = "Read the current customer profile", description = "Bearer token の subject から導出した認証済み customer のプロフィールを返します。")
    CustomerProfileResponse me(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new InvalidCredentialsException();
        }
        return repository.findProfile(jwt.getSubject())
                .orElseThrow(InvalidCredentialsException::new);
    }
}