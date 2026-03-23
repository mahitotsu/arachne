package io.arachne.samples.domainseparation.security;

import java.util.List;

public record OperatorAuthorizationContext(
        String operatorId,
        String accessToken,
        List<String> permissions) {

    public OperatorAuthorizationContext {
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }
}