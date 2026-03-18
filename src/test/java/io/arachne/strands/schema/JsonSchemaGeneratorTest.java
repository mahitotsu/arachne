package io.arachne.strands.schema;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

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

    record Forecast(String city, List<Integer> temperatures, boolean raining) {
    }
}