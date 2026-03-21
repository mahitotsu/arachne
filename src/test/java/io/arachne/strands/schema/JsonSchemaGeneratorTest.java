package io.arachne.strands.schema;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import io.arachne.strands.tool.annotation.ToolParam;

class JsonSchemaGeneratorTest {

    private final JsonSchemaGenerator generator = new JsonSchemaGenerator();

    @Test
    void generatesObjectSchemaForRecord() {
        var schema = generator.schemaForType(Forecast.class);

        assertThat(schema.get("type").asText()).isEqualTo("object");
        assertThat(schema.get("properties").get("city").get("type").asText()).isEqualTo("string");
        assertThat(schema.get("properties").get("temperatures").get("type").asText()).isEqualTo("array");
        assertThat(schema.get("required")).extracting(node -> node.findValuesAsText(""))
                .isNotNull();
        assertThat(schema.get("required")).extracting(node -> node.toString()).asString().contains("city");
    }

    @Test
    void schemaForMethodUsesToolParamMetadataAndOptionalUnwrapping() throws Exception {
        Method method = ForecastTools.class.getDeclaredMethod("lookup", Optional.class, Map.class, String.class);

        var schema = generator.schemaForMethod(method);

        assertThat(schema.get("properties").get("forecast").get("type").asText()).isEqualTo("object");
        assertThat(schema.get("properties").get("tags").get("type").asText()).isEqualTo("object");
        assertThat(schema.get("properties").get("city_name").get("description").asText()).isEqualTo("City name");
        assertThat(schema.get("required").toString()).contains("tags", "city_name");
        assertThat(schema.get("required").toString()).doesNotContain("forecast");
    }

    @Test
    void recursiveBeanSchemaStopsAtVisitedTypes() {
        var schema = generator.schemaForType(RecursiveNode.class);

        assertThat(schema.get("type").asText()).isEqualTo("object");
        assertThat(schema.get("properties").get("name").get("type").asText()).isEqualTo("string");
        assertThat(schema.get("properties").get("next").get("type").asText()).isEqualTo("object");
    }

    record Forecast(String city, List<Integer> temperatures, boolean raining) {
    }

    static class ForecastTools {

        @SuppressWarnings("unused")
        void lookup(
                @ToolParam(name = "forecast") Optional<Forecast> forecast,
                @ToolParam Map<String, Integer> tags,
                @ToolParam(name = "city_name", description = "City name") String city) {
        }
    }

    static class RecursiveNode {

        public String getName() {
            return "root";
        }

        public RecursiveNode getNext() {
            return this;
        }
    }
}