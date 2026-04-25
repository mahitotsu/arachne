package com.mahitotsu.arachne.samples.delivery.paymentservice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mahitotsu.arachne.strands.model.Model;
import com.mahitotsu.arachne.strands.model.ModelEvent;
import com.mahitotsu.arachne.strands.model.ToolSelection;
import com.mahitotsu.arachne.strands.model.ToolSpec;
import com.mahitotsu.arachne.strands.spring.AgentFactory;
import com.mahitotsu.arachne.strands.tool.Tool;
import com.mahitotsu.arachne.strands.tool.ToolInvocationContext;
import com.mahitotsu.arachne.strands.tool.ToolResult;
import com.mahitotsu.arachne.strands.types.ContentBlock;
import com.mahitotsu.arachne.strands.types.Message;

@SpringBootApplication
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }

    @Bean
    SecurityFilterChain paymentSecurity(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}))
                .build();
    }
}

@RestController
@RequestMapping(path = "/internal/payment", produces = MediaType.APPLICATION_JSON_VALUE)
class PaymentController {

    private final PaymentApplicationService applicationService;

    PaymentController(PaymentApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/prepare")
    PaymentPrepareResponse prepare(@RequestBody PaymentPrepareRequest request) {
        return applicationService.prepare(request);
    }
}

@Service
class PaymentApplicationService {

    private final AgentFactory agentFactory;
    private final PaymentProfileRepository repository;
    private final Tool paymentProfileLookupTool;

    PaymentApplicationService(
            AgentFactory agentFactory,
            PaymentProfileRepository repository,
            @Qualifier("paymentProfileLookupTool") Tool paymentProfileLookupTool) {
        this.agentFactory = agentFactory;
        this.repository = repository;
        this.paymentProfileLookupTool = paymentProfileLookupTool;
    }

    PaymentPrepareResponse prepare(PaymentPrepareRequest request) {
        PaymentProfile profile = repository.profileFor(request.message());
        boolean charged = request.confirmRequested();
        String authorizationId = charged ? "pay-" + UUID.randomUUID().toString().substring(0, 8) : null;
        String paymentStatus = charged ? "CHARGED" : "READY";
        String summary = agentFactory.builder()
            .systemPrompt("""
                あなたは単一ブランドのクラウドキッチンアプリの payment-agent です。
                支払方法と確認状態を簡潔に説明してください。
                通常は登録済みのデジタル決済手段ですが、お客様が明示的に希望された場合は代金引換も受け付けてください。
                """)
                .tools(paymentProfileLookupTool)
                .build()
                .run("message=" + request.message())
                .text();
        return new PaymentPrepareResponse(
                "payment-service",
                "payment-agent",
                charged ? "payment-agent が請求処理を完了しました" : "payment-agent が希望の支払方法を準備しました",
                summary,
                profile.methodLabel(),
                request.total().setScale(2, RoundingMode.HALF_UP),
                paymentStatus,
                charged,
                authorizationId);
    }
}

@Component
class PaymentProfileRepository {

    PaymentProfile profileFor(String message) {
        if (message != null && (message.toLowerCase().contains("apple") || message.contains("アップル"))) {
            return new PaymentProfile("apple-pay", "Apple Pay", "メインの iPhone のウォレットに登録済みです。");
        }
        if (message != null && message.contains("現金")) {
            return new PaymentProfile("cash", "代金引換", "現金払いは処理に少し時間がかかりますが、ご利用いただけます。");
        }
        return new PaymentProfile("card-default", "登録済み Visa（下4桁: 2048）", "デフォルトカードはワンタップ確認済みです。");
    }
}

@Configuration
class PaymentArachneConfiguration {

    @Bean
    Tool paymentProfileLookupTool(PaymentProfileRepository repository) {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec("payment_profile_lookup", "支払パスを提案する前に登録済みの支払プロファイルを読み込む。", schema());
            }

            @Override
            public ToolResult invoke(Object input) {
                return invoke(input, new ToolInvocationContext("payment_profile_lookup", null, input, null));
            }

            @Override
            public ToolResult invoke(Object input, ToolInvocationContext context) {
                String message = String.valueOf(values(input).getOrDefault("message", ""));
                PaymentProfile profile = repository.profileFor(message);
                return ToolResult.success(context.toolUseId(), Map.of(
                        "method", profile.methodLabel(),
                        "note", profile.note()));
            }
        };
    }

    @Bean
    @ConditionalOnProperty(name = "delivery.model.mode", havingValue = "deterministic", matchIfMissing = false)
    Model paymentDeterministicModel() {
        return new PaymentDeterministicModel();
    }

    private static ObjectNode schema() {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("type", "object");
        ObjectNode properties = root.putObject("properties");
        properties.putObject("message").put("type", "string");
        root.putArray("required").add("message");
        root.put("additionalProperties", false);
        return root;
    }

    private static Map<String, Object> values(Object input) {
        if (input instanceof Map<?, ?> rawValues) {
            LinkedHashMap<String, Object> values = new LinkedHashMap<>();
            rawValues.forEach((key, value) -> values.put(String.valueOf(key), value));
            return values;
        }
        return Map.of();
    }

    private static final class PaymentDeterministicModel implements Model {

        @Override
        public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools) {
            return converse(messages, tools, null, null);
        }

        @Override
        public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools, String systemPrompt) {
            return converse(messages, tools, systemPrompt, null);
        }

        @Override
        public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools, String systemPrompt, ToolSelection toolSelection) {
            Map<String, Object> toolContent = latestToolContent(messages, "payment-lookup");
            if (toolContent == null) {
                return List.of(
                        new ModelEvent.ToolUse("payment-lookup", "payment_profile_lookup", Map.of("message", latestUserText(messages))),
                        new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
            }
            return List.of(
                    new ModelEvent.TextDelta("payment-agent が " + toolContent.getOrDefault("method", "登録済みカード")
                            + " を選択しました。" + toolContent.getOrDefault("note", "お支払いの確認が完了しました。") + ""),
                    new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
        }

        private Map<String, Object> latestToolContent(List<Message> messages, String toolUseId) {
            for (int index = messages.size() - 1; index >= 0; index--) {
                Message message = messages.get(index);
                for (ContentBlock block : message.content()) {
                    if (block instanceof ContentBlock.ToolResult result
                            && toolUseId.equals(result.toolUseId())
                            && result.content() instanceof Map<?, ?> content) {
                        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
                        content.forEach((key, value) -> values.put(String.valueOf(key), value));
                        return values;
                    }
                }
            }
            return null;
        }

        private String latestUserText(List<Message> messages) {
            for (int index = messages.size() - 1; index >= 0; index--) {
                Message message = messages.get(index);
                if (message.role() != Message.Role.USER) {
                    continue;
                }
                for (ContentBlock block : message.content()) {
                    if (block instanceof ContentBlock.Text text) {
                        return text.text().replace("message=", "");
                    }
                }
            }
            return "";
        }
    }
}

record PaymentPrepareRequest(String sessionId, String message, BigDecimal total, boolean confirmRequested) {}

record PaymentPrepareResponse(
        String service,
        String agent,
        String headline,
        String summary,
        String selectedMethod,
        BigDecimal total,
        String paymentStatus,
        boolean charged,
        String authorizationId) {}

record PaymentProfile(String methodCode, String methodLabel, String note) {}