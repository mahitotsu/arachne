package io.arachne.strands.tool;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.arachne.strands.hooks.NoOpHookRegistry;
import io.arachne.strands.model.ToolSpec;
import io.arachne.strands.types.ContentBlock;

class ToolExecutorTest {

    @Test
    void sequentialModeExecutesToolsInOrder() {
        List<String> order = new CopyOnWriteArrayList<>();
        Tool first = namedTool("first", order);
        Tool second = namedTool("second", order);

        ToolExecutor executor = new ToolExecutor(ToolExecutionMode.SEQUENTIAL);
        List<ToolResult> results = executor.execute(
                List.of(first, second),
                List.of(
                        new ContentBlock.ToolUse("1", "first", Map.of()),
                        new ContentBlock.ToolUse("2", "second", Map.of())),
                new NoOpHookRegistry());

        assertThat(order).containsExactly("first", "second");
        assertThat(results).extracting(ToolResult::toolUseId).containsExactly("1", "2");
    }

    private static Tool namedTool(String name, List<String> order) {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec(name, name, JsonNodeFactory.instance.objectNode());
            }

            @Override
            public ToolResult invoke(Object input) {
                order.add(name);
                return ToolResult.success(null, name);
            }
        };
    }
}