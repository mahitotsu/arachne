package io.arachne.strands.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.arachne.strands.hooks.HookRegistry;
import io.arachne.strands.types.ContentBlock;

/**
 * Executes model-requested tool calls using the configured policy.
 */
public class ToolExecutor {

    private static final Logger LOG = Logger.getLogger(ToolExecutor.class.getName());

    private final ToolExecutionMode executionMode;
    private final Executor parallelExecutor;

    public ToolExecutor() {
        this(ToolExecutionMode.PARALLEL, null);
    }

    public ToolExecutor(ToolExecutionMode executionMode) {
        this(executionMode, null);
    }

    public ToolExecutor(ToolExecutionMode executionMode, Executor parallelExecutor) {
        this.executionMode = executionMode;
        this.parallelExecutor = parallelExecutor;
    }

    public List<ToolResult> execute(List<Tool> tools, List<ContentBlock.ToolUse> requests, HookRegistry hooks) {
        return switch (executionMode) {
            case PARALLEL -> executeParallel(tools, requests, hooks);
            case SEQUENTIAL -> executeSequential(tools, requests, hooks);
        };
    }

    private List<ToolResult> executeSequential(List<Tool> tools, List<ContentBlock.ToolUse> requests, HookRegistry hooks) {
        List<ToolResult> results = new ArrayList<>(requests.size());
        for (ContentBlock.ToolUse request : requests) {
            results.add(executeOne(tools, request, hooks));
        }
        return List.copyOf(results);
    }

    private List<ToolResult> executeParallel(List<Tool> tools, List<ContentBlock.ToolUse> requests, HookRegistry hooks) {
        if (parallelExecutor != null) {
            return executeParallel(tools, requests, hooks, parallelExecutor);
        }

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            return executeParallel(tools, requests, hooks, executor);
        }
    }

    private List<ToolResult> executeParallel(
            List<Tool> tools,
            List<ContentBlock.ToolUse> requests,
            HookRegistry hooks,
            Executor executor) {
        List<CompletableFuture<ToolResult>> futures = requests.stream()
                .map(request -> CompletableFuture.supplyAsync(() -> executeOne(tools, request, hooks), executor))
                .toList();

        List<ToolResult> results = new ArrayList<>(futures.size());
        for (CompletableFuture<ToolResult> future : futures) {
            results.add(await(future));
        }
        return List.copyOf(results);
    }

    private ToolResult await(CompletableFuture<ToolResult> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ToolDefinitionException("Tool execution interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new ToolDefinitionException("Tool execution failed", cause);
        }
    }

    private ToolResult executeOne(List<Tool> tools, ContentBlock.ToolUse request, HookRegistry hooks) {
        hooks.onBeforeToolCall(request.name(), request.toolUseId(), request.input());
        ToolResult result;
        try {
            Tool tool = findTool(tools, request.name());
            result = tool.invoke(request.input());
        } catch (StructuredOutputException e) {
            LOG.log(Level.FINE, () -> "Tool invocation failed: " + request.name() + ": " + e.getMessage());
            throw e;
        } catch (ToolDefinitionException | ToolValidationException e) {
            LOG.log(Level.FINE, () -> "Tool invocation failed: " + request.name() + ": " + e.getMessage());
            result = ToolResult.error(request.toolUseId(), e.getMessage());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Tool invocation failed: " + request.name(), e);
            result = ToolResult.error(request.toolUseId(), e.getMessage());
        }

        if (result.toolUseId() == null || result.toolUseId().isBlank()) {
            result = new ToolResult(request.toolUseId(), result.status(), result.content());
        }
        hooks.onAfterToolCall(request.name(), request.toolUseId(), result);
        return result;
    }

    private Tool findTool(List<Tool> tools, String name) {
        return tools.stream()
                .filter(tool -> tool.spec().name().equals(name))
                .findFirst()
                .orElseThrow(() -> new ToolDefinitionException("Unknown tool: " + name));
    }
}