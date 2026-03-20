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

import io.arachne.strands.agent.AgentInterrupt;
import io.arachne.strands.agent.AgentState;
import io.arachne.strands.hooks.AfterToolCallEvent;
import io.arachne.strands.hooks.BeforeToolCallEvent;
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

    public List<ToolResult> execute(
            List<Tool> tools,
            List<ContentBlock.ToolUse> requests,
            HookRegistry hooks,
            AgentState state) {
        List<BeforeToolCallEvent> preparedRequests = prepareRequests(requests, hooks, state);
        return switch (executionMode) {
            case PARALLEL -> executeParallel(tools, preparedRequests, hooks, state);
            case SEQUENTIAL -> executeSequential(tools, preparedRequests, hooks, state);
        };
    }

    private List<BeforeToolCallEvent> prepareRequests(
            List<ContentBlock.ToolUse> requests,
            HookRegistry hooks,
            AgentState state) {
        List<BeforeToolCallEvent> preparedRequests = new ArrayList<>(requests.size());
        List<AgentInterrupt> interrupts = new ArrayList<>();
        for (ContentBlock.ToolUse request : requests) {
            BeforeToolCallEvent event = hooks.onBeforeToolCall(
                    new BeforeToolCallEvent(request.name(), request.toolUseId(), request.input(), state));
            preparedRequests.add(event);
            if (event.interrupt() != null) {
                interrupts.add(event.interrupt());
            }
        }
        if (!interrupts.isEmpty()) {
            throw new ToolExecutionInterruptedException(interrupts);
        }
        return List.copyOf(preparedRequests);
    }

    private List<ToolResult> executeSequential(
            List<Tool> tools,
            List<BeforeToolCallEvent> requests,
            HookRegistry hooks,
            AgentState state) {
        List<ToolResult> results = new ArrayList<>(requests.size());
        for (BeforeToolCallEvent request : requests) {
            results.add(executeOne(tools, request, hooks, state));
        }
        return List.copyOf(results);
    }

    private List<ToolResult> executeParallel(
            List<Tool> tools,
            List<BeforeToolCallEvent> requests,
            HookRegistry hooks,
            AgentState state) {
        if (parallelExecutor != null) {
            return executeParallel(tools, requests, hooks, state, parallelExecutor);
        }

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            return executeParallel(tools, requests, hooks, state, executor);
        }
    }

    private List<ToolResult> executeParallel(
            List<Tool> tools,
            List<BeforeToolCallEvent> requests,
            HookRegistry hooks,
            AgentState state,
            Executor executor) {
        List<CompletableFuture<ToolResult>> futures = requests.stream()
                .map(request -> CompletableFuture.supplyAsync(() -> executeOne(tools, request, hooks, state), executor))
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

    private ToolResult executeOne(
            List<Tool> tools,
            BeforeToolCallEvent request,
            HookRegistry hooks,
            AgentState state) {
        ToolResult result;
        try {
            if (request.overrideResult() != null) {
                result = request.overrideResult();
            } else {
                Tool tool = findTool(tools, request.toolName());
                result = tool.invoke(request.input());
            }
        } catch (StructuredOutputException e) {
            LOG.log(Level.FINE, () -> "Tool invocation failed: " + request.toolName() + ": " + e.getMessage());
            throw e;
        } catch (ToolDefinitionException | ToolValidationException e) {
            LOG.log(Level.FINE, () -> "Tool invocation failed: " + request.toolName() + ": " + e.getMessage());
            result = ToolResult.error(request.toolUseId(), e.getMessage());
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Tool invocation failed: " + request.toolName(), e);
            result = ToolResult.error(request.toolUseId(), e.getMessage());
        }

        if (result.toolUseId() == null || result.toolUseId().isBlank()) {
            result = new ToolResult(request.toolUseId(), result.status(), result.content());
        }
        return hooks.onAfterToolCall(new AfterToolCallEvent(
                request.toolName(),
                request.toolUseId(),
                result,
                state)).result();
    }

    private Tool findTool(List<Tool> tools, String name) {
        return tools.stream()
                .filter(tool -> tool.spec().name().equals(name))
                .findFirst()
                .orElseThrow(() -> new ToolDefinitionException("Unknown tool: " + name));
    }
}