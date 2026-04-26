package com.mahitotsu.arachne.samples.delivery.customerservice;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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