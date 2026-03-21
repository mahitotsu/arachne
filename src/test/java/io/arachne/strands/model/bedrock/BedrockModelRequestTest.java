package io.arachne.strands.model.bedrock;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;

import io.arachne.strands.model.ModelEvent;
import io.arachne.strands.model.ModelException;
import io.arachne.strands.model.ModelRetryableException;
import io.arachne.strands.model.ModelThrottledException;
import io.arachne.strands.model.ToolSelection;
import io.arachne.strands.model.ToolSpec;
import io.arachne.strands.types.ContentBlock;
import io.arachne.strands.types.Message;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDelta;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockDeltaEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockStart;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockStartEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ContentBlockStopEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamMetadataEvent;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamOutput;
import software.amazon.awssdk.services.bedrockruntime.model.ConverseStreamResponseHandler;
import software.amazon.awssdk.services.bedrockruntime.model.MessageStopEvent;
import software.amazon.awssdk.services.bedrockruntime.model.StopReason;
import software.amazon.awssdk.services.bedrockruntime.model.TokenUsage;
import software.amazon.awssdk.services.bedrockruntime.model.ToolResultContentBlock;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlockDelta;
import software.amazon.awssdk.services.bedrockruntime.model.ToolUseBlockStart;

class BedrockModelRequestTest {

    @Test
    void buildRequestIncludesSystemPrompt() {
        BedrockModel model = new BedrockModel("test-model", "us-west-2");

        var request = model.buildRequest(List.of(Message.user("hello")), List.of(), "Be concise.", null);

        assertThat(request.system()).hasSize(1);
        assertThat(request.system().getFirst().text()).isEqualTo("Be concise.");
    }

    @Test
    void structuredToolResultIsSentAsJson() {
        BedrockModel model = new BedrockModel("test-model", "us-west-2");
        Message message = new Message(
                Message.Role.USER,
                List.of(new ContentBlock.ToolResult("tool-1", Map.of("city", "Tokyo"), "success")));

        var request = model.buildRequest(
                List.of(message),
                List.of(new ToolSpec("weather", "Weather lookup", null)),
                null,
                null);

        ToolResultContentBlock content = request.messages().getFirst().content().getFirst().toolResult().content().getFirst();
        assertThat(content.type()).isEqualTo(ToolResultContentBlock.Type.JSON);
        assertThat(content.json().asMap()).containsKey("city");
    }

    @Test
    void buildRequestCanForceSpecificToolChoice() {
        BedrockModel model = new BedrockModel("test-model", "us-west-2");

        var request = model.buildRequest(
                List.of(Message.user("hello")),
                List.of(new ToolSpec("structured_output", "Final schema", null)),
                null,
                ToolSelection.force("structured_output"));

        assertThat(request.toolConfig().toolChoice().tool().name()).isEqualTo("structured_output");
    }

    @Test
    void converseStreamEmitsDefaultMetadataWhenProviderOmitsMetadataEvent() {
        List<ModelEvent> events = new ArrayList<>();
        BedrockModel model = new BedrockModel(
            unusedClient(),
            asyncClientStreaming(
                List.of(
                    ContentBlockDeltaEvent.builder()
                        .contentBlockIndex(0)
                        .delta(ContentBlockDelta.fromText("partial"))
                        .build(),
                    MessageStopEvent.builder()
                        .stopReason(StopReason.TOOL_USE)
                        .build())),
            "test-model");

        model.converseStream(List.of(Message.user("hello")), List.of(), null, null, events::add);

        assertThat(events).containsExactly(
            new ModelEvent.TextDelta("partial"),
            new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(0, 0)));
    }

    @Test
    void converseStreamTranslatesHandlerReportedAsyncFailures() {
        BedrockModel model = new BedrockModel(
            unusedClient(),
            asyncClientFailing(new ServiceUnavailableException("temporary outage"), true),
            "test-model");

        assertThatThrownBy(() -> model.converseStream(List.of(Message.user("hello")), List.of(), null, null, event -> {
        }))
            .isInstanceOf(ModelRetryableException.class)
            .hasMessageContaining("Bedrock temporarily failed the model request");
    }

    @Test
    void converseStreamTranslatesJoinFailuresWhenHandlerFailureIsAbsent() {
        BedrockModel model = new BedrockModel(
            unusedClient(),
            asyncClientFailing(new ThrottlingException("slow down"), false),
            "test-model");

        assertThatThrownBy(() -> model.converseStream(List.of(Message.user("hello")), List.of(), null, null, event -> {
        }))
            .isInstanceOf(ModelThrottledException.class)
            .hasMessageContaining("Bedrock throttled the model request");
    }

    @Test
    void converseStreamFallsBackToSynchronousConverseWhenAsyncClientIsMissing() {
    BedrockRuntimeClient unusedClient = (BedrockRuntimeClient) Proxy.newProxyInstance(
        BedrockRuntimeClient.class.getClassLoader(),
        new Class<?>[]{BedrockRuntimeClient.class},
        (proxy, method, args) -> {
            if (method.getName().equals("serviceName")) {
            return "BedrockRuntime";
            }
            if (method.getName().equals("close")) {
            return null;
            }
            throw new UnsupportedOperationException(method.getName());
        });
    List<ModelEvent> events = new ArrayList<>();
    List<String> calls = new ArrayList<>();
    BedrockModel model = new BedrockModel(unusedClient, null, "test-model") {
        @Override
        public Iterable<ModelEvent> converse(
            List<Message> messages,
            List<ToolSpec> tools,
            String systemPrompt,
            ToolSelection toolSelection) {
        calls.add(systemPrompt == null ? "" : systemPrompt);
        return List.of(
            new ModelEvent.TextDelta("fallback"),
            new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(4, 2)));
        }
    };

    model.converseStream(List.of(Message.user("hello")), List.of(), "fallback-system", null, events::add);

    assertThat(calls).containsExactly("fallback-system");
    assertThat(events).containsExactly(
        new ModelEvent.TextDelta("fallback"),
        new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(4, 2)));
    }

    @Test
    void handleStreamOutputMapsTextToolUseAndMetadata() throws Exception {
    BedrockModel model = new BedrockModel("test-model", "us-west-2");
    Method handleStreamOutput = BedrockModel.class.getDeclaredMethod(
        "handleStreamOutput",
        ConverseStreamOutput.class,
        Map.class,
        AtomicReference.class,
        AtomicBoolean.class,
        Consumer.class);
    handleStreamOutput.setAccessible(true);
    Map<Integer, Object> toolUses = new LinkedHashMap<>();
    AtomicReference<String> stopReason = new AtomicReference<>("end_turn");
    AtomicBoolean metadataEmitted = new AtomicBoolean(false);
    List<ModelEvent> events = new ArrayList<>();

    invokeHandleStreamOutput(
        handleStreamOutput,
        model,
        ContentBlockStartEvent.builder()
            .contentBlockIndex(1)
            .start(ContentBlockStart.fromToolUse(ToolUseBlockStart.builder()
                .toolUseId("tool-1")
                .name("weather")
                .build()))
            .build(),
        toolUses,
        stopReason,
        metadataEmitted,
        events);
    invokeHandleStreamOutput(
        handleStreamOutput,
        model,
        ContentBlockDeltaEvent.builder()
            .contentBlockIndex(0)
            .delta(ContentBlockDelta.fromText("draft"))
            .build(),
        toolUses,
        stopReason,
        metadataEmitted,
        events);
    invokeHandleStreamOutput(
        handleStreamOutput,
        model,
        ContentBlockDeltaEvent.builder()
            .contentBlockIndex(1)
            .delta(ContentBlockDelta.fromToolUse(ToolUseBlockDelta.builder()
                .input("{\"city\":\"Tokyo\"}")
                .build()))
            .build(),
        toolUses,
        stopReason,
        metadataEmitted,
        events);
    invokeHandleStreamOutput(
        handleStreamOutput,
        model,
        ContentBlockStopEvent.builder()
            .contentBlockIndex(1)
            .build(),
        toolUses,
        stopReason,
        metadataEmitted,
        events);
    invokeHandleStreamOutput(
        handleStreamOutput,
        model,
        MessageStopEvent.builder()
            .stopReason(StopReason.TOOL_USE)
            .build(),
        toolUses,
        stopReason,
        metadataEmitted,
        events);
    invokeHandleStreamOutput(
        handleStreamOutput,
        model,
        ConverseStreamMetadataEvent.builder()
            .usage(TokenUsage.builder()
                .inputTokens(3)
                .outputTokens(5)
                .build())
            .build(),
        toolUses,
        stopReason,
        metadataEmitted,
        events);

    assertThat(events).containsExactly(
        new ModelEvent.TextDelta("draft"),
        new ModelEvent.ToolUse("tool-1", "weather", Map.of("city", "Tokyo")),
        new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(3, 5)));
    assertThat(metadataEmitted).isTrue();
    }

    @Test
    void handleStreamOutputAccumulatesSplitToolInputAndDefaultsMetadataUsage() throws Exception {
    BedrockModel model = new BedrockModel("test-model", "us-west-2");
    Method handleStreamOutput = BedrockModel.class.getDeclaredMethod(
        "handleStreamOutput",
        ConverseStreamOutput.class,
        Map.class,
        AtomicReference.class,
        AtomicBoolean.class,
        Consumer.class);
    handleStreamOutput.setAccessible(true);
    Map<Integer, Object> toolUses = new LinkedHashMap<>();
    AtomicReference<String> stopReason = new AtomicReference<>("end_turn");
    AtomicBoolean metadataEmitted = new AtomicBoolean(false);
    List<ModelEvent> events = new ArrayList<>();

    invokeHandleStreamOutput(
        handleStreamOutput,
        model,
        ContentBlockStartEvent.builder()
            .contentBlockIndex(0)
            .start(ContentBlockStart.fromToolUse(ToolUseBlockStart.builder()
                .toolUseId("tool-9")
                .name("lookup_weather")
                .build()))
            .build(),
        toolUses,
        stopReason,
        metadataEmitted,
        events);
    invokeHandleStreamOutput(
        handleStreamOutput,
        model,
        ContentBlockDeltaEvent.builder()
            .contentBlockIndex(0)
            .delta(ContentBlockDelta.fromToolUse(ToolUseBlockDelta.builder()
                .input("{\"city\":\"To")
                .build()))
            .build(),
        toolUses,
        stopReason,
        metadataEmitted,
        events);
    invokeHandleStreamOutput(
        handleStreamOutput,
        model,
        ContentBlockDeltaEvent.builder()
            .contentBlockIndex(0)
            .delta(ContentBlockDelta.fromToolUse(ToolUseBlockDelta.builder()
                .input("kyo\"}")
                .build()))
            .build(),
        toolUses,
        stopReason,
        metadataEmitted,
        events);
    invokeHandleStreamOutput(
        handleStreamOutput,
        model,
        ContentBlockStopEvent.builder()
            .contentBlockIndex(0)
            .build(),
        toolUses,
        stopReason,
        metadataEmitted,
        events);
    invokeHandleStreamOutput(
        handleStreamOutput,
        model,
        ConverseStreamMetadataEvent.builder().build(),
        toolUses,
        stopReason,
        metadataEmitted,
        events);

    assertThat(events).containsExactly(
        new ModelEvent.ToolUse("tool-9", "lookup_weather", Map.of("city", "Tokyo")),
        new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(0, 0)));
    assertThat(metadataEmitted).isTrue();
    }

    @Test
    void parseStreamedToolInputRejectsInvalidJson() throws Exception {
    BedrockModel model = new BedrockModel("test-model", "us-west-2");
    Method parseStreamedToolInput = BedrockModel.class.getDeclaredMethod("parseStreamedToolInput", String.class);
    parseStreamedToolInput.setAccessible(true);

    assertThatThrownBy(() -> parseStreamedToolInput.invoke(model, "{not-json}"))
        .hasCauseInstanceOf(ModelException.class)
        .cause()
        .hasMessageContaining("invalid streamed tool input JSON");
    }

    @Test
    void translateExceptionMapsRetryableAndThrottledFailures() throws Exception {
    BedrockModel model = new BedrockModel("test-model", "us-west-2");
    Method translateException = BedrockModel.class.getDeclaredMethod("translateException", Throwable.class);
    translateException.setAccessible(true);

    RuntimeException throttled = (RuntimeException) translateException.invoke(
        model,
        new CompletionException(new ThrottlingException("slow down")));
    RuntimeException retryable = (RuntimeException) translateException.invoke(
        model,
        new ServiceUnavailableException("temporary outage"));

    assertThat(throttled)
        .isInstanceOf(ModelThrottledException.class)
        .hasMessageContaining("Bedrock throttled the model request");
    assertThat(retryable)
        .isInstanceOf(ModelRetryableException.class)
        .hasMessageContaining("Bedrock temporarily failed the model request");
    }

    private static void invokeHandleStreamOutput(
        Method handleStreamOutput,
        BedrockModel model,
        ConverseStreamOutput output,
        Map<Integer, Object> toolUses,
        AtomicReference<String> stopReason,
        AtomicBoolean metadataEmitted,
        List<ModelEvent> events) throws Exception {
    handleStreamOutput.invoke(model, output, toolUses, stopReason, metadataEmitted, (Consumer<ModelEvent>) events::add);
    }

    private static BedrockRuntimeClient unusedClient() {
        return (BedrockRuntimeClient) Proxy.newProxyInstance(
            BedrockRuntimeClient.class.getClassLoader(),
            new Class<?>[]{BedrockRuntimeClient.class},
            (proxy, method, args) -> {
                if (method.getName().equals("serviceName")) {
                    return "BedrockRuntime";
                }
                if (method.getName().equals("close")) {
                    return null;
                }
                throw new UnsupportedOperationException(method.getName());
            });
    }

    private static BedrockRuntimeAsyncClient asyncClientStreaming(List<ConverseStreamOutput> outputs) {
        return (BedrockRuntimeAsyncClient) Proxy.newProxyInstance(
            BedrockRuntimeAsyncClient.class.getClassLoader(),
            new Class<?>[]{BedrockRuntimeAsyncClient.class},
            (proxy, method, args) -> {
                if (method.getName().equals("serviceName")) {
                    return "BedrockRuntime";
                }
                if (method.getName().equals("close")) {
                    return null;
                }
                if (method.getName().equals("converseStream")) {
                    ConverseStreamResponseHandler handler = (ConverseStreamResponseHandler) args[1];
                    handler.onEventStream(SdkPublisher.fromIterable(outputs));
                    handler.complete();
                    return CompletableFuture.completedFuture(null);
                }
                throw new UnsupportedOperationException(method.getName());
            });
    }

    private static BedrockRuntimeAsyncClient asyncClientFailing(Throwable failure, boolean reportToHandler) {
        return (BedrockRuntimeAsyncClient) Proxy.newProxyInstance(
            BedrockRuntimeAsyncClient.class.getClassLoader(),
            new Class<?>[]{BedrockRuntimeAsyncClient.class},
            (proxy, method, args) -> {
                if (method.getName().equals("serviceName")) {
                    return "BedrockRuntime";
                }
                if (method.getName().equals("close")) {
                    return null;
                }
                if (method.getName().equals("converseStream")) {
                    ConverseStreamResponseHandler handler = (ConverseStreamResponseHandler) args[1];
                    if (reportToHandler) {
                        handler.exceptionOccurred(failure);
                        return CompletableFuture.completedFuture(null);
                    }
                    return CompletableFuture.failedFuture(new CompletionException(failure));
                }
                throw new UnsupportedOperationException(method.getName());
            });
    }

    private static final class ThrottlingException extends RuntimeException {
        private ThrottlingException(String message) {
            super(message);
        }
    }

    private static final class ServiceUnavailableException extends RuntimeException {
        private ServiceUnavailableException(String message) {
            super(message);
        }
    }
}