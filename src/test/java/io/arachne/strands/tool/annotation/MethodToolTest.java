package io.arachne.strands.tool.annotation;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

import io.arachne.strands.schema.JsonSchemaGenerator;
import io.arachne.strands.tool.ToolDefinitionException;

class MethodToolTest {

    private final JsonSchemaGenerator schemaGenerator = new JsonSchemaGenerator();

    @Test
    void invokeBindsOptionalArgumentsAndUnwrapsOptionalReturnValues() throws Exception {
        Method method = OptionalTools.class.getDeclaredMethod("lookup", Optional.class);
        MethodTool tool = new MethodTool(new OptionalTools(), method, schemaGenerator);

        assertThat(tool.invoke(Map.of("count", 3)).content()).isEqualTo("count=3");
        assertThat(tool.invoke(Map.of()).content()).isEqualTo("count=none");
    }

    @Test
    void invokeRejectsMissingPrimitiveArguments() throws Exception {
        Method method = PrimitiveTools.class.getDeclaredMethod("lookup", int.class);
        MethodTool tool = new MethodTool(new PrimitiveTools(), method, schemaGenerator);

        assertThatThrownBy(() -> tool.invoke(Map.of()))
                .isInstanceOf(ToolDefinitionException.class)
                .hasMessageContaining("Missing required primitive tool parameter: count");
    }

    @Test
    void invokeWrapsCheckedExceptions() throws Exception {
        Method method = CheckedExceptionTools.class.getDeclaredMethod("lookup", String.class);
        MethodTool tool = new MethodTool(new CheckedExceptionTools(), method, schemaGenerator);

        assertThatThrownBy(() -> tool.invoke(Map.of("city", "Tokyo")))
                .isInstanceOf(ToolDefinitionException.class)
                .hasMessageContaining("Tool method threw checked exception")
                .hasCauseInstanceOf(IOException.class);
    }

    static class OptionalTools {

        @StrandsTool(name = "optional_lookup")
        public Optional<String> lookup(@ToolParam(name = "count") Optional<Integer> count) {
            return Optional.of(count.map(value -> "count=" + value).orElse("count=none"));
        }
    }

    static class PrimitiveTools {

        @StrandsTool(name = "primitive_lookup")
        public String lookup(@ToolParam(name = "count") int count) {
            return "count=" + count;
        }
    }

    static class CheckedExceptionTools {

        @StrandsTool(name = "checked_lookup")
        public String lookup(@ToolParam(name = "city") String city) throws IOException {
            throw new IOException(city);
        }
    }
}