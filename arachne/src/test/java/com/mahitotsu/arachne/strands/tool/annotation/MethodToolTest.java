package com.mahitotsu.arachne.strands.tool.annotation;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

import com.mahitotsu.arachne.strands.schema.JsonSchemaGenerator;
import com.mahitotsu.arachne.strands.tool.ToolDefinitionException;
import com.mahitotsu.arachne.strands.tool.ToolInvocationContext;

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

    @Test
    void invokeInjectsToolInvocationContextAndOmitsItFromSchema() throws Exception {
        Method method = ContextAwareTools.class.getDeclaredMethod("lookup", String.class, ToolInvocationContext.class);
        MethodTool tool = new MethodTool(new ContextAwareTools(), method, schemaGenerator);

        ToolInvocationContext context = new ToolInvocationContext("context_lookup", "tool-9", Map.of("city", "Tokyo"), new com.mahitotsu.arachne.strands.agent.AgentState());

        assertThat(tool.spec().inputSchema().get("properties").has("city")).isTrue();
        assertThat(tool.spec().inputSchema().get("properties").has("context")).isFalse();
        assertThat(tool.invoke(Map.of("city", "Tokyo"), context).content()).isEqualTo("context_lookup:tool-9:Tokyo");
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

    static class ContextAwareTools {

        @StrandsTool(name = "context_lookup")
        public String lookup(@ToolParam(name = "city") String city, ToolInvocationContext context) {
            return context.toolName() + ":" + context.toolUseId() + ":" + city;
        }
    }
}
