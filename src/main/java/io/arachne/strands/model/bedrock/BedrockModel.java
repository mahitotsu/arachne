package io.arachne.strands.model.bedrock;

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

import io.arachne.strands.model.Model;
import io.arachne.strands.model.ModelEvent;
import io.arachne.strands.model.ModelException;
import io.arachne.strands.model.ModelRetryableException;
import io.arachne.strands.model.ModelThrottledException;
import io.arachne.strands.model.StreamingModel;
import io.arachne.strands.model.ToolSelection;
import io.arachne.strands.model.ToolSpec;
import io.arachne.strands.types.ContentBlock;
import io.arachne.strands.types.Message;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
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

    /** Default model used when none is configured. */
    public static final String DEFAULT_MODEL_ID = "jp.amazon.nova-2-lite-v1:0";

    /** Default region used when none is configured. */
    public static final String DEFAULT_REGION = "ap-northeast-1";

    private static final Logger LOG = Logger.getLogger(BedrockModel.class.getName());

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final BedrockRuntimeClient client;
    private final BedrockRuntimeAsyncClient asyncClient;
    private final String modelId;
    private final String region;

    /**
     * Create a BedrockModel using the default region and model.
     */
    public BedrockModel() {
        this(DEFAULT_MODEL_ID, DEFAULT_REGION);
    }

    /**
     * Create a BedrockModel with an explicit model ID and region.
     *
     * @param modelId Bedrock model ID, e.g. {@code "jp.amazon.nova-2-lite-v1:0"}
     * @param region AWS region, e.g. {@code "ap-northeast-1"}
     */
    public BedrockModel(String modelId, String region) {
        this.modelId = modelId;
        this.region = region == null || region.isBlank() ? DEFAULT_REGION : region;
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
        this(client, null, modelId, DEFAULT_REGION);
    }

    public BedrockModel(BedrockRuntimeClient client, BedrockRuntimeAsyncClient asyncClient, String modelId) {
        this(client, asyncClient, modelId, DEFAULT_REGION);
    }

    private BedrockModel(
            BedrockRuntimeClient client,
            BedrockRuntimeAsyncClient asyncClient,
            String modelId,
            String region) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.asyncClient = asyncClient;
        this.modelId = Objects.requireNonNull(modelId, "modelId must not be null");
        this.region = region == null || region.isBlank() ? DEFAULT_REGION : region;
    }

    /** The Bedrock model ID this instance is bound to. */
    public String getModelId() {
        return modelId;
    }

    /** The AWS region this instance sends Bedrock requests to. */
    public String getRegion() {
        return region;
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

        AtomicReference<String> stopReason = new AtomicReference<>("end_turn");
        AtomicBoolean metadataEmitted = new AtomicBoolean(false);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Map<Integer, StreamedToolUse> toolUses = new LinkedHashMap<>();

        ConverseStreamResponseHandler handler = ConverseStreamResponseHandler.builder()
                .subscriber(output -> handleStreamOutput(output, toolUses, stopReason, metadataEmitted, eventConsumer))
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
        if (!metadataEmitted.get()) {
            eventConsumer.accept(new ModelEvent.Metadata(stopReason.get(), new ModelEvent.Usage(0, 0)));
        }
    }

    private RuntimeException translateException(Throwable throwable) {
        Throwable exception = throwable;
        if (exception == null) {
            exception = new IllegalStateException("Bedrock model request failed without an exception cause");
        } else {
            exception = unwrap(exception);
        }
        if (exception instanceof ModelException modelException) {
            return modelException;
        }
        String exceptionName = exception.getClass().getSimpleName();
        String message = exception.getMessage() == null ? exceptionName : exception.getMessage();

        if ("ThrottlingException".equals(exceptionName)) {
            return new ModelThrottledException("Bedrock throttled the model request: " + message, exception);
        }
        if ("ServiceUnavailableException".equals(exceptionName)
                || "ModelNotReadyException".equals(exceptionName)
                || "InternalServerException".equals(exceptionName)) {
            return new ModelRetryableException("Bedrock temporarily failed the model request: " + message, exception);
        }
        return new ModelException("Bedrock model request failed: " + message, exception);
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    // ── Request building ────────────────────────────────────────────────────

    ConverseRequest buildRequest(
            List<Message> messages,
            List<ToolSpec> tools,
            String systemPrompt,
            ToolSelection toolSelection) {
        List<software.amazon.awssdk.services.bedrockruntime.model.Message> bedrockMessages =
                messages.stream().map(this::toBedrockMessage).toList();

        ConverseRequest.Builder builder = ConverseRequest.builder()
                .modelId(modelId)
                .messages(bedrockMessages);

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            builder.system(SystemContentBlock.builder().text(systemPrompt).build());
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
        List<software.amazon.awssdk.services.bedrockruntime.model.Message> bedrockMessages =
                messages.stream().map(this::toBedrockMessage).toList();

        ConverseStreamRequest.Builder builder = ConverseStreamRequest.builder()
                .modelId(modelId)
                .messages(bedrockMessages);

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            builder.system(SystemContentBlock.builder().text(systemPrompt).build());
        }

        if (!tools.isEmpty()) {
            builder.toolConfig(buildToolConfig(tools, toolSelection));
        }

        return builder.build();
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
        List<Tool> bedrockTools = tools.stream().map(spec -> {
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
        }).toList();

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

        Integer inputTokenCount = response.usage() != null ? response.usage().inputTokens() : null;
        Integer outputTokenCount = response.usage() != null ? response.usage().outputTokens() : null;
        int inputTokens = inputTokenCount != null ? inputTokenCount : 0;
        int outputTokens = outputTokenCount != null ? outputTokenCount : 0;

        events.add(new ModelEvent.Metadata(stopReasonStr, new ModelEvent.Usage(inputTokens, outputTokens)));

        return events;
    }

    private void handleStreamOutput(
            ConverseStreamOutput output,
            Map<Integer, StreamedToolUse> toolUses,
            AtomicReference<String> stopReason,
            AtomicBoolean metadataEmitted,
            Consumer<ModelEvent> eventConsumer) {
        if (output instanceof ContentBlockStartEvent startEvent) {
            handleContentBlockStart(startEvent, toolUses);
            return;
        }
        if (output instanceof ContentBlockDeltaEvent deltaEvent) {
            handleContentBlockDelta(deltaEvent, toolUses, eventConsumer);
            return;
        }
        if (output instanceof ContentBlockStopEvent stopEvent) {
            handleContentBlockStop(stopEvent, toolUses, eventConsumer);
            return;
        }
        if (output instanceof MessageStopEvent messageStopEvent) {
            stopReason.set(messageStopEvent.stopReasonAsString() != null
                    ? messageStopEvent.stopReasonAsString()
                    : "end_turn");
            return;
        }
        if (output instanceof ConverseStreamMetadataEvent metadataEvent) {
            metadataEmitted.set(true);
            software.amazon.awssdk.services.bedrockruntime.model.TokenUsage usage = metadataEvent.usage();
            Integer inputTokenCount = usage != null ? usage.inputTokens() : null;
            Integer outputTokenCount = usage != null ? usage.outputTokens() : null;
            int inputTokens = inputTokenCount != null ? inputTokenCount : 0;
            int outputTokens = outputTokenCount != null ? outputTokenCount : 0;
            eventConsumer.accept(new ModelEvent.Metadata(
                    stopReason.get(),
                    new ModelEvent.Usage(inputTokens, outputTokens)));
        }
    }

    private void handleContentBlockStart(ContentBlockStartEvent startEvent, Map<Integer, StreamedToolUse> toolUses) {
        if (startEvent.start() == null || startEvent.start().toolUse() == null || startEvent.contentBlockIndex() == null) {
            return;
        }
        toolUses.put(
                startEvent.contentBlockIndex(),
                new StreamedToolUse(
                        startEvent.start().toolUse().toolUseId(),
                        startEvent.start().toolUse().name()));
    }

    private void handleContentBlockDelta(
            ContentBlockDeltaEvent deltaEvent,
            Map<Integer, StreamedToolUse> toolUses,
            Consumer<ModelEvent> eventConsumer) {
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

    private void handleContentBlockStop(
            ContentBlockStopEvent stopEvent,
            Map<Integer, StreamedToolUse> toolUses,
            Consumer<ModelEvent> eventConsumer) {
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

    private record StreamedToolUse(String toolUseId, String name, StringBuilder input) {

        private StreamedToolUse(String toolUseId, String name) {
            this(toolUseId, name, new StringBuilder());
        }
    }

    // ── Document ↔ Java object conversion ──────────────────────────────────

    /**
     * Convert a Jackson {@link JsonNode} (typically a JSON Schema) to an AWS SDK {@link Document}.
     */
    static Document jsonNodeToDocument(JsonNode node) {
        if (node == null || node.isNull()) return Document.fromNull();
        if (node.isBoolean()) return Document.fromBoolean(node.booleanValue());
        if (node.isNumber()) return Document.fromNumber(node.decimalValue().toPlainString());
        if (node.isTextual()) return Document.fromString(node.textValue());
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

    /**
     * Convert a plain Java object (Map, List, String, Number, Boolean, null) to an AWS SDK Document.
     * Used when a tool's input was stored as a Java object.
     */
    @SuppressWarnings("unchecked")
    static Document objectToDocument(Object obj) {
        if (obj == null) return Document.fromNull();
        if (obj instanceof Boolean b) return Document.fromBoolean(b);
        if (obj instanceof Number n) return Document.fromNumber(n.toString());
        if (obj instanceof String s) return Document.fromString(s);
        if (obj instanceof List<?> list) {
            List<Document> docs = list.stream().map(BedrockModel::objectToDocument).toList();
            return Document.fromList(docs);
        }
        if (obj instanceof Map<?, ?> map) {
            Map<String, Document> docs = new LinkedHashMap<>();
            ((Map<String, Object>) map).forEach((k, v) -> docs.put(k, objectToDocument(v)));
            return Document.fromMap(docs);
        }
        // Fallback: serialize arbitrary POJOs into a JSON-shaped Document.
        try {
            JsonNode node = OBJECT_MAPPER.valueToTree(obj);
            return jsonNodeToDocument(node);
        } catch (IllegalArgumentException e) {
            return Document.fromString(obj.toString());
        }
    }

    /**
     * Convert an AWS SDK {@link Document} (tool-use input from the model) to a plain Java object
     * that tools can consume.
     */
    static Object documentToObject(Document doc) {
        if (doc == null || doc.isNull()) return null;
        if (doc.isBoolean()) return doc.asBoolean();
        if (doc.isNumber()) {
            try {
                return doc.asNumber().longValue();
            } catch (NumberFormatException e) {
                try {
                    return doc.asNumber().doubleValue();
                } catch (NumberFormatException e2) {
                    return doc.asNumber().stringValue();
                }
            }
        }
        if (doc.isString()) return doc.asString();
        if (doc.isList()) {
            return doc.asList().stream().map(BedrockModel::documentToObject).toList();
        }
        if (doc.isMap()) {
            Map<String, Object> result = new LinkedHashMap<>();
            doc.asMap().forEach((k, v) -> result.put(k, documentToObject(v)));
            return result;
        }
        return doc.toString();
    }
}
