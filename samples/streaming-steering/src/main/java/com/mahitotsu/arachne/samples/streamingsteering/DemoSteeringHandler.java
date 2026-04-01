package com.mahitotsu.arachne.samples.streamingsteering;

import java.util.concurrent.atomic.AtomicBoolean;

import com.mahitotsu.arachne.strands.hooks.AfterModelCallEvent;
import com.mahitotsu.arachne.strands.hooks.BeforeToolCallEvent;
import com.mahitotsu.arachne.strands.steering.Guide;
import com.mahitotsu.arachne.strands.steering.ModelSteeringAction;
import com.mahitotsu.arachne.strands.steering.Proceed;
import com.mahitotsu.arachne.strands.steering.SteeringHandler;
import com.mahitotsu.arachne.strands.steering.ToolSteeringAction;
import com.mahitotsu.arachne.strands.types.ContentBlock;

final class DemoSteeringHandler extends SteeringHandler {

    private final AtomicBoolean retried = new AtomicBoolean();

    @Override
    protected ToolSteeringAction steerBeforeTool(BeforeToolCallEvent event) {
        if ("policy_lookup".equals(event.toolName())) {
            return new Guide("Use the cached refund policy summary instead of the live lookup.");
        }
        return new Proceed("allow");
    }

    @Override
    protected ModelSteeringAction steerAfterModel(AfterModelCallEvent event) {
        if (retried.get()) {
            return new Proceed("accept");
        }
        String text = event.response().content().stream()
                .filter(ContentBlock.Text.class::isInstance)
                .map(ContentBlock.Text.class::cast)
                .map(ContentBlock.Text::text)
                .findFirst()
                .orElse("");
        if (text.contains("blocked")) {
            retried.set(true);
            return new Guide("Provide the cached refund policy summary directly.");
        }
        return new Proceed("accept");
    }
}