package com.mahitotsu.arachne.samples.delivery.customerservice.api;

import static com.mahitotsu.arachne.samples.delivery.customerservice.domain.CustomerTypes.*;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mahitotsu.arachne.samples.delivery.customerservice.application.CustomerAuthenticationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(path = "/api/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Customer Authentication", description = "Customer-service authentication endpoints used by the demo clients.")
public class CustomerAuthController {

    private final CustomerAuthenticationService authenticationService;

    CustomerAuthController(CustomerAuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @PostMapping("/sign-in")
    @Operation(summary = "Sign in with demo credentials", description = "Returns a bearer token and profile summary for the supplied login credentials.")
    AccessTokenResponse signIn(@RequestBody SignInRequest request) {
        return authenticationService.signIn(request);
    }
}