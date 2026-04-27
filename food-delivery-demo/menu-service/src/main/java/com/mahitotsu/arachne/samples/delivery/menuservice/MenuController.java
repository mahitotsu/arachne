package com.mahitotsu.arachne.samples.delivery.menuservice;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(path = "/internal/menu", produces = MediaType.APPLICATION_JSON_VALUE)
class MenuController {

    private final MenuApplicationService applicationService;

    MenuController(MenuApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/suggest")
    MenuSuggestionResponse suggest(@RequestBody MenuSuggestionRequest request) {
        return applicationService.suggest(request);
    }

    @PostMapping("/substitutes")
    MenuSubstitutionResponse substitutes(@RequestBody MenuSubstitutionRequest request) {
        return applicationService.suggestSubstitutes(request);
    }
}