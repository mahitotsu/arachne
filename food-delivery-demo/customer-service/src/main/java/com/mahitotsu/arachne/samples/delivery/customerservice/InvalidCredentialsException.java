package com.mahitotsu.arachne.samples.delivery.customerservice;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.UNAUTHORIZED)
class InvalidCredentialsException extends RuntimeException {

    InvalidCredentialsException() {
        super("Invalid login ID or password");
    }
}