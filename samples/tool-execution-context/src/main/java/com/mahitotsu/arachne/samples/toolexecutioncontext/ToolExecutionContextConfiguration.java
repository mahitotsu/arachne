package com.mahitotsu.arachne.samples.toolexecutioncontext;

import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.mahitotsu.arachne.strands.model.Model;
import com.mahitotsu.arachne.strands.model.ModelEvent;
import com.mahitotsu.arachne.strands.tool.ExecutionContextPropagation;
import com.mahitotsu.arachne.strands.types.ContentBlock;
import com.mahitotsu.arachne.strands.types.Message;

@Configuration(proxyBeanMethods = false)
public class ToolExecutionContextConfiguration {

    @Bean
    Model demoToolContextModel() {
        return new Model() {
            private boolean firstCall = true;

            @Override
            public Iterable<ModelEvent> converse(List<Message> messages, List<com.mahitotsu.arachne.strands.model.ToolSpec> tools) {
                if (firstCall) {
                    firstCall = false;
                    return List.of(
                            new ModelEvent.ToolUse("tool-1", "context_echo", Map.of("value", "alpha")),
                            new ModelEvent.ToolUse("tool-2", "context_echo", Map.of("value", "beta")),
                            new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(2, 2)));
                }
                return List.of(
                        new ModelEvent.TextDelta("completed tool demo"),
                        new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
            }
        };
    }

    @Bean
    ExecutionContextPropagation requestIdPropagation(DemoRequestContext requestContext) {
        return task -> {
            String captured = requestContext.currentRequestId();
            return () -> {
                String previous = requestContext.currentRequestId();
                requestContext.restore(captured);
                try {
                    task.run();
                } finally {
                    requestContext.restore(previous);
                }
            };
        };
    }
}
