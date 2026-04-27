package com.mahitotsu.arachne.samples.delivery.customerservice.api;

import static com.mahitotsu.arachne.samples.delivery.customerservice.domain.CustomerTypes.*;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mahitotsu.arachne.samples.delivery.customerservice.application.CustomerAuthenticationService;

@RestController
@RequestMapping(path = "/api/auth", produces = MediaType.APPLICATION_JSON_VALUE)
public class CustomerAuthController {

    private final CustomerAuthenticationService authenticationService;

    CustomerAuthController(CustomerAuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/sign-in")
    AccessTokenResponse signIn(@RequestBody SignInRequest request) {
        return authenticationService.signIn(request);
    }
}