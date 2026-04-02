package com.mahitotsu.arachne.samples.marketplace.workflowservice;

final class OperatorAuthorizationContextHolder {

    private final ThreadLocal<OperatorAuthorizationContext> current = new ThreadLocal<>();

    OperatorAuthorizationContext current() {
        return current.get();
    }

    void restore(OperatorAuthorizationContext context) {
        if (context == null) {
            current.remove();
            return;
        }
        current.set(context);
    }
}