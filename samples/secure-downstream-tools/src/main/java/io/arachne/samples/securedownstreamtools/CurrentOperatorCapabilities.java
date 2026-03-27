package io.arachne.samples.securedownstreamtools;

import java.util.List;

public record CurrentOperatorCapabilities(
        String operatorId,
        String tenantId,
        List<String> roles,
        List<String> permissions,
        List<String> restrictedActions) {

    public CurrentOperatorCapabilities {
        roles = roles == null ? List.of() : List.copyOf(roles);
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
        restrictedActions = restrictedActions == null ? List.of() : List.copyOf(restrictedActions);
    }
}