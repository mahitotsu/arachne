package com.mahitotsu.arachne.samples.securedownstreamtools;

import java.util.List;

public record CustomerProfilePayload(
        String customerId,
        String displayName,
        String status,
        List<String> accountFlags) {

    public CustomerProfilePayload {
        accountFlags = accountFlags == null ? List.of() : List.copyOf(accountFlags);
    }
}