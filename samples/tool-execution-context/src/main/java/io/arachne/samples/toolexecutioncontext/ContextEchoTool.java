package com.mahitotsu.arachne.samples.toolexecutioncontext;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import io.arachne.strands.tool.ToolInvocationContext;
import io.arachne.strands.tool.annotation.StrandsTool;
import io.arachne.strands.tool.annotation.ToolParam;

@Service
public class ContextEchoTool {

    private final DemoRequestContext requestContext;

    public ContextEchoTool(DemoRequestContext requestContext) {
        this.requestContext = requestContext;
    }

    @StrandsTool(name = "context_echo", description = "Echo a value while exposing logical and execution context details.")
    public String contextEcho(@ToolParam(description = "Value to echo") String value, ToolInvocationContext context) {
        requestContext.recordCurrentRequestId();
        appendStateEntry(context, "toolCalls", context.toolUseId() + ":" + context.toolName() + ":" + value);
        String result = context.toolUseId() + "|" + context.toolName() + "|" + value + "|" + requestContext.currentRequestId();
        appendStateEntry(context, "toolResults", result);
        return result;
    }

    private void appendStateEntry(ToolInvocationContext context, String key, String entry) {
        synchronized (context.state()) {
            List<String> values = new ArrayList<>();
            Object existing = context.state().get(key);
            if (existing instanceof List<?> list) {
                for (Object item : list) {
                    values.add(String.valueOf(item));
                }
            }
            values.add(entry);
            context.state().put(key, List.copyOf(values));
        }
    }
}
