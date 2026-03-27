package io.arachne.samples.securedownstreamtools;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import io.arachne.strands.tool.ToolInvocationContext;
import io.arachne.strands.tool.annotation.StrandsTool;
import io.arachne.strands.tool.annotation.ToolParam;

@Service
public class CustomerProfileTool {

    private final CustomerProfileClient customerProfileClient;

    public CustomerProfileTool(CustomerProfileClient customerProfileClient) {
        this.customerProfileClient = customerProfileClient;
    }

    @StrandsTool(name = "fetch_customer_profile", description = "Fetch a customer profile summary through a downstream API.")
    public CustomerProfileSummary fetchCustomerProfile(
            @ToolParam(description = "Customer id to fetch") String customerId,
            ToolInvocationContext context) {
        OperatorPrincipal principal = currentPrincipal();
        CustomerProfileSummary summary = customerProfileClient.fetchProfile(customerId, principal.bearerToken());
        context.state().put("profileSummary", summary);
        return summary;
    }

    private OperatorPrincipal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof UsernamePasswordAuthenticationToken token)
                || !(token.getPrincipal() instanceof OperatorPrincipal principal)) {
            throw new IllegalStateException("Expected OperatorPrincipal in SecurityContext");
        }
        return principal;
    }
}