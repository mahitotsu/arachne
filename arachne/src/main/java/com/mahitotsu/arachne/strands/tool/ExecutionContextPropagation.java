package com.mahitotsu.arachne.strands.tool;

import java.util.List;

/**
 * Captures and restores execution context around tool execution tasks.
 *
 * <p>This contract is intentionally about executor-boundary propagation of runtime context
 * such as thread-local framework state. It is separate from {@link ToolInvocationContext},
 * which carries logical tool-call metadata.
 */
@FunctionalInterface
public interface ExecutionContextPropagation {

    /**
     * Capture the current execution context and return a wrapped task that restores it when run.
     */
    Runnable wrap(Runnable task);

    static ExecutionContextPropagation noop() {
        return task -> task;
    }

    static ExecutionContextPropagation compose(List<? extends ExecutionContextPropagation> propagations) {
        List<? extends ExecutionContextPropagation> ordered = propagations == null ? List.of() : List.copyOf(propagations);
        if (ordered.isEmpty()) {
            return noop();
        }
        return task -> {
            Runnable wrapped = task;
            for (int index = ordered.size() - 1; index >= 0; index--) {
                wrapped = ordered.get(index).wrap(wrapped);
            }
            return wrapped;
        };
    }
}
