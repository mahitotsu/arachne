package io.arachne.strands.tool;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.arachne.strands.agent.AgentState;
import io.arachne.strands.hooks.DispatchingHookRegistry;
import io.arachne.strands.hooks.HookProvider;
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
            new NoOpHookRegistry(),
            new AgentState());

        assertThat(order).containsExactly("first", "second");
        assertThat(results).extracting(ToolResult::toolUseId).containsExactly("1", "2");
    }

    @Test
    void parallelModeUsesProvidedExecutor() {
        List<String> order = new CopyOnWriteArrayList<>();
        Tool first = namedTool("first", order);
        Tool second = namedTool("second", order);
        RecordingExecutor recordingExecutor = new RecordingExecutor();

        ToolExecutor executor = new ToolExecutor(ToolExecutionMode.PARALLEL, recordingExecutor);
        List<ToolResult> results = executor.execute(
                List.of(first, second),
                List.of(
                        new ContentBlock.ToolUse("1", "first", Map.of()),
                        new ContentBlock.ToolUse("2", "second", Map.of())),
            new NoOpHookRegistry(),
            new AgentState());

        assertThat(recordingExecutor.count()).isEqualTo(2);
        assertThat(results).extracting(ToolResult::toolUseId).containsExactly("1", "2");
    }

        @Test
        void toolHooksCanShortCircuitAndRewriteResult() {
        HookProvider hookProvider = registrar -> registrar
            .beforeToolCall(event -> event.skipWith(ToolResult.success(event.toolUseId(), "short-circuited")))
            .afterToolCall(event -> event.setResult(ToolResult.success(event.toolUseId(), event.result().content() + "-after")));
        ToolExecutor executor = new ToolExecutor(ToolExecutionMode.SEQUENTIAL);

        List<ToolResult> results = executor.execute(
            List.of(namedTool("first", new CopyOnWriteArrayList<>())),
            List.of(new ContentBlock.ToolUse("1", "first", Map.of("value", 1))),
            DispatchingHookRegistry.fromProviders(List.of(hookProvider)),
            new AgentState());

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.toolUseId()).isEqualTo("1");
            assertThat(result.content()).isEqualTo("short-circuited-after");
        });
        }

    private static final class RecordingExecutor implements Executor {

        private final AtomicInteger executions = new AtomicInteger();

        @Override
        public void execute(Runnable command) {
            executions.incrementAndGet();
            command.run();
        }

        int count() {
            return executions.get();
        }
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