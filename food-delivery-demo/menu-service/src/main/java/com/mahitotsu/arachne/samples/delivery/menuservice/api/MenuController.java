package com.mahitotsu.arachne.samples.delivery.menuservice.api;

import static com.mahitotsu.arachne.samples.delivery.menuservice.domain.MenuTypes.*;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mahitotsu.arachne.samples.delivery.menuservice.application.MenuApplicationService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(path = "/internal/menu", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Menu Service", description = "Menu-service endpoints for suggestions and substitution candidates.")
public class MenuController {

    private final MenuApplicationService applicationService;

    MenuController(MenuApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/suggest")
    @Operation(
            summary = "Suggest menu candidates",
            description = "Accepts the current customer order intent and returns menu candidates with an agent summary and ETA.",
            extensions = @Extension(name = "x-ai-prompt-contract", properties = {
                    @ExtensionProperty(name = "agent", value = "menu-agent"),
                    @ExtensionProperty(name = "contract", value = "{\"requiredInputs\":[{\"field\":\"query\",\"meaning\":\"Primary order intent including preferences,人数, budget, and desired items.\"}],\"optionalInputs\":[{\"field\":\"refinement\",\"meaning\":\"Additional constraints for a follow-up suggestion.\"},{\"field\":\"recentOrderSummary\",\"meaning\":\"Repeat-order context injected by the caller when available.\"},{\"field\":\"sessionId\",\"meaning\":\"Correlation id for the suggestion workflow.\"}]}", parseValue = true)
            }))
    MenuSuggestionResponse suggest(@RequestBody MenuSuggestionRequest request) {
        return applicationService.suggest(request);
    }

    @PostMapping("/substitutes")
    @Operation(
            summary = "Suggest substitute menu candidates",
            description = "Accepts an unavailable item and the original customer intent, then returns fallback candidates from menu-service.",
            extensions = @Extension(name = "x-ai-prompt-contract", properties = {
                    @ExtensionProperty(name = "agent", value = "menu-agent"),
                    @ExtensionProperty(name = "contract", value = "{\"requiredInputs\":[{\"field\":\"unavailableItemId\",\"meaning\":\"Original menu item that became unavailable.\"},{\"field\":\"message\",\"meaning\":\"Original customer intent used to preserve the substitution context.\"}],\"optionalInputs\":[{\"field\":\"sessionId\",\"meaning\":\"Correlation id for the substitution workflow.\"}]}", parseValue = true)
            }))
    MenuSubstitutionResponse substitutes(@RequestBody MenuSubstitutionRequest request) {
        return applicationService.suggestSubstitutes(request);
    }
}