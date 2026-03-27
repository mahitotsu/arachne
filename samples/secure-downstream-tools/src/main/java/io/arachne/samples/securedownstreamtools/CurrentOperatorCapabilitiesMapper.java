package io.arachne.samples.securedownstreamtools;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class CurrentOperatorCapabilitiesMapper {

    public CurrentOperatorCapabilities toView(OperatorPrincipal principal) {
        List<String> restricted = new ArrayList<>();
        if (!principal.permissions().contains("customer.export")) {
            restricted.add("customer.export");
        }
        if (!principal.permissions().contains("customer.delete")) {
            restricted.add("customer.delete");
        }
        return new CurrentOperatorCapabilities(
                principal.operatorId(),
                principal.tenantId(),
                principal.roles(),
                principal.permissions(),
                List.copyOf(restricted));
    }
}