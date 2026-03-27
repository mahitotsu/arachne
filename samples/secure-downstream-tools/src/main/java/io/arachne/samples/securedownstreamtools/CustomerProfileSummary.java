package io.arachne.samples.securedownstreamtools;

import java.util.List;

public record CustomerProfileSummary(
        String customerId,
        String displayName,
        String status,
        List<String> accountFlags) {

    public CustomerProfileSummary {
        accountFlags = accountFlags == null ? List.of() : List.copyOf(accountFlags);
    }
}