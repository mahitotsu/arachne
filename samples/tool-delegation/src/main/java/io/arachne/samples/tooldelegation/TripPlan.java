package io.arachne.samples.tooldelegation;

import jakarta.validation.constraints.NotBlank;

public record TripPlan(
        @NotBlank String city,
        @NotBlank String forecast,
        @NotBlank String advice
) {
}