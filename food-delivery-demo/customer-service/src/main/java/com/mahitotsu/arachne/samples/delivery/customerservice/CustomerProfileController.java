package com.mahitotsu.arachne.samples.delivery.customerservice;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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