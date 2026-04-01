package com.mahitotsu.arachne.samples.securedownstreamtools;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import io.arachne.strands.tool.ToolInvocationContext;
import io.arachne.strands.tool.annotation.StrandsTool;

@Service
public class CurrentOperatorCapabilitiesTool {

    private final CurrentOperatorCapabilitiesMapper mapper;

    public CurrentOperatorCapabilitiesTool(CurrentOperatorCapabilitiesMapper mapper) {
        this.mapper = mapper;
    }

    @StrandsTool(name = "current_operator_capabilities", description = "Return a safe capability view for the current operator.")
    public CurrentOperatorCapabilities currentOperatorCapabilities(ToolInvocationContext context) {
        OperatorPrincipal principal = currentPrincipal();
        CurrentOperatorCapabilities view = mapper.toView(principal);
        context.state().put("capabilitiesView", view);
        return view;
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