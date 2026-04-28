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
@Tag(name = "Customer Profile", description = "Authenticated customer profile endpoints.")
public class CustomerProfileController {

    private final CustomerAccountRepository repository;

    CustomerProfileController(CustomerAccountRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/me")
    @Operation(summary = "Read the current customer profile", description = "Returns the authenticated customer's profile derived from the bearer token subject.")
    CustomerProfileResponse me(Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new InvalidCredentialsException();
        }
        return repository.findProfile(jwt.getSubject())
                .orElseThrow(InvalidCredentialsException::new);
    }
}