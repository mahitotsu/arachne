package io.arachne.samples.phase2tools;

import jakarta.validation.constraints.NotBlank;

public record TripPlan(
        @NotBlank String city,
        @NotBlank String forecast,
        @NotBlank String advice
) {
}