package com.mahitotsu.arachne.samples.delivery.menuservice.application;

import com.mahitotsu.arachne.samples.delivery.menuservice.domain.MenuTypes.MenuSuggestionRequest;

record MenuAgentUserPrompt(
        String query,
        String intentMode,
        String directItemHint,
        Integer partySize,
        java.math.BigDecimal budgetUpperBound,
        Integer childCount,
        String rationale,
        String refinement,
        String recentOrderSummary) {

    static MenuAgentUserPrompt from(MenuSuggestionRequest request) {
        var grounding = request.groundingContext();
        return new MenuAgentUserPrompt(
                request.query(),
                grounding == null ? null : grounding.intentMode(),
                grounding == null ? null : grounding.directItemHint(),
                grounding == null ? null : grounding.partySize(),
                grounding == null ? null : grounding.budgetUpperBound(),
                grounding == null ? null : grounding.childCount(),
                grounding == null ? null : grounding.rationale(),
                request.refinement(),
                request.recentOrderSummary());
    }

    String render() {
        StringBuilder builder = new StringBuilder("query=").append(query == null ? "" : query.trim());
        if (intentMode != null && !intentMode.isBlank()) {
            builder.append("\nintent_mode=").append(intentMode.trim());
        }
        if (directItemHint != null && !directItemHint.isBlank()) {
            builder.append("\ndirect_item_hint=").append(directItemHint.trim());
        }
        if (partySize != null) {
            builder.append("\nparty_size=").append(partySize);
        }
        if (budgetUpperBound != null) {
            builder.append("\nbudget_upper_bound=").append(budgetUpperBound.stripTrailingZeros().toPlainString());
        }
        if (childCount != null) {
            builder.append("\nchild_count=").append(childCount);
        }
        if (rationale != null && !rationale.isBlank()) {
            builder.append("\nrationale=").append(rationale.trim());
        }
        if (refinement != null && !refinement.isBlank()) {
            builder.append("\nrefinement=").append(refinement.trim());
        }
        if (recentOrderSummary != null && !recentOrderSummary.isBlank()) {
            builder.append("\nrecent_order=").append(recentOrderSummary.trim());
        }
        return builder.toString();
    }
}