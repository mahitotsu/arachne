package com.mahitotsu.arachne.samples.domainseparation.security;

import org.springframework.stereotype.Component;

@Component
public class OperatorAuthorizationContextHolder {

    private final ThreadLocal<OperatorAuthorizationContext> currentContext = new ThreadLocal<>();

    public void setCurrent(OperatorAuthorizationContext authorizationContext) {
        currentContext.set(authorizationContext);
    }

    public OperatorAuthorizationContext current() {
        return currentContext.get();
    }

    public void restore(OperatorAuthorizationContext authorizationContext) {
        if (authorizationContext == null) {
            currentContext.remove();
            return;
        }
        currentContext.set(authorizationContext);
    }

    public void clear() {
        currentContext.remove();
    }
}