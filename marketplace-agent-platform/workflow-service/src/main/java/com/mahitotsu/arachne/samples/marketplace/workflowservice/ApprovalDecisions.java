package com.mahitotsu.arachne.samples.marketplace.workflowservice;

final class ApprovalDecisions {

    private ApprovalDecisions() {
    }

    static boolean isApproved(String decision) {
        if (decision == null) {
            return false;
        }
        return "APPROVE".equalsIgnoreCase(decision) || "APPROVED".equalsIgnoreCase(decision);
    }
}