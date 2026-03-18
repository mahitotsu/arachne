package io.arachne.strands.tool.annotation;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;

import io.arachne.strands.tool.Tool;
import io.arachne.strands.tool.ToolDefinitionException;
import io.arachne.strands.tool.ToolValidationException;
import jakarta.validation.constraints.NotBlank;

class AnnotationToolScannerTest {

    private final AnnotationToolScanner scanner = new AnnotationToolScanner();

    @Test
    void scansAnnotatedMethodsIntoTools() {
        List<Tool> tools = scanner.scan(List.of(new WeatherTools()));

        assertThat(tools).hasSize(1);
        assertThat(tools.getFirst().spec().name()).isEqualTo("lookup_weather");
        assertThat(tools.getFirst().spec().inputSchema().get("properties").get("city").get("description").asText())
                .isEqualTo("City name to query");
        assertThat(tools.getFirst().invoke(Map.of("city", "Tokyo", "unit", "C"))
                .content())
                .isEqualTo("Tokyo:C");
    }

    @Test
    void rejectsDuplicateToolNames() {
        assertThatThrownBy(() -> scanner.scan(List.of(new WeatherTools(), new DuplicateWeatherTools())))
                .isInstanceOf(ToolDefinitionException.class)
                .hasMessageContaining("Duplicate tool name");
    }

    @Test
    void capturesToolQualifiersFromAnnotation() {
        List<DiscoveredTool> tools = scanner.scanDiscoveredTools(List.of(new QualifiedTools()));

        assertThat(tools).hasSize(1);
        assertThat(tools.getFirst().qualifiers()).containsExactlyInAnyOrder("planner", "travel");
    }

    @Test
    void mergesSpringQualifierFromBeanClassAndMethod() {
        List<DiscoveredTool> tools = scanner.scanDiscoveredTools(List.of(new SpringQualifiedTools()));

        assertThat(tools).hasSize(1);
        assertThat(tools.getFirst().qualifiers()).containsExactlyInAnyOrder("planner", "bean-scope", "method-scope");
    }

    @Test
    void validatesToolArgumentsAtRuntime() {
        Tool tool = scanner.scan(List.of(new ValidatedTools())).getFirst();

        assertThatThrownBy(() -> tool.invoke(Map.of("city", "  ")))
                .isInstanceOf(ToolValidationException.class)
                .hasMessageContaining("city");
    }

    static class WeatherTools {

        @StrandsTool(name = "lookup_weather", description = "Look up weather information")
        public String weather(
                @ToolParam(description = "City name to query") String city,
                @ToolParam(required = false) String unit) {
            return city + ":" + unit;
        }
    }

    static class DuplicateWeatherTools {

        @StrandsTool(name = "lookup_weather")
        public String other(@ToolParam String city) {
            return city;
        }
    }

    static class QualifiedTools {

        @StrandsTool(name = "qualified_weather", qualifiers = {"planner", "travel"})
        public String weather(@ToolParam String city) {
            return city;
        }
    }

    @Qualifier("bean-scope")
    static class SpringQualifiedTools {

        @Qualifier("method-scope")
        @StrandsTool(name = "spring_qualified_weather", qualifiers = "planner")
        public String weather(@ToolParam String city) {
            return city;
        }
    }

    static class ValidatedTools {

        @StrandsTool(name = "validated_weather")
        public String weather(@ToolParam @NotBlank String city) {
            return city;
        }
    }
}