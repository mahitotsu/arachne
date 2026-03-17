package io.arachne.strands.model.bedrock;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.arachne.strands.model.Model;
import io.arachne.strands.model.ModelEvent;
import io.arachne.strands.model.ToolSpec;
import io.arachne.strands.types.ContentBlock;
import io.arachne.strands.types.Message;
import software.amazon.awssdk.core.document.Document;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlock.Type;
import software.amazon.awssdk.services.bedrockruntime.model.ConversationRole;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseRequest;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseResponse;
import software.amazon.awssdk.services.bedrockruntime.model.SystemContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.Tool;
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
 * <p>Uses the synchronous (non-streaming) {@code converse} API for Phase 1 simplicity.
 * Streaming via {@code converseStream} is planned for Phase 6.
 *
 * <p>Corresponds to {@code strands.models.BedrockModel} in the Python SDK.
 *
 * <p>AWS credentials are resolved via the standard SDK default-credentials chain
 * (environment variables, instance profile, ~/.aws/credentials, etc.).
 */
public class BedrockModel implements Model {

    /** Default model used when none is configured. */
    public static final String DEFAULT_MODEL_ID = "jp.amazon.nova-2-lite-v1:0";

    /** Default region used when none is configured. */
    public static final String DEFAULT_REGION = "ap-northeast-1";

    private static final Logger LOG = Logger.getLogger(BedrockModel.class.getName());

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final BedrockRuntimeClient client;
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
         * @param modelId  Bedrock model ID, e.g. {@code "jp.amazon.nova-2-lite-v1:0"}
         * @param region   AWS region, e.g. {@code "ap-northeast-1"}
     */
    public BedrockModel(String modelId, String region) {
        this.modelId = modelId;
        this.region = region == null || region.isBlank() ? DEFAULT_REGION : region;
        this.client = BedrockRuntimeClient.builder()
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
        this.client = client;
        this.modelId = modelId;
        this.region = DEFAULT_REGION;
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
        ConverseRequest request = buildRequest(messages, tools, systemPrompt);
        LOG.fine(() -> "modelId=" + modelId + " | invoking Bedrock Converse API");

        ConverseResponse response = client.converse(request);
        return mapResponse(response);
    }

    // ── Request building ────────────────────────────────────────────────────

    ConverseRequest buildRequest(List<Message> messages, List<ToolSpec> tools, String systemPrompt) {
        List<software.amazon.awssdk.services.bedrockruntime.model.Message> bedrockMessages =
                messages.stream().map(this::toBedrockMessage).toList();

        ConverseRequest.Builder builder = ConverseRequest.builder()
                .modelId(modelId)
                .messages(bedrockMessages);

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            builder.system(SystemContentBlock.builder().text(systemPrompt).build());
        }

        if (!tools.isEmpty()) {
            builder.toolConfig(buildToolConfig(tools));
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

    private ToolConfiguration buildToolConfig(List<ToolSpec> tools) {
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

        return ToolConfiguration.builder().tools(bedrockTools).build();
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

        software.amazon.awssdk.services.bedrockruntime.model.Message message =
                response.output().message();

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

        events.add(new ModelEvent.Metadata(
                stopReasonStr,
                new ModelEvent.Usage(inputTokens, outputTokens)));

        return events;
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
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
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
        // Fallback: try to serialize via Jackson
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
