package com.mahitotsu.arachne.samples.domainseparation.domain;

public record AccountWorkflowApproval(
        boolean required,
        String status,
        Boolean approved,
        String approverId,
        String comment) {
}