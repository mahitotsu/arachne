package com.mahitotsu.arachne.samples.domainseparation.domain;

public record ApprovalDecision(
        boolean approved,
        String approverId,
        String comment) {
}