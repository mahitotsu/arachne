package com.mahitotsu.arachne.samples.securedownstreamtools;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.mahitotsu.arachne.strands.agent.Agent;
import com.mahitotsu.arachne.strands.spring.AgentFactory;
import com.mahitotsu.arachne.strands.tool.ToolExecutionMode;

@Component
public class SecureDownstreamToolsRunner implements ApplicationRunner {

    private final AgentFactory agentFactory;
    private final DownstreamAuditLog downstreamAuditLog;

    public SecureDownstreamToolsRunner(AgentFactory agentFactory, DownstreamAuditLog downstreamAuditLog) {
        this.agentFactory = agentFactory;
        this.downstreamAuditLog = downstreamAuditLog;
    }

    @Override
    public void run(ApplicationArguments args) {
        Agent agent = agentFactory.builder()
                .toolExecutionMode(ToolExecutionMode.PARALLEL)
                .build();
        String prompt = "Check what the current operator can do and fetch customer cust-42.";

        downstreamAuditLog.reset();
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContextHolder.setContext(securityContext());
        try {
            String reply = agent.run(prompt).text();

            System.out.println("Arachne secure downstream tools sample");
            System.out.println("request> " + prompt);
            System.out.println("final.reply> " + reply);
            System.out.println("capabilities.view> " + agent.getState().get("capabilitiesView"));
            System.out.println("profile.summary> " + agent.getState().get("profileSummary"));
            System.out.println("downstream.requests> " + downstreamAuditLog.requestedCustomerIds());
            System.out.println("downstream.authHeaders> " + downstreamAuditLog.observedAuthorizationHeaders());
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }

    private SecurityContext securityContext() {
        OperatorPrincipal principal = new OperatorPrincipal(
                "operator-42",
                "tenant-north",
                "token-demo-42",
                List.of("support-operator"),
                List.of("customer.read", "customer.profile.read"));

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(
                        new SimpleGrantedAuthority("ROLE_SUPPORT_OPERATOR"),
                        new SimpleGrantedAuthority("PERM_customer.read"),
                        new SimpleGrantedAuthority("PERM_customer.profile.read")));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        return context;
    }
}