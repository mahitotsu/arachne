package io.arachne.strands.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import com.fasterxml.jackson.databind.ObjectMapper;

class ArachneTemplateRendererTest {

    private final ArachneTemplateRenderer renderer = new ArachneTemplateRenderer(
            new PathMatchingResourcePatternResolver(),
            new ObjectMapper());

    @Test
    void rendersTopLevelStructuredOutputFieldsFromClasspathTemplate() {
        TripPlan plan = new TripPlan("Tokyo", "Sunny", "Bring water.");

        String rendered = renderer.render("classpath:/templates/trip-plan.txt", plan);

        assertThat(rendered).isEqualTo("City: Tokyo\nForecast: Sunny\nAdvice: Bring water.");
    }

    @Test
    void rendersNestedValuesAsCompactJsonStrings() {
        NestedSummary summary = new NestedSummary("ok", new Meta("Tokyo", false));

        String rendered = renderer.render("classpath:/templates/nested-summary.txt", summary);

        assertThat(rendered).isEqualTo("Answer: ok\nMeta: {\"city\":\"Tokyo\",\"rain\":false}");
    }

    @Test
    void raisesDedicatedExceptionWhenTemplateIsMissing() {
        assertThatThrownBy(() -> renderer.render("classpath:/templates/does-not-exist.txt", new TripPlan("x", "y", "z")))
                .isInstanceOf(ArachneTemplateNotFoundException.class)
                .hasMessage("Template resource not found: classpath:/templates/does-not-exist.txt");
    }

    @Test
    void wrapsMissingTemplateVariablesAsRenderFailures() {
        TripPlan plan = new TripPlan("Tokyo", "Sunny", "Bring water.");

        assertThatThrownBy(() -> renderer.render("classpath:/templates/missing-field.txt", plan))
                .isInstanceOf(ArachneTemplateRenderException.class)
                .hasMessageContaining("Template rendering failed for classpath:/templates/missing-field.txt")
                .hasMessageContaining("Missing template variable: summary");
    }

    record TripPlan(String city, String forecast, String advice) {
    }

    record NestedSummary(String answer, Meta meta) {
    }

    record Meta(String city, boolean rain) {
    }
}