package io.arachne.samples.securedownstreamtools;

import java.util.List;

public record OperatorPrincipal(
        String operatorId,
        String tenantId,
        String bearerToken,
        List<String> roles,
        List<String> permissions) {

    public OperatorPrincipal {
        roles = roles == null ? List.of() : List.copyOf(roles);
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }
}