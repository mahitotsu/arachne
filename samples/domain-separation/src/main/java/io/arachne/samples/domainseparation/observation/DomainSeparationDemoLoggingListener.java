package com.mahitotsu.arachne.samples.domainseparation.observation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.arachne.strands.spring.ArachneLifecycleApplicationEvent;
import io.arachne.strands.tool.ToolResult;
import io.arachne.strands.types.ContentBlock;
import io.arachne.strands.types.Message;

@Component
@ConditionalOnProperty(name = "sample.domain-separation.demo-logging.enabled", havingValue = "true", matchIfMissing = true)
public class DomainSeparationDemoLoggingListener {

    private static final Logger log = LoggerFactory.getLogger(DomainSeparationDemoLoggingListener.class);

    private final ObjectMapper objectMapper;
    private final boolean verboseExecutorLogging;

    public DomainSeparationDemoLoggingListener(
            ObjectMapper objectMapper,
            @Value("${sample.domain-separation.demo-logging.verbose-executor:false}") boolean verboseExecutorLogging) {
        this.objectMapper = objectMapper;
        this.verboseExecutorLogging = verboseExecutorLogging;
    }

    @EventListener
    public void onLifecycleEvent(ArachneLifecycleApplicationEvent event) {
        if (event.payload() instanceof ArachneLifecycleApplicationEvent.ModelCallObservation observation) {
            if (verboseExecutorLogging && "beforeModelCall".equals(event.type())) {
                logExecutorModelRequest(observation);
            }
            if ("afterModelCall".equals(event.type())) {
                logModelResponse(observation);
                if (verboseExecutorLogging) {
                    logExecutorModelResponse(observation);
                }
            }
            return;
        }
        if (!(event.payload() instanceof ArachneLifecycleApplicationEvent.ToolCallObservation observation)) {
            return;
        }
        if (!isDemoTool(observation.toolName())) {
            return;
        }
        if ("beforeToolCall".equals(event.type())) {
            logBeforeToolCall(observation);
            return;
        }
        if ("afterToolCall".equals(event.type())) {
            logAfterToolCall(observation);
        }
    }

    private void logModelResponse(ArachneLifecycleApplicationEvent.ModelCallObservation observation) {
        Message response = observation.response();
        if (response == null || response.role() != Message.Role.ASSISTANT) {
            return;
        }

        String requestedTools = response.content().stream()
                .filter(ContentBlock.ToolUse.class::isInstance)
                .map(ContentBlock.ToolUse.class::cast)
                .map(ContentBlock.ToolUse::name)
                .collect(Collectors.joining(", "));
        if (!requestedTools.isBlank()) {
            log.info("llm.trace> assistant requested tools: {}", requestedTools);
        }

        String text = response.content().stream()
                .filter(ContentBlock.Text.class::isInstance)
                .map(ContentBlock.Text.class::cast)
                .map(ContentBlock.Text::text)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining(" "));
        if (!text.isBlank()) {
            log.info("llm.trace> assistant text begin");
            for (String line : text.split("\\R", -1)) {
                log.info("llm.trace> | {}", line);
            }
            log.info("llm.trace> assistant text end");
        }
    }

    private void logExecutorModelRequest(ArachneLifecycleApplicationEvent.ModelCallObservation observation) {
        if (!isExecutorConversation(observation)) {
            return;
        }

        log.info("executor.llm.trace> request begin");
        String systemPrompt = observation.systemPrompt();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            log.info("executor.llm.trace> system-prompt> {}", systemPrompt.trim());
        }

        String latestUserText = latestUserText(observation.messages());
        if (!latestUserText.isBlank()) {
            for (String line : latestUserText.split("\\R", -1)) {
                log.info("executor.llm.trace> prompt | {}", line);
            }
        }

        String lastToolResult = lastToolResultContent(observation.messages());
        if (!lastToolResult.isBlank()) {
            log.info("executor.llm.trace> last-tool-result> {}", lastToolResult);
        }
        log.info("executor.llm.trace> request end");
    }

    private void logExecutorModelResponse(ArachneLifecycleApplicationEvent.ModelCallObservation observation) {
        if (!isExecutorConversation(observation)) {
            return;
        }

        Message response = observation.response();
        if (response == null || response.role() != Message.Role.ASSISTANT) {
            return;
        }

        log.info("executor.llm.trace> response begin");
        String requestedTools = response.content().stream()
                .filter(ContentBlock.ToolUse.class::isInstance)
                .map(ContentBlock.ToolUse.class::cast)
                .map(ContentBlock.ToolUse::name)
                .collect(Collectors.joining(", "));
        if (!requestedTools.isBlank()) {
            log.info("executor.llm.trace> tools> {}", requestedTools);
        }

        String text = response.content().stream()
                .filter(ContentBlock.Text.class::isInstance)
                .map(ContentBlock.Text.class::cast)
                .map(ContentBlock.Text::text)
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining(" "));
        if (!text.isBlank()) {
            for (String line : text.split("\\R", -1)) {
                log.info("executor.llm.trace> text | {}", line);
            }
        }

        log.info("executor.llm.trace> stop-reason> {}", stringValue(observation.stopReason()));
        log.info("executor.llm.trace> response end");
    }

    private void logBeforeToolCall(ArachneLifecycleApplicationEvent.ToolCallObservation observation) {
        Map<String, Object> input = mapValue(observation.input());
        String toolName = observation.toolName();
        if ("activate_skill".equals(toolName)) {
            log.info("demo.trace> coordinator requests skill activation: {}", stringValue(input.get("name")));
            return;
        }
        if ("prepare_account_operation".equals(toolName) || "execute_account_operation".equals(toolName)) {
            log.info(
                    "demo.trace> coordinator calls {} for {} {}",
                    toolName,
                    stringValue(input.get("operationType")),
                    stringValue(input.get("accountId")));
            return;
        }
        if ("find_account".equals(toolName) || "unlock_account".equals(toolName)) {
            log.info(
                    "demo.trace> executor runs {} for {}",
                    toolName,
                    stringValue(input.get("accountId")));
        }
    }

    private void logAfterToolCall(ArachneLifecycleApplicationEvent.ToolCallObservation observation) {
        ToolResult result = observation.result();
        if (result == null) {
            return;
        }

        Map<String, Object> content = mapValue(result.content());
        String toolName = observation.toolName();
        if ("activate_skill".equals(toolName)) {
            log.info("demo.trace> skill activated: {}", stringValue(content.get("name")));
            return;
        }
        if ("prepare_account_operation".equals(toolName)) {
            log.info("demo.trace> preparation returned status {}", stringValue(content.get("preparedStatus")));
            return;
        }
        if ("execute_account_operation".equals(toolName)) {
            log.info("demo.trace> execution returned outcome {}", stringValue(content.get("outcome")));
            return;
        }
        if ("find_account".equals(toolName)) {
            log.info("demo.trace> executor observed account status {}", stringValue(content.get("currentStatus")));
            return;
        }
        if ("unlock_account".equals(toolName)) {
            log.info("demo.trace> executor mutation outcome {}", stringValue(content.get("outcome")));
        }
    }

    private boolean isDemoTool(String toolName) {
        return "activate_skill".equals(toolName)
                || "prepare_account_operation".equals(toolName)
                || "execute_account_operation".equals(toolName)
                || "find_account".equals(toolName)
                || "unlock_account".equals(toolName);
    }

    private boolean isExecutorConversation(ArachneLifecycleApplicationEvent.ModelCallObservation observation) {
        String latestUserText = latestUserText(observation.messages());
        if (latestUserText.isBlank()) {
            return false;
        }
        return latestUserText.contains("mode=prepare") || latestUserText.contains("mode=execute");
    }

    private String latestUserText(java.util.List<Message> messages) {
        return messages.stream()
                .filter(message -> message.role() == Message.Role.USER)
                .flatMap(message -> message.content().stream())
                .filter(ContentBlock.Text.class::isInstance)
                .map(ContentBlock.Text.class::cast)
                .map(ContentBlock.Text::text)
                .reduce((first, second) -> second)
                .orElse("");
    }

    private String lastToolResultContent(java.util.List<Message> messages) {
        return messages.stream()
                .flatMap(message -> message.content().stream())
                .filter(ContentBlock.ToolResult.class::isInstance)
                .map(ContentBlock.ToolResult.class::cast)
                .reduce((first, second) -> second)
                .map(ContentBlock.ToolResult::content)
                .map(this::jsonValue)
                .orElse("");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value == null) {
            return Map.of();
        }
        if (value instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, nestedValue) -> copy.put(String.valueOf(key), nestedValue));
            return copy;
        }
        if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean) {
            return Map.of("value", value);
        }
        return objectMapper.convertValue(value, Map.class);
    }

    private String jsonValue(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return String.valueOf(value);
        }
    }

    private String stringValue(Object value) {
        return value == null ? "unknown" : String.valueOf(value);
    }
}