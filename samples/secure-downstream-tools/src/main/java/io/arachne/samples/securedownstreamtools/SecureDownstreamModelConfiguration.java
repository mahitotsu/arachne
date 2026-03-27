package io.arachne.samples.securedownstreamtools;

import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.arachne.strands.model.Model;
import io.arachne.strands.model.ModelEvent;
import io.arachne.strands.types.Message;

@Configuration(proxyBeanMethods = false)
public class SecureDownstreamModelConfiguration {

    @Bean
    Model secureDownstreamDemoModel() {
        return new Model() {
            private boolean firstCall = true;

            @Override
            public Iterable<ModelEvent> converse(List<Message> messages, List<io.arachne.strands.model.ToolSpec> tools) {
                if (firstCall) {
                    firstCall = false;
                    return List.of(
                            new ModelEvent.ToolUse("tool-1", "current_operator_capabilities", Map.of()),
                            new ModelEvent.ToolUse("tool-2", "fetch_customer_profile", Map.of("customerId", "cust-42")),
                            new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(3, 3)));
                }
                return List.of(
                        new ModelEvent.TextDelta("capability view and downstream profile fetched"),
                        new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
            }
        };
    }
}