package com.mahitotsu.arachne.samples.delivery.menuservice.application;

import com.mahitotsu.arachne.samples.delivery.menuservice.domain.MenuTypes.MenuSuggestionRequest;

record MenuAgentUserPrompt(String query, String refinement, String recentOrderSummary) {

    static MenuAgentUserPrompt from(MenuSuggestionRequest request) {
        return new MenuAgentUserPrompt(request.query(), request.refinement(), request.recentOrderSummary());
    }

    String render() {
        StringBuilder builder = new StringBuilder("query=").append(query == null ? "" : query.trim());
        if (refinement != null && !refinement.isBlank()) {
            builder.append("\nrefinement=").append(refinement.trim());
        }
        if (recentOrderSummary != null && !recentOrderSummary.isBlank()) {
            builder.append("\nrecent_order=").append(recentOrderSummary.trim());
        }
        return builder.toString();
    }
}