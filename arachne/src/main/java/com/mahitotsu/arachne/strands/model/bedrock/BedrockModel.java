package com.mahitotsu.arachne.strands.model.bedrock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.mahitotsu.arachne.strands.model.Model;
import com.mahitotsu.arachne.strands.model.ModelEvent;
import com.mahitotsu.arachne.strands.model.ModelException;
import com.mahitotsu.arachne.strands.model.ModelRetryableException;
import com.mahitotsu.arachne.strands.model.ModelThrottledException;
import com.mahitotsu.arachne.strands.model.StreamingModel;
import com.mahitotsu.arachne.strands.model.ToolSelection;
import com.mahitotsu.arachne.strands.model.ToolSpec;
import com.mahitotsu.arachne.strands.types.ContentBlock;
import com.mahitotsu.arachne.strands.types.Message;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.CachePointBlock;
import software.amazon.awssdk.services.bedrockruntime.model.CachePointType;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock.Type;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDeltaEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockStartEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockStopEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamMetadataEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.MessageStopEvent;
import software.amazon.awssdk.services.bedrockruntime.model.SpecificToolChoice;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
import software.amazon.awssdk.services.bedrockruntime.model.ToolChoice;
import software.amazon.awssdk.services.bedrockruntime.model.ToolConfiguration;
import software.amazon.awssdk.services.bedrockruntime.model.ToolInputSchema;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultStatus;
import software.amazon.awssdk.services.bedrockruntime.model.ToolSpecification;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlock;

/**
 * {@link Model} implementation backed by the AWS Bedrock ConverseAPI.
 *
 * <p>Uses the synchronous {@code converse} API for the standard blocking path and
 * {@code converseStream} for the optional Phase 6 streaming path.
 *
 * <p>Corresponds to {@code strands.models.BedrockModel} in the Python SDK.
 *
 * <p>AWS credentials are resolved via the standard SDK default-credentials chain
 * (environment variables, instance profile, ~/.aws/credentials, etc.).
 */
public class BedrockModel implements StreamingModel {

    private static final String DEFAULT_STOP_REASON = "end_turn";

    public record PromptCaching(boolean systemPrompt, boolean tools) {

        public static final PromptCaching DISABLED = new PromptCaching(false, false);
    }

    /** Default model used when none is configured. */
    public static final String DEFAULT_MODEL_ID = "jp.amazon.nova-2-lite-v1:0";

    /** Default region used when none is configured. */
    public static final String DEFAULT_REGION = "ap-northeast-1";

    private static final Logger LOG = Logger.getLogger(BedrockModel.class.getName());

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final CachePointBlock DEFAULT_CACHE_POINT = CachePointBlock.builder()
            .type(CachePointType.DEFAULT)
            .build();
    private static final FailureTranslator FAILURE_TRANSLATOR = new FailureTranslator();
    private static final BedrockDocuments BEDROCK_DOCUMENTS = new BedrockDocuments();

    private final BedrockRuntimeClient client;
    private final BedrockRuntimeAsyncClient asyncClient;
    private final String modelId;
    private final String region;
    private final PromptCaching promptCaching;

    /**
     * Create a BedrockModel using the default region and model.
     */
    public BedrockModel() {
        this(DEFAULT_MODEL_ID, DEFAULT_REGION, PromptCaching.DISABLED);
    }

    /**
     * Create a BedrockModel with an explicit model ID and region.
     *
     * @param modelId Bedrock model ID, e.g. {@code "jp.amazon.nova-2-lite-v1:0"}
     * @param region AWS region, e.g. {@code "ap-northeast-1"}
     */
    public BedrockModel(String modelId, String region) {
        this(modelId, region, PromptCaching.DISABLED);
    }

    public BedrockModel(String modelId, String region, PromptCaching promptCaching) {
        this.modelId = modelId;
        this.region = region == null || region.isBlank() ? DEFAULT_REGION : region;
        this.promptCaching = promptCaching == null ? PromptCaching.DISABLED : promptCaching;
        this.client = BedrockRuntimeClient.builder()
                .region(Region.of(this.region))
                .build();
        this.asyncClient = BedrockRuntimeAsyncClient.builder()
            .region(Region.of(this.region))
            .build();
    }

    /**
     * Create a BedrockModel using a pre-built client (useful for testing/VPC endpoints).
     *
     * @param client  pre-configured {@link BedrockRuntimeClient}
     * @param modelId Bedrock model ID
     */
    public BedrockModel(BedrockRuntimeClient client, String modelId) {
        this(client, null, modelId, DEFAULT_REGION, PromptCaching.DISABLED);
    }

    public BedrockModel(BedrockRuntimeClient client, BedrockRuntimeAsyncClient asyncClient, String modelId) {
        this(client, asyncClient, modelId, DEFAULT_REGION, PromptCaching.DISABLED);
    }

    public BedrockModel(
            BedrockRuntimeClient client,
            BedrockRuntimeAsyncClient asyncClient,
            String modelId,
            String region,
            PromptCaching promptCaching) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.asyncClient = asyncClient;
        this.modelId = Objects.requireNonNull(modelId, "modelId must not be null");
        this.region = region == null || region.isBlank() ? DEFAULT_REGION : region;
        this.promptCaching = promptCaching == null ? PromptCaching.DISABLED : promptCaching;
    }

    /** The Bedrock model ID this instance is bound to. */
    public String getModelId() {
        return modelId;
    }

    /** The AWS region this instance sends Bedrock requests to. */
    public String getRegion() {
        return region;
    }

    public PromptCaching getPromptCaching() {
        return promptCaching;
    }

    // ── Model interface ──────────────────────────────────────────────────────

    @Override
    public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools) {
        return converse(messages, tools, null);
    }

    @Override
    public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools, String systemPrompt) {
        return converse(messages, tools, systemPrompt, null);
    }

    @Override
    public Iterable<ModelEvent> converse(
            List<Message> messages,
            List<ToolSpec> tools,
            String systemPrompt,
            ToolSelection toolSelection) {
        ConverseRequest request = buildRequest(messages, tools, systemPrompt, toolSelection);
        LOG.fine(() -> "modelId=" + modelId + " | invoking Bedrock Converse API");

        ConverseResponse response;
        try {
            response = client.converse(request);
        } catch (RuntimeException exception) {
            throw translateException(exception);
        }
        return mapResponse(response);
    }

    @Override
    public void converseStream(
            List<Message> messages,
            List<ToolSpec> tools,
            String systemPrompt,
            ToolSelection toolSelection,
            Consumer<ModelEvent> eventConsumer) {
        Objects.requireNonNull(eventConsumer, "eventConsumer must not be null");
        if (asyncClient == null) {
            StreamingModel.super.converseStream(messages, tools, systemPrompt, toolSelection, eventConsumer);
            return;
        }

        ConverseStreamRequest request = buildStreamRequest(messages, tools, systemPrompt, toolSelection);
        LOG.fine(() -> "modelId=" + modelId + " | invoking Bedrock ConverseStream API");

        AtomicReference<Throwable> failure = new AtomicReference<>();
        StreamState streamState = new StreamState(eventConsumer);

        ConverseStreamResponseHandler handler = ConverseStreamResponseHandler.builder()
            .subscriber(streamState::handleOutput)
                .onError(failure::set)
                .build();

        CompletableFuture<Void> future;
        try {
            future = asyncClient.converseStream(request, handler);
            future.join();
        } catch (RuntimeException exception) {
            Throwable streamFailure = failure.get();
            throw translateException(streamFailure != null ? streamFailure : exception);
        }

        Throwable streamFailure = failure.get();
        if (streamFailure != null) {
            throw translateException(streamFailure);
        }
        streamState.emitDefaultMetadataIfMissing();
    }

    private RuntimeException translateException(Throwable throwable) {
        return FAILURE_TRANSLATOR.translate(throwable);
    }

    // ── Request building ────────────────────────────────────────────────────

    ConverseRequest buildRequest(
            List<Message> messages,
            List<ToolSpec> tools,
            String systemPrompt,
            ToolSelection toolSelection) {
        ConverseRequest.Builder builder = ConverseRequest.builder()
                .modelId(modelId)
            .messages(toBedrockMessages(messages));

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            builder.system(buildSystemPromptBlocks(systemPrompt));
        }

        if (!tools.isEmpty()) {
            builder.toolConfig(buildToolConfig(tools, toolSelection));
        }

        return builder.build();
    }

    private ConverseStreamRequest buildStreamRequest(
            List<Message> messages,
            List<ToolSpec> tools,
            String systemPrompt,
            ToolSelection toolSelection) {
        ConverseStreamRequest.Builder builder = ConverseStreamRequest.builder()
                .modelId(modelId)
            .messages(toBedrockMessages(messages));

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            builder.system(buildSystemPromptBlocks(systemPrompt));
        }

        if (!tools.isEmpty()) {
            builder.toolConfig(buildToolConfig(tools, toolSelection));
        }

        return builder.build();
    }

    private List<software.amazon.awssdk.services.bedrockruntime.model.Message> toBedrockMessages(List<Message> messages) {
        return messages.stream().map(this::toBedrockMessage).toList();
    }

    private software.amazon.awssdk.services.bedrockruntime.model.Message toBedrockMessage(Message msg) {
        ConversationRole role = switch (msg.role()) {
            case USER -> ConversationRole.USER;
            case ASSISTANT -> ConversationRole.ASSISTANT;
        };

        List<software.amazon.awssdk.services.bedrockruntime.model.ContentBlock> blocks =
                msg.content().stream().map(this::toBedrockContentBlock).toList();

        return software.amazon.awssdk.services.bedrockruntime.model.Message.builder()
                .role(role)
                .content(blocks)
                .build();
    }

    private software.amazon.awssdk.services.bedrockruntime.model.ContentBlock toBedrockContentBlock(
            ContentBlock block) {
        return switch (block) {
            case ContentBlock.Text t ->
                    software.amazon.awssdk.services.bedrockruntime.model.ContentBlock.builder()
                            .text(t.text())
                            .build();

            case ContentBlock.ToolUse tu -> {
                Document inputDoc = objectToDocument(tu.input());
                yield software.amazon.awssdk.services.bedrockruntime.model.ContentBlock.builder()
                        .toolUse(ToolUseBlock.builder()
                                .toolUseId(tu.toolUseId())
                                .name(tu.name())
                                .input(inputDoc)
                                .build())
                        .build();
            }

            case ContentBlock.ToolResult tr -> {
                ToolResultStatus status = "error".equalsIgnoreCase(tr.status())
                        ? ToolResultStatus.ERROR
                        : ToolResultStatus.SUCCESS;
                yield software.amazon.awssdk.services.bedrockruntime.model.ContentBlock.builder()
                        .toolResult(ToolResultBlock.builder()
                                .toolUseId(tr.toolUseId())
                                .status(status)
                                .content(List.of(toBedrockToolResultContentBlock(tr.content())))
                                .build())
                        .build();
            }
        };
    }

    private ToolResultContentBlock toBedrockToolResultContentBlock(Object content) {
        if (content instanceof CharSequence || content instanceof Character) {
            return ToolResultContentBlock.builder().text(content.toString()).build();
        }

        return ToolResultContentBlock.builder()
                .json(objectToDocument(content))
                .build();
    }

    private ToolConfiguration buildToolConfig(List<ToolSpec> tools, ToolSelection toolSelection) {
        List<Tool> bedrockTools = new ArrayList<>(tools.stream().map(spec -> {
            Document schemaDoc = jsonNodeToDocument(spec.inputSchema());
            return Tool.builder()
                    .toolSpec(ToolSpecification.builder()
                            .name(spec.name())
                            .description(spec.description())
                            .inputSchema(ToolInputSchema.builder()
                                    .json(schemaDoc)
                                    .build())
                            .build())
                    .build();
        }).toList());

        if (promptCaching.tools() && !bedrockTools.isEmpty()) {
            bedrockTools.add(Tool.fromCachePoint(DEFAULT_CACHE_POINT));
        }

        ToolConfiguration.Builder builder = ToolConfiguration.builder().tools(bedrockTools);
        if (toolSelection != null) {
            builder.toolChoice(ToolChoice.fromTool(SpecificToolChoice.builder()
                    .name(toolSelection.toolName())
                    .build()));
        }
        return builder.build();
    }

    // ── Response mapping ────────────────────────────────────────────────────

    /**
     * Map a {@link ConverseResponse} to a list of {@link ModelEvent}s.
     *
     * <p>Emits one {@link ModelEvent.TextDelta} per text content block,
     * one {@link ModelEvent.ToolUse} per tool-use content block,
     * and a final {@link ModelEvent.Metadata} with stop reason and token usage.
     */
    private List<ModelEvent> mapResponse(ConverseResponse response) {
        List<ModelEvent> events = new ArrayList<>();

        software.amazon.awssdk.services.bedrockruntime.model.Message message = response.output().message();

        for (software.amazon.awssdk.services.bedrockruntime.model.ContentBlock block : message.content()) {
            if (block.type() == Type.TEXT) {
                events.add(new ModelEvent.TextDelta(block.text()));
            } else if (block.type() == Type.TOOL_USE) {
                ToolUseBlock tu = block.toolUse();
                Object input = documentToObject(tu.input());
                events.add(new ModelEvent.ToolUse(tu.toolUseId(), tu.name(), input));
            }
            // Other content block types (reasoning, image, etc.) are ignored in Phase 1
        }

        String stopReasonStr = response.stopReasonAsString() != null
                ? response.stopReasonAsString()
                : "end_turn";

        events.add(new ModelEvent.Metadata(stopReasonStr, toUsage(response.usage())));

        return events;
    }

    private List<SystemContentBlock> buildSystemPromptBlocks(String systemPrompt) {
        List<SystemContentBlock> systemBlocks = new ArrayList<>();
        systemBlocks.add(SystemContentBlock.fromText(systemPrompt));
        if (promptCaching.systemPrompt()) {
            systemBlocks.add(SystemContentBlock.fromCachePoint(DEFAULT_CACHE_POINT));
        }
        return List.copyOf(systemBlocks);
    }

    private ModelEvent.Usage toUsage(software.amazon.awssdk.services.bedrockruntime.model.TokenUsage usage) {
        if (usage == null) {
            return ModelEvent.ZERO_USAGE;
        }
        Integer inputTokenCount = usage.inputTokens();
        Integer outputTokenCount = usage.outputTokens();
        Integer cacheReadTokenCount = usage.cacheReadInputTokens();
        Integer cacheWriteTokenCount = usage.cacheWriteInputTokens();
        return new ModelEvent.Usage(
                inputTokenCount != null ? inputTokenCount : 0,
                outputTokenCount != null ? outputTokenCount : 0,
                cacheReadTokenCount != null ? cacheReadTokenCount : 0,
                cacheWriteTokenCount != null ? cacheWriteTokenCount : 0);
    }

    private Object parseStreamedToolInput(String rawInput) {
        return BEDROCK_DOCUMENTS.parseStreamedToolInput(rawInput);
    }

    private record StreamedToolUse(String toolUseId, String name, StringBuilder input) {

        private StreamedToolUse(String toolUseId, String name) {
            this(toolUseId, name, new StringBuilder());
        }
    }

    private final class StreamState {

        private final Consumer<ModelEvent> eventConsumer;
        private final AtomicReference<String> stopReason = new AtomicReference<>(DEFAULT_STOP_REASON);
        private final AtomicBoolean metadataEmitted = new AtomicBoolean(false);
        private final Map<Integer, StreamedToolUse> toolUses = new LinkedHashMap<>();

        private StreamState(Consumer<ModelEvent> eventConsumer) {
            this.eventConsumer = eventConsumer;
        }

        private void handleOutput(ConverseStreamOutput output) {
            if (output instanceof ContentBlockStartEvent startEvent) {
                handleContentBlockStart(startEvent);
                return;
            }
            if (output instanceof ContentBlockDeltaEvent deltaEvent) {
                handleContentBlockDelta(deltaEvent);
                return;
            }
            if (output instanceof ContentBlockStopEvent stopEvent) {
                handleContentBlockStop(stopEvent);
                return;
            }
            if (output instanceof MessageStopEvent messageStopEvent) {
                stopReason.set(messageStopEvent.stopReasonAsString() != null
                        ? messageStopEvent.stopReasonAsString()
                        : DEFAULT_STOP_REASON);
                return;
            }
            if (output instanceof ConverseStreamMetadataEvent metadataEvent) {
                metadataEmitted.set(true);
                eventConsumer.accept(new ModelEvent.Metadata(stopReason.get(), toUsage(metadataEvent.usage())));
            }
        }

        private void emitDefaultMetadataIfMissing() {
            if (!metadataEmitted.get()) {
                eventConsumer.accept(new ModelEvent.Metadata(stopReason.get(), ModelEvent.ZERO_USAGE));
            }
        }

        private void handleContentBlockStart(ContentBlockStartEvent startEvent) {
            if (startEvent.start() == null || startEvent.start().toolUse() == null || startEvent.contentBlockIndex() == null) {
                return;
            }
            toolUses.put(
                    startEvent.contentBlockIndex(),
                    new StreamedToolUse(
                            startEvent.start().toolUse().toolUseId(),
                            startEvent.start().toolUse().name()));
        }

        private void handleContentBlockDelta(ContentBlockDeltaEvent deltaEvent) {
            if (deltaEvent.delta() == null) {
                return;
            }
            if (deltaEvent.delta().text() != null) {
                eventConsumer.accept(new ModelEvent.TextDelta(deltaEvent.delta().text()));
            }
            if (deltaEvent.delta().toolUse() != null && deltaEvent.contentBlockIndex() != null) {
                StreamedToolUse toolUse = toolUses.get(deltaEvent.contentBlockIndex());
                if (toolUse != null && deltaEvent.delta().toolUse().input() != null) {
                    toolUse.input().append(deltaEvent.delta().toolUse().input());
                }
            }
        }

        private void handleContentBlockStop(ContentBlockStopEvent stopEvent) {
            if (stopEvent.contentBlockIndex() == null) {
                return;
            }
            StreamedToolUse toolUse = toolUses.remove(stopEvent.contentBlockIndex());
            if (toolUse == null) {
                return;
            }
            eventConsumer.accept(new ModelEvent.ToolUse(
                    toolUse.toolUseId(),
                    toolUse.name(),
                    parseStreamedToolInput(toolUse.input().toString())));
        }
    }

    // ── Document ↔ Java object conversion ──────────────────────────────────

    /**
     * Convert a Jackson {@link JsonNode} (typically a JSON Schema) to an AWS SDK {@link Document}.
     */
    static Document jsonNodeToDocument(JsonNode node) {
        return BEDROCK_DOCUMENTS.jsonNodeToDocument(node);
    }

    /**
     * Convert a plain Java object (Map, List, String, Number, Boolean, null) to an AWS SDK Document.
     * Used when a tool's input was stored as a Java object.
     */
    static Document objectToDocument(Object obj) {
        return BEDROCK_DOCUMENTS.objectToDocument(obj);
    }

    /**
     * Convert an AWS SDK {@link Document} (tool-use input from the model) to a plain Java object
     * that tools can consume.
     */
    static Object documentToObject(Document doc) {
        return BEDROCK_DOCUMENTS.documentToObject(doc);
    }

    private static final class FailureTranslator {

        private RuntimeException translate(Throwable throwable) {
            Throwable resolvedException = throwable == null ? null : unwrap(throwable);
            if (resolvedException instanceof ModelException modelException) {
                return modelException;
            }
            Throwable failure = resolvedException == null
                    ? new IllegalStateException("Bedrock model request failed without an exception cause")
                    : resolvedException;
            return classifyFailure(failure);
        }

        private RuntimeException classifyFailure(Throwable failure) {
            String exceptionName = failure.getClass().getSimpleName();
            String message = failure.getMessage() == null ? exceptionName : failure.getMessage();

            if (isThrottlingFailure(exceptionName)) {
                return new ModelThrottledException("Bedrock throttled the model request: " + message, failure);
            }
            if (isRetryableFailure(exceptionName)) {
                return new ModelRetryableException("Bedrock temporarily failed the model request: " + message, failure);
            }
            return new ModelException("Bedrock model request failed: " + message, failure);
        }

        private boolean isThrottlingFailure(String exceptionName) {
            return "ThrottlingException".equals(exceptionName);
        }

        private boolean isRetryableFailure(String exceptionName) {
            return "ServiceUnavailableException".equals(exceptionName)
                    || "ModelNotReadyException".equals(exceptionName)
                    || "InternalServerException".equals(exceptionName);
        }

        private Throwable unwrap(Throwable throwable) {
            Throwable current = throwable;
            while (current instanceof CompletionException && current.getCause() != null) {
                current = current.getCause();
            }
            return current;
        }
    }

    private static final class BedrockDocuments {

        private Document jsonNodeToDocument(JsonNode node) {
            if (node == null || node.isNull()) {
                return Document.fromNull();
            }
            if (node.isBoolean()) {
                return Document.fromBoolean(node.booleanValue());
            }
            if (node.isNumber()) {
                return Document.fromNumber(node.decimalValue().toPlainString());
            }
            if (node.isTextual()) {
                return Document.fromString(node.textValue());
            }
            if (node.isArray()) {
                List<Document> list = new ArrayList<>();
                node.forEach(child -> list.add(jsonNodeToDocument(child)));
                return Document.fromList(list);
            }
            if (node.isObject()) {
                Map<String, Document> map = new LinkedHashMap<>();
                for (Map.Entry<String, JsonNode> entry : node.properties()) {
                    map.put(entry.getKey(), jsonNodeToDocument(entry.getValue()));
                }
                return Document.fromMap(map);
            }
            throw new IllegalArgumentException("Unsupported JsonNode type: " + node.getNodeType());
        }

        private Document objectToDocument(Object obj) {
            if (obj == null) {
                return Document.fromNull();
            }
            if (obj instanceof Boolean b) {
                return Document.fromBoolean(b);
            }
            if (obj instanceof Number n) {
                return Document.fromNumber(n.toString());
            }
            if (obj instanceof String s) {
                return Document.fromString(s);
            }
            if (obj instanceof List<?> list) {
                List<Document> docs = list.stream().map(this::objectToDocument).toList();
                return Document.fromList(docs);
            }
            if (obj instanceof Map<?, ?> map) {
                Map<String, Document> docs = new LinkedHashMap<>();
                map.forEach((key, value) -> docs.put(String.valueOf(key), objectToDocument(value)));
                return Document.fromMap(docs);
            }
            try {
                JsonNode node = OBJECT_MAPPER.valueToTree(obj);
                return jsonNodeToDocument(node);
            } catch (IllegalArgumentException exception) {
                return Document.fromString(obj.toString());
            }
        }

        private Object documentToObject(Document doc) {
            if (doc == null || doc.isNull()) {
                return null;
            }
            if (doc.isBoolean()) {
                return doc.asBoolean();
            }
            if (doc.isNumber()) {
                return numberValue(doc);
            }
            if (doc.isString()) {
                return doc.asString();
            }
            if (doc.isList()) {
                return doc.asList().stream().map(this::documentToObject).toList();
            }
            if (doc.isMap()) {
                Map<String, Object> result = new LinkedHashMap<>();
                doc.asMap().forEach((k, v) -> result.put(k, documentToObject(v)));
                return result;
            }
            return doc.toString();
        }

        private Object parseStreamedToolInput(String rawInput) {
            if (rawInput == null || rawInput.isBlank()) {
                return null;
            }
            try {
                return OBJECT_MAPPER.readValue(rawInput, Object.class);
            } catch (java.io.IOException | RuntimeException exception) {
                throw new ModelException("Bedrock returned invalid streamed tool input JSON", exception);
            }
        }

        private Object numberValue(Document doc) {
            String number = doc.asNumber().stringValue();
            if (number.contains(".") || number.contains("e") || number.contains("E")) {
                try {
                    return Double.valueOf(number);
                } catch (NumberFormatException ignored) {
                    return number;
                }
            }
            try {
                return Long.valueOf(number);
            } catch (NumberFormatException longException) {
                try {
                    return Double.valueOf(number);
                } catch (NumberFormatException doubleException) {
                    return number;
                }
            }
        }
    }
}
