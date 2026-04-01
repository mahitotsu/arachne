package com.mahitotsu.arachne.samples.domainseparation.workflow;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DomainSeparationWorkflowState {

    public static final String REQUEST = "domainSeparation.workflow.request";
    public static final String STATUS = "domainSeparation.workflow.status";
    public static final String APPROVAL = "domainSeparation.workflow.approval";
    public static final String PREPARATION = "domainSeparation.workflow.preparation";
    public static final String EXECUTION = "domainSeparation.workflow.execution";

    private DomainSeparationWorkflowState() {
    }

    public static Map<String, String> parsePrompt(String prompt) {
        Map<String, String> values = new LinkedHashMap<>();
        if (prompt == null || prompt.isBlank()) {
            return values;
        }
        for (String line : prompt.split("\\R")) {
            int separator = line.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            values.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
        }
        return values;
    }
}