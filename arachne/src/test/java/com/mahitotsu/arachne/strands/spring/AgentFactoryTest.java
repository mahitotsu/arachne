package com.mahitotsu.arachne.strands.spring;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.springframework.session.MapSessionRepository;

import com.mahitotsu.arachne.strands.agent.Agent;
import com.mahitotsu.arachne.strands.hooks.Plugin;
import com.mahitotsu.arachne.strands.model.Model;
import com.mahitotsu.arachne.strands.model.ModelEvent;
import com.mahitotsu.arachne.strands.model.ModelThrottledException;
import com.mahitotsu.arachne.strands.model.bedrock.BedrockModel;
import com.mahitotsu.arachne.strands.model.retry.ExponentialBackoffRetryStrategy;
import com.mahitotsu.arachne.strands.session.SpringSessionManager;
import com.mahitotsu.arachne.strands.skills.Skill;
import com.mahitotsu.arachne.strands.steering.Guide;
import com.mahitotsu.arachne.strands.steering.Interrupt;
import com.mahitotsu.arachne.strands.steering.Proceed;
import com.mahitotsu.arachne.strands.steering.SteeringHandler;
import com.mahitotsu.arachne.strands.tool.ExecutionContextPropagation;
import com.mahitotsu.arachne.strands.tool.Tool;
import com.mahitotsu.arachne.strands.tool.ToolResult;
import com.mahitotsu.arachne.strands.tool.annotation.DiscoveredTool;
import com.mahitotsu.arachne.strands.tool.annotation.StrandsTool;
import com.mahitotsu.arachne.strands.tool.builtin.CurrentTimeTool;

class AgentFactoryTest {

    @Test
    void buildFallsBackToConfiguredBedrockModel() {
        ArachneProperties properties = new ArachneProperties();
        properties.getModel().setId("jp.amazon.nova-2-lite-v1:0");
        properties.getModel().setRegion("ap-northeast-1");

        Agent agent = new AgentFactory(properties).builder().build();

        assertThat(agent.getModel()).isInstanceOf(BedrockModel.class);
        assertThat(((BedrockModel) agent.getModel()).getModelId()).isEqualTo("jp.amazon.nova-2-lite-v1:0");
        assertThat(((BedrockModel) agent.getModel()).getRegion()).isEqualTo("ap-northeast-1");
    }

    @Test
    void createDefaultModelUsesConfiguredRegionWhenModelIdIsBlank() {
        ArachneProperties.ModelProperties modelProperties = new ArachneProperties.ModelProperties();
        modelProperties.setId("   ");
        modelProperties.setRegion("us-west-2");
        modelProperties.getBedrock().getCache().setSystemPrompt(true);

        BedrockModel model = (BedrockModel) AgentFactory.createDefaultModel(modelProperties);

        assertThat(model.getModelId()).isEqualTo(BedrockModel.DEFAULT_MODEL_ID);
        assertThat(model.getRegion()).isEqualTo("us-west-2");
        assertThat(model.getPromptCaching().systemPrompt()).isTrue();
        assertThat(model.getPromptCaching().tools()).isFalse();
    }

    @Test
    void createDefaultModelUsesFrameworkDefaultsWhenCoordinatesAreBlank() {
        ArachneProperties.ModelProperties modelProperties = new ArachneProperties.ModelProperties();
        modelProperties.setId(" ");
        modelProperties.setRegion(" ");
        modelProperties.getBedrock().getCache().setTools(true);

        BedrockModel model = (BedrockModel) AgentFactory.createDefaultModel(modelProperties);

        assertThat(model.getModelId()).isEqualTo(BedrockModel.DEFAULT_MODEL_ID);
        assertThat(model.getRegion()).isEqualTo(BedrockModel.DEFAULT_REGION);
        assertThat(model.getPromptCaching().systemPrompt()).isFalse();
        assertThat(model.getPromptCaching().tools()).isTrue();
    }

    @Test
    void buildPrefersInjectedDefaultModelBean() {
        ArachneProperties properties = new ArachneProperties();
        Model model = (messages, tools) -> List.of(
                new ModelEvent.TextDelta("ok"),
                new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));

        Agent agent = new AgentFactory(properties, model).builder().build();

        assertThat(agent.getModel()).isSameAs(model);
    }

    @Test
    void buildRejectsUnsupportedConfiguredProvider() {
        ArachneProperties properties = new ArachneProperties();
        properties.getModel().setProvider("openai");

        assertThatThrownBy(() -> new AgentFactory(properties).builder().build())
                .isInstanceOf(UnsupportedModelProviderException.class)
                .hasMessageContaining("currently supports bedrock only");
    }

    @Test
    void buildIncludesDiscoveredAnnotationTools() {
        ArachneProperties properties = new ArachneProperties();
        Model model = (messages, tools) -> List.of(
                new ModelEvent.TextDelta("ok"),
                new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
        DiscoveredTool discoveredTool = new com.mahitotsu.arachne.strands.tool.annotation.AnnotationToolScanner()
            .scanDiscoveredTools(List.of(new ToolBean()))
                .getFirst();

        Agent agent = new AgentFactory(properties, model, List.of(discoveredTool)).builder().build();

        assertThat(agent.getTools()).extracting(tool -> tool.spec().name()).contains("helloTool");
    }

    @Test
    void buildCanRegisterPluginToolsAndHooks() {
        ArachneProperties properties = new ArachneProperties();
        Model model = (messages, tools) -> List.of(
                new ModelEvent.TextDelta("ok"),
                new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
        Plugin plugin = new Plugin() {
            @Override
            public List<Tool> tools() {
                return List.of(new Tool() {
                    @Override
                    public com.mahitotsu.arachne.strands.model.ToolSpec spec() {
                        return new com.mahitotsu.arachne.strands.model.ToolSpec("pluginTool", "plugin", null);
                    }

                    @Override
                    public ToolResult invoke(Object input) {
                        return ToolResult.success(null, "plugin");
                    }
                });
            }

            @Override
            public void registerHooks(com.mahitotsu.arachne.strands.hooks.HookRegistrar registrar) {
                registrar.beforeInvocation(event -> event.state().put("source", "plugin"));
            }
        };

        Agent agent = new AgentFactory(properties, model)
                .builder()
                .plugins(plugin)
                .build();

        agent.run("hello");

        assertThat(agent.getTools()).extracting(tool -> tool.spec().name()).contains("pluginTool");
        assertThat(agent.getState().get("source")).isEqualTo("plugin");
    }

    @Test
    void buildCanOptIntoSteeringHandlers() {
        ArachneProperties properties = new ArachneProperties();
        java.util.concurrent.atomic.AtomicInteger toolCalls = new java.util.concurrent.atomic.AtomicInteger();
        Model model = new Model() {
            private int calls;

            @Override
            public Iterable<ModelEvent> converse(List<com.mahitotsu.arachne.strands.types.Message> messages, List<com.mahitotsu.arachne.strands.model.ToolSpec> tools) {
                calls++;
                if (calls == 1) {
                    return List.of(
                            new ModelEvent.ToolUse("tool-1", "echo", java.util.Map.of("value", "a")),
                            new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
                }
                return List.of(
                        new ModelEvent.TextDelta("guided"),
                        new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
            }
        };
        Tool tool = new Tool() {
            @Override
            public com.mahitotsu.arachne.strands.model.ToolSpec spec() {
                return new com.mahitotsu.arachne.strands.model.ToolSpec("echo", "echo", null);
            }

            @Override
            public ToolResult invoke(Object input) {
                toolCalls.incrementAndGet();
                return ToolResult.success("tool-1", input);
            }
        };

        Agent agent = new AgentFactory(properties, model)
                .builder()
                .tools(tool)
                .steeringHandlers(new SteeringHandler() {
                    @Override
                    protected com.mahitotsu.arachne.strands.steering.ToolSteeringAction steerBeforeTool(com.mahitotsu.arachne.strands.hooks.BeforeToolCallEvent event) {
                        return new Guide("Use the cached answer instead.");
                    }
                })
                .build();

        assertThat(agent.run("hello").text()).isEqualTo("guided");
        assertThat(toolCalls).hasValue(0);
    }

    @Test
    void buildCanProceedThroughSteeringHandlers() {
        ArachneProperties properties = new ArachneProperties();
        AtomicInteger toolCalls = new AtomicInteger();
        Model model = new Model() {
            private int calls;

            @Override
            public Iterable<ModelEvent> converse(List<com.mahitotsu.arachne.strands.types.Message> messages, List<com.mahitotsu.arachne.strands.model.ToolSpec> tools) {
                calls++;
                if (calls == 1) {
                    return List.of(
                            new ModelEvent.ToolUse("tool-1", "echo", java.util.Map.of("value", "a")),
                            new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
                }
                return List.of(
                        new ModelEvent.TextDelta("executed"),
                        new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
            }
        };
        Tool tool = new Tool() {
            @Override
            public com.mahitotsu.arachne.strands.model.ToolSpec spec() {
                return new com.mahitotsu.arachne.strands.model.ToolSpec("echo", "echo", null);
            }

            @Override
            public ToolResult invoke(Object input) {
                toolCalls.incrementAndGet();
                return ToolResult.success("tool-1", input);
            }
        };

        Agent agent = new AgentFactory(properties, model)
                .builder()
                .tools(tool)
                .steeringHandlers(new SteeringHandler() {
                    @Override
                    protected com.mahitotsu.arachne.strands.steering.ToolSteeringAction steerBeforeTool(com.mahitotsu.arachne.strands.hooks.BeforeToolCallEvent event) {
                        return new Proceed("allow");
                    }
                })
                .build();

        assertThat(agent.run("hello").text()).isEqualTo("executed");
        assertThat(toolCalls).hasValue(1);
    }

    @Test
    void buildCanInterruptThroughSteeringHandlers() {
        ArachneProperties properties = new ArachneProperties();
        AtomicInteger toolCalls = new AtomicInteger();
        Model model = (messages, tools) -> List.of(
                new ModelEvent.ToolUse("tool-1", "echo", java.util.Map.of("value", "a")),
                new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
        Tool tool = new Tool() {
            @Override
            public com.mahitotsu.arachne.strands.model.ToolSpec spec() {
                return new com.mahitotsu.arachne.strands.model.ToolSpec("echo", "echo", null);
            }

            @Override
            public ToolResult invoke(Object input) {
                toolCalls.incrementAndGet();
                return ToolResult.success("tool-1", input);
            }
        };

        Agent agent = new AgentFactory(properties, model)
                .builder()
                .tools(tool)
                .steeringHandlers(new SteeringHandler() {
                    @Override
                    protected com.mahitotsu.arachne.strands.steering.ToolSteeringAction steerBeforeTool(com.mahitotsu.arachne.strands.hooks.BeforeToolCallEvent event) {
                        return new Interrupt("Operator approval required.");
                    }
                })
                .build();

        com.mahitotsu.arachne.strands.agent.AgentResult result = agent.run("hello");

        assertThat(result.interrupted()).isTrue();
        assertThat(result.stopReason()).isEqualTo("interrupt");
        assertThat(toolCalls).hasValue(0);
        assertThat(result.interrupts()).singleElement().satisfies(interrupt -> {
            assertThat(interrupt.name()).isEqualTo("steering_input_echo");
            assertThat(interrupt.reason()).isEqualTo(java.util.Map.of("message", "Operator approval required."));
            assertThat(interrupt.toolName()).isEqualTo("echo");
        });
    }

    @Test
    void buildIncludesDiscoveredSkillsInCatalogAndActivationTool() {
        ArachneProperties properties = new ArachneProperties();
        properties.getAgent().setSystemPrompt("Base system prompt");
        RecordingSystemPromptModel model = new RecordingSystemPromptModel();
        Skill discoveredSkill = new Skill(
                "release-checklist",
                "Use this skill when preparing a release.",
                "Run mvn test before merging.");

        Agent agent = new AgentFactory(
                properties,
                model,
                List.of(),
                List.of(),
                List.of(discoveredSkill),
                com.mahitotsu.arachne.strands.tool.BeanValidationSupport.defaultValidator(),
                null,
                null,
                new com.fasterxml.jackson.databind.ObjectMapper(),
                null)
                .builder()
                .build();

        agent.run("prepare release");

        assertThat(agent.getTools()).extracting(tool -> tool.spec().name()).contains("activate_skill");
        assertThat(model.systemPrompt()).contains("Base system prompt");
        assertThat(model.systemPrompt()).contains("release-checklist");
        assertThat(model.systemPrompt()).contains("<available_skills>");
        assertThat(model.systemPrompt()).doesNotContain("Run mvn test before merging.");
    }

    @Test
    void buildRuntimeSkillsOverrideDiscoveredSkillsWithSameName() {
        ArachneProperties properties = new ArachneProperties();
        SkillSessionRestoreModel model = new SkillSessionRestoreModel();
        Skill discoveredSkill = new Skill(
                "release-checklist",
                "Discovered guidance",
                "Run the discovered checklist.");
        Skill runtimeSkill = new Skill(
                "release-checklist",
                "Runtime guidance",
                "Run the runtime checklist first.");

        Agent agent = new AgentFactory(
                properties,
                model,
                List.of(),
                List.of(),
                List.of(discoveredSkill),
                com.mahitotsu.arachne.strands.tool.BeanValidationSupport.defaultValidator(),
                null,
                null,
                new com.fasterxml.jackson.databind.ObjectMapper(),
                null)
                .builder()
                .skills(runtimeSkill)
                .build();

        assertThat(agent.run("prepare release").text()).isEqualTo("loaded");
        assertThat(agent.run("what should I do next?").text()).isEqualTo("restored");
        assertThat(agent.getTools()).extracting(tool -> tool.spec().name()).contains("activate_skill");
        assertThat(model.systemPrompts()).hasSize(3);
        assertThat(model.systemPrompts().getLast()).contains("Run the runtime checklist first.");
        assertThat(model.systemPrompts().getLast()).doesNotContain("Run the discovered checklist.");
    }

    @Test
    void buildCanFilterDiscoveredToolsByQualifier() {
        ArachneProperties properties = new ArachneProperties();
        Model model = (messages, tools) -> List.of(
                new ModelEvent.TextDelta("ok"),
                new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
        List<DiscoveredTool> discoveredTools = new com.mahitotsu.arachne.strands.tool.annotation.AnnotationToolScanner()
                .scanDiscoveredTools(List.of(new PlannerToolBean(), new SupportToolBean()));

        Agent agent = new AgentFactory(properties, model, discoveredTools)
                .builder()
                .toolQualifiers("planner")
                .build();

        assertThat(agent.getTools()).extracting(tool -> tool.spec().name()).containsExactly("plannerTool");
    }

    @Test
    void buildCanDisableDiscoveredTools() {
        ArachneProperties properties = new ArachneProperties();
        Model model = (messages, tools) -> List.of(
                new ModelEvent.TextDelta("ok"),
                new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
        List<DiscoveredTool> discoveredTools = new com.mahitotsu.arachne.strands.tool.annotation.AnnotationToolScanner()
                .scanDiscoveredTools(List.of(new PlannerToolBean()));

        Agent agent = new AgentFactory(properties, model, discoveredTools)
                .builder()
                .useDiscoveredTools(false)
                .build();

        assertThat(agent.getTools()).isEmpty();
    }

    @Test
    void buildCombinesDiscoveredPluginAndExplicitTools() {
        ArachneProperties properties = new ArachneProperties();
        Model model = echoModel();
        DiscoveredTool discoveredTool = new com.mahitotsu.arachne.strands.tool.annotation.AnnotationToolScanner()
                .scanDiscoveredTools(List.of(new PlannerToolBean()))
                .getFirst();
        Plugin plugin = new Plugin() {
            @Override
            public List<Tool> tools() {
                return List.of(namedTool("pluginTool"));
            }
        };

        Agent agent = new AgentFactory(properties, model, List.of(discoveredTool))
                .builder()
                .plugins(plugin)
                .tools(namedTool("runtimeTool"))
                .build();

        assertThat(agent.getTools()).extracting(tool -> tool.spec().name())
                .containsExactly("plannerTool", "pluginTool", "runtimeTool");
    }

            @Test
            void buildIncludesDefaultBuiltInTools() {
            ArachneProperties properties = new ArachneProperties();
            Agent agent = new AgentFactory(
                properties,
                echoModel(),
                new BuiltInToolRegistry(List.of(builtInTool(new CurrentTimeTool(), true, "read-only", "utility"))),
                List.of(),
                List.of(),
                List.of(),
                com.mahitotsu.arachne.strands.tool.BeanValidationSupport.defaultValidator(),
                null,
                null,
                new com.fasterxml.jackson.databind.ObjectMapper(),
                null)
                .builder()
                .build();

            assertThat(agent.getTools()).extracting(tool -> tool.spec().name()).contains("current_time");
            }

            @Test
            void buildCanDisableInheritedBuiltInTools() {
            ArachneProperties properties = new ArachneProperties();
            Agent agent = new AgentFactory(
                properties,
                echoModel(),
                new BuiltInToolRegistry(List.of(builtInTool(new CurrentTimeTool(), true, "read-only", "utility"))),
                List.of(),
                List.of(),
                List.of(),
                com.mahitotsu.arachne.strands.tool.BeanValidationSupport.defaultValidator(),
                null,
                null,
                new com.fasterxml.jackson.databind.ObjectMapper(),
                null)
                .builder()
                .inheritBuiltInTools(false)
                .build();

            assertThat(agent.getTools()).isEmpty();
            }

            @Test
            void buildNamedAgentCanSelectBuiltInToolGroups() {
            ArachneProperties properties = new ArachneProperties();
            ArachneProperties.NamedAgentProperties namedAgent = new ArachneProperties.NamedAgentProperties();
            namedAgent.getBuiltIns().setInheritDefaults(false);
            namedAgent.getBuiltIns().setToolGroups(List.of("resource"));
            properties.getAgents().put("reader", namedAgent);

            Agent agent = new AgentFactory(
                properties,
                echoModel(),
                new BuiltInToolRegistry(List.of(
                    builtInTool(new CurrentTimeTool(), true, "utility"),
                    builtInTool(namedTool("resource_reader"), true, "resource"))),
                List.of(),
                List.of(),
                List.of(),
                com.mahitotsu.arachne.strands.tool.BeanValidationSupport.defaultValidator(),
                null,
                null,
                new com.fasterxml.jackson.databind.ObjectMapper(),
                null)
                .builder("reader")
                .build();

            assertThat(agent.getTools()).extracting(tool -> tool.spec().name()).containsExactly("resource_reader");
            }

    @Test
    void buildUsesConfiguredSessionIdAndRestoresAcrossAgents() {
        ArachneProperties properties = new ArachneProperties();
        properties.getAgent().getSession().setId("shared-session");
        Model model = (messages, tools) -> List.of(
                new ModelEvent.TextDelta("ok"),
                new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
        SpringSessionManager sessionManager = new SpringSessionManager(
            new MapSessionRepository(new java.util.concurrent.ConcurrentHashMap<>()));

        AgentFactory factory = new AgentFactory(properties, model, List.of(), com.mahitotsu.arachne.strands.tool.BeanValidationSupport.defaultValidator(), sessionManager);
        Agent first = factory.builder().build();
        first.getState().put("topic", "travel");
        first.run("hello");

        Agent restored = factory.builder().build();

        assertThat(restored.getMessages()).hasSize(2);
        assertThat(restored.getState().get("topic")).isEqualTo("travel");
    }

    @Test
    void buildRestoresActivatedSkillsAcrossAgents() {
        ArachneProperties properties = new ArachneProperties();
        properties.getAgent().getSession().setId("skills-session");
        Skill skill = new Skill(
                "release-checklist",
                "Use this skill when preparing a release.",
                "Run mvn test before merging.");
        SkillSessionRestoreModel model = new SkillSessionRestoreModel();
        SpringSessionManager sessionManager = new SpringSessionManager(
                new MapSessionRepository(new java.util.concurrent.ConcurrentHashMap<>()));

        AgentFactory factory = new AgentFactory(
                properties,
                model,
                List.of(),
                List.of(),
                List.of(skill),
                com.mahitotsu.arachne.strands.tool.BeanValidationSupport.defaultValidator(),
                sessionManager,
                null,
                new com.fasterxml.jackson.databind.ObjectMapper(),
                null);

        Agent first = factory.builder().build();
        assertThat(first.run("prepare release").text()).isEqualTo("loaded");

        Agent restored = factory.builder().build();
        assertThat(restored.getState().get("arachne.skills.loaded")).isEqualTo(List.of("release-checklist"));
        assertThat(restored.run("what should I do next?").text()).isEqualTo("restored");

        assertThat(model.systemPrompts()).hasSize(3);
        assertThat(model.systemPrompts().get(0)).contains("<available_skills>");
        assertThat(model.systemPrompts().get(0)).doesNotContain("<active_skills>");
        assertThat(model.systemPrompts().get(1)).doesNotContain("<active_skills>");
        assertThat(model.systemPrompts().get(2)).contains("<active_skills>");
        assertThat(model.systemPrompts().get(2)).contains("Run mvn test before merging.");
    }

    @Test
    void buildCreatesIndependentRuntimeInstances() {
        ArachneProperties properties = new ArachneProperties();
        AgentFactory factory = new AgentFactory(properties, echoModel());

        Agent first = factory.builder().build();
        Agent second = factory.builder().build();

        first.getState().put("topic", "travel");
        first.run("first");
        second.run("second");

        assertThat(first.getMessages()).hasSize(2);
        assertThat(second.getMessages()).hasSize(2);
        assertThat(first.getMessages().getFirst().content().getFirst())
                .isEqualTo(com.mahitotsu.arachne.strands.types.ContentBlock.text("first"));
        assertThat(second.getMessages().getFirst().content().getFirst())
                .isEqualTo(com.mahitotsu.arachne.strands.types.ContentBlock.text("second"));
        assertThat(second.getState().get("topic")).isNull();
    }

    @Test
    void buildCanApplyExplicitRetryStrategy() {
        ArachneProperties properties = new ArachneProperties();
        AtomicInteger calls = new AtomicInteger();
        Model model = (messages, tools) -> {
            if (calls.incrementAndGet() < 3) {
                throw new ModelThrottledException("retry me");
            }
            return List.of(
                    new ModelEvent.TextDelta("ok"),
                    new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
        };

        Agent agent = new AgentFactory(properties, model)
                .builder()
                .retryStrategy(new ExponentialBackoffRetryStrategy(4, Duration.ZERO, Duration.ZERO))
                .build();

        assertThat(agent.run("hello").text()).isEqualTo("ok");
        assertThat(calls).hasValue(3);
    }

    @Test
    void buildCanConfigureDefaultStructuredOutputTypeAndPrompt() {
        ArachneProperties properties = new ArachneProperties();
        Agent agent = new AgentFactory(properties, structuredOutputModel())
                .builder()
                .structuredOutputType(WeatherSummary.class)
                .structuredOutputPrompt("Return strict JSON now.")
                .build();

        com.mahitotsu.arachne.strands.agent.AgentResult result = agent.run("東京の天気を返してください");

        assertThat(result.hasStructuredOutput()).isTrue();
        assertThat(result.structuredOutput(WeatherSummary.class).answer()).isEqualTo("Tokyo");
        assertThat(agent.getMessages().get(2)).isEqualTo(com.mahitotsu.arachne.strands.types.Message.user("Return strict JSON now."));
    }

    @Test
    void buildNamedAgentUsesNamedModelOverrides() {
        ArachneProperties properties = new ArachneProperties();
        properties.getModel().setId("jp.amazon.nova-2-lite-v1:0");
        properties.getModel().setRegion("ap-northeast-1");

        ArachneProperties.NamedAgentProperties analyst = new ArachneProperties.NamedAgentProperties();
        analyst.getModel().setId("us.amazon.nova-pro-v1:0");
        analyst.getModel().setRegion("us-east-1");
        properties.getAgents().put("analyst", analyst);

        Model sharedModel = (messages, tools) -> List.of(
                new ModelEvent.TextDelta("shared"),
                new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));

        Agent agent = new AgentFactory(properties, sharedModel).builder("analyst").build();

        assertThat(agent.getModel()).isInstanceOf(BedrockModel.class);
        assertThat(((BedrockModel) agent.getModel()).getModelId()).isEqualTo("us.amazon.nova-pro-v1:0");
        assertThat(((BedrockModel) agent.getModel()).getRegion()).isEqualTo("us-east-1");
    }

    @Test
    void buildNamedAgentUsesNamedToolQualifiersAndSessionId() {
        ArachneProperties properties = new ArachneProperties();
        properties.getAgent().getSession().setId("global-session");

        ArachneProperties.NamedAgentProperties planner = new ArachneProperties.NamedAgentProperties();
        planner.setToolQualifiers(List.of("planner"));
        planner.getSession().setId("planner-session");
        properties.getAgents().put("planner", planner);

        Model model = (messages, tools) -> List.of(
                new ModelEvent.TextDelta("ok"),
                new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
        List<DiscoveredTool> discoveredTools = new com.mahitotsu.arachne.strands.tool.annotation.AnnotationToolScanner()
                .scanDiscoveredTools(List.of(new PlannerToolBean(), new SupportToolBean()));
        SpringSessionManager sessionManager = new SpringSessionManager(
                new MapSessionRepository(new java.util.concurrent.ConcurrentHashMap<>()));

        AgentFactory factory = new AgentFactory(
                properties,
                model,
                discoveredTools,
                com.mahitotsu.arachne.strands.tool.BeanValidationSupport.defaultValidator(),
                sessionManager);

        Agent first = factory.builder("planner").build();
        first.getState().put("topic", "travel");
        first.run("hello");

        Agent restored = factory.builder("planner").build();

        assertThat(restored.getTools()).extracting(tool -> tool.spec().name()).containsExactly("plannerTool");
        assertThat(restored.getMessages()).hasSize(2);
        assertThat(restored.getState().get("topic")).isEqualTo("travel");
    }

    @Test
    void buildNamedAgentCanDisableDiscoveredToolsWhileMergingBuiltInSelections() {
        ArachneProperties properties = new ArachneProperties();
        properties.getAgent().getBuiltIns().setToolNames(List.of("current_time"));

        ArachneProperties.NamedAgentProperties namedAgent = new ArachneProperties.NamedAgentProperties();
        namedAgent.setUseDiscoveredTools(false);
        namedAgent.getBuiltIns().setToolGroups(List.of("resource"));
        properties.getAgents().put("reader", namedAgent);

        List<DiscoveredTool> discoveredTools = new com.mahitotsu.arachne.strands.tool.annotation.AnnotationToolScanner()
                .scanDiscoveredTools(List.of(new PlannerToolBean()));

        Agent agent = new AgentFactory(
                properties,
                echoModel(),
                new BuiltInToolRegistry(List.of(
                        builtInTool(new CurrentTimeTool(), false, "utility"),
                        builtInTool(namedTool("resource_reader"), false, "resource"))),
                discoveredTools,
                List.of(),
                List.of(),
                com.mahitotsu.arachne.strands.tool.BeanValidationSupport.defaultValidator(),
                null,
                null,
                new com.fasterxml.jackson.databind.ObjectMapper(),
                null)
                .builder("reader")
                .build();

        assertThat(agent.getTools()).extracting(tool -> tool.spec().name())
                .containsExactly("current_time", "resource_reader");
    }

    @Test
    void buildNamedAgentKeepsInjectedDefaultModelWithoutModelOverride() {
        ArachneProperties properties = new ArachneProperties();
        ArachneProperties.NamedAgentProperties analyst = new ArachneProperties.NamedAgentProperties();
        analyst.getSession().setId("analyst-session");
        properties.getAgents().put("analyst", analyst);

        Model sharedModel = (messages, tools) -> List.of(
                new ModelEvent.TextDelta("shared"),
                new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));

        Agent agent = new AgentFactory(properties, sharedModel).builder("analyst").build();

        assertThat(agent.getModel()).isSameAs(sharedModel);
    }

    @Test
    void buildNamedAgentCanOverrideBedrockCacheWithoutReplacingInheritedModelCoordinates() {
        ArachneProperties properties = new ArachneProperties();
        properties.getModel().setId("jp.amazon.nova-2-lite-v1:0");
        properties.getModel().setRegion("ap-northeast-1");

        ArachneProperties.NamedAgentProperties cached = new ArachneProperties.NamedAgentProperties();
        cached.getModel().getBedrock().getCache().setSystemPrompt(true);
        cached.getModel().getBedrock().getCache().setTools(true);
        properties.getAgents().put("cached", cached);

        Model sharedModel = (messages, tools) -> List.of(
                new ModelEvent.TextDelta("shared"),
                new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));

        Agent agent = new AgentFactory(properties, sharedModel).builder("cached").build();

        assertThat(agent.getModel()).isInstanceOf(BedrockModel.class);
        assertThat(agent.getModel()).isNotSameAs(sharedModel);
        BedrockModel bedrockModel = (BedrockModel) agent.getModel();
        assertThat(bedrockModel.getModelId()).isEqualTo("jp.amazon.nova-2-lite-v1:0");
        assertThat(bedrockModel.getRegion()).isEqualTo("ap-northeast-1");
        assertThat(bedrockModel.getPromptCaching().systemPrompt()).isTrue();
        assertThat(bedrockModel.getPromptCaching().tools()).isTrue();
    }

    @Test
    void buildNamedAgentCanEnableRetryWithoutGlobalRetry() {
        ArachneProperties properties = new ArachneProperties();

        ArachneProperties.NamedAgentProperties resilient = new ArachneProperties.NamedAgentProperties();
        resilient.getRetry().setEnabled(true);
        resilient.getRetry().setMaxAttempts(4);
        resilient.getRetry().setInitialDelay(Duration.ZERO);
        resilient.getRetry().setMaxDelay(Duration.ZERO);
        properties.getAgents().put("resilient", resilient);

        AtomicInteger calls = new AtomicInteger();
        Model model = (messages, tools) -> {
            if (calls.incrementAndGet() < 3) {
                throw new ModelThrottledException("retry me");
            }
            return List.of(
                    new ModelEvent.TextDelta("ok"),
                    new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
        };

        Agent agent = new AgentFactory(properties, model).builder("resilient").build();

        assertThat(agent.run("hello").text()).isEqualTo("ok");
        assertThat(calls).hasValue(3);
    }

    @Test
    void buildNamedAgentCanDisableGlobalRetry() {
        ArachneProperties properties = new ArachneProperties();

        ArachneProperties.NamedAgentProperties nonRetrying = new ArachneProperties.NamedAgentProperties();
        nonRetrying.getRetry().setEnabled(false);
        properties.getAgents().put("nonRetrying", nonRetrying);

        AtomicInteger calls = new AtomicInteger();
        Model model = (messages, tools) -> {
            calls.incrementAndGet();
            throw new ModelThrottledException("retry me");
        };

        AgentFactory factory = new AgentFactory(
                properties,
                model,
                List.of(),
                com.mahitotsu.arachne.strands.tool.BeanValidationSupport.defaultValidator(),
                null,
                new ExponentialBackoffRetryStrategy(4, Duration.ZERO, Duration.ZERO));

        Agent agent = factory.builder("nonRetrying").build();

        assertThatThrownBy(() -> agent.run("hello"))
                .isInstanceOf(ModelThrottledException.class)
                .hasMessageContaining("retry me");
        assertThat(calls).hasValue(1);
    }

    @Test
    void buildNamedAgentRejectsUnknownConfiguration() {
        ArachneProperties properties = new ArachneProperties();

        AgentFactory factory = new AgentFactory(properties);

        assertThatThrownBy(() -> factory.builder("missing"))
                .isInstanceOf(NamedAgentNotFoundException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void buildCanApplyExecutionContextPropagation() {
        ArachneProperties properties = new ArachneProperties();
        ThreadLocal<String> context = new ThreadLocal<>();
        context.set("factory-context");

        try (java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(1)) {
            Tool tool = new Tool() {
                @Override
                public com.mahitotsu.arachne.strands.model.ToolSpec spec() {
                    return new com.mahitotsu.arachne.strands.model.ToolSpec(
                            "echo",
                            "echo",
                            com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode());
                }

                @Override
                public ToolResult invoke(Object input) {
                    return ToolResult.success(null, context.get());
                }
            };

            Model model = new Model() {
                private boolean firstCall = true;

                @Override
                public Iterable<ModelEvent> converse(
                        List<com.mahitotsu.arachne.strands.types.Message> messages,
                        List<com.mahitotsu.arachne.strands.model.ToolSpec> tools) {
                    if (firstCall) {
                        firstCall = false;
                        return List.of(
                                new ModelEvent.ToolUse("tool-1", "echo", java.util.Map.of()),
                                new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
                    }
                    return List.of(
                            new ModelEvent.TextDelta("done"),
                            new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
                }
            };

            ExecutionContextPropagation propagation = task -> {
                String captured = context.get();
                return () -> {
                    String previous = context.get();
                    context.set(captured);
                    try {
                        task.run();
                    } finally {
                        if (previous == null) {
                            context.remove();
                        } else {
                            context.set(previous);
                        }
                    }
                };
            };

            Agent agent = new AgentFactory(properties, model)
                    .builder()
                    .tools(tool)
                    .toolExecutionExecutor(executor)
                    .executionContextPropagation(propagation)
                    .build();

            assertThat(agent.run("hello").text()).isEqualTo("done");
        } finally {
            context.remove();
        }
    }

    static class ToolBean {

        @StrandsTool
        public String helloTool() {
            return "ok";
        }
    }

    static class PlannerToolBean {

        @StrandsTool(qualifiers = "planner")
        public String plannerTool() {
            return "planner";
        }
    }

    static class SupportToolBean {

        @StrandsTool(qualifiers = "support")
        public String supportTool() {
            return "support";
        }
    }

    private static Model echoModel() {
        return (messages, tools) -> {
            String prompt = messages.getLast().content().stream()
                    .filter(com.mahitotsu.arachne.strands.types.ContentBlock.Text.class::isInstance)
                    .map(com.mahitotsu.arachne.strands.types.ContentBlock.Text.class::cast)
                    .map(com.mahitotsu.arachne.strands.types.ContentBlock.Text::text)
                    .findFirst()
                    .orElse("(missing prompt)");
            return List.of(
                    new ModelEvent.TextDelta("Echo: " + prompt),
                    new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
        };
    }

    private static Model structuredOutputModel() {
        return new Model() {
            private int calls;

            @Override
            public Iterable<ModelEvent> converse(
                    List<com.mahitotsu.arachne.strands.types.Message> messages,
                    List<com.mahitotsu.arachne.strands.model.ToolSpec> tools) {
                throw new AssertionError("Structured output should use the extended overloads");
            }

            @Override
            public Iterable<ModelEvent> converse(
                    List<com.mahitotsu.arachne.strands.types.Message> messages,
                    List<com.mahitotsu.arachne.strands.model.ToolSpec> tools,
                    String systemPrompt) {
                calls++;
                return List.of(
                        new ModelEvent.TextDelta("Plain text draft"),
                        new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(10, 5)));
            }

            @Override
            public Iterable<ModelEvent> converse(
                    List<com.mahitotsu.arachne.strands.types.Message> messages,
                    List<com.mahitotsu.arachne.strands.model.ToolSpec> tools,
                    String systemPrompt,
                    com.mahitotsu.arachne.strands.model.ToolSelection toolSelection) {
                if (toolSelection == null) {
                    return converse(messages, tools, systemPrompt);
                }
                if (calls == 1) {
                    calls++;
                    return List.of(
                            new ModelEvent.ToolUse(
                                    "structured-1",
                                    toolSelection.toolName(),
                                    java.util.Map.of("answer", "Tokyo", "confidence", 0.9)),
                            new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(12, 4)));
                }
                return List.of(new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(13, 2)));
            }
        };
    }

    private static Tool namedTool(String name) {
        return new Tool() {
            @Override
            public com.mahitotsu.arachne.strands.model.ToolSpec spec() {
                return new com.mahitotsu.arachne.strands.model.ToolSpec(name, name, null);
            }

            @Override
            public ToolResult invoke(Object input) {
                return ToolResult.success(null, name);
            }
        };
    }

    private static BuiltInToolDefinition builtInTool(Tool tool, boolean includedByDefault, String... groups) {
        return new BuiltInToolDefinition(tool, includedByDefault, java.util.Set.of(groups));
    }

    record WeatherSummary(String answer, double confidence) {
    }

    private static final class RecordingSystemPromptModel implements Model {

        private String systemPrompt;

        @Override
        public Iterable<ModelEvent> converse(List<com.mahitotsu.arachne.strands.types.Message> messages, List<com.mahitotsu.arachne.strands.model.ToolSpec> tools) {
            throw new AssertionError("Expected the system-prompt-aware overload");
        }

        @Override
        public Iterable<ModelEvent> converse(
                List<com.mahitotsu.arachne.strands.types.Message> messages,
                List<com.mahitotsu.arachne.strands.model.ToolSpec> tools,
                String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return List.of(
                    new ModelEvent.TextDelta("ok"),
                    new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
        }

        String systemPrompt() {
            return systemPrompt;
        }
    }

    private static final class SkillSessionRestoreModel implements Model {

        private final java.util.ArrayList<String> systemPrompts = new java.util.ArrayList<>();

        @Override
        public Iterable<ModelEvent> converse(List<com.mahitotsu.arachne.strands.types.Message> messages, List<com.mahitotsu.arachne.strands.model.ToolSpec> tools) {
            throw new AssertionError("Expected the system-prompt-aware overload");
        }

        @Override
        public Iterable<ModelEvent> converse(
                List<com.mahitotsu.arachne.strands.types.Message> messages,
                List<com.mahitotsu.arachne.strands.model.ToolSpec> tools,
                String systemPrompt) {
            return converse(messages, tools, systemPrompt, null);
        }

        @Override
        public Iterable<ModelEvent> converse(
                List<com.mahitotsu.arachne.strands.types.Message> messages,
                List<com.mahitotsu.arachne.strands.model.ToolSpec> tools,
                String systemPrompt,
                com.mahitotsu.arachne.strands.model.ToolSelection toolSelection) {
            systemPrompts.add(systemPrompt);
            if (!hasSkillActivation(messages)) {
                return List.of(
                        new ModelEvent.ToolUse("skill-1", "activate_skill", java.util.Map.of("name", "release-checklist")),
                        new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(1, 1)));
            }
            String latestUserText = latestUserText(messages);
            String text = "what should I do next?".equals(latestUserText) ? "restored" : "loaded";
            return List.of(
                    new ModelEvent.TextDelta(text),
                    new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(1, 1)));
        }

        List<String> systemPrompts() {
            return List.copyOf(systemPrompts);
        }

        private boolean hasSkillActivation(List<com.mahitotsu.arachne.strands.types.Message> messages) {
            for (com.mahitotsu.arachne.strands.types.Message message : messages) {
                for (com.mahitotsu.arachne.strands.types.ContentBlock block : message.content()) {
                    if (block instanceof com.mahitotsu.arachne.strands.types.ContentBlock.ToolResult toolResult
                            && toolResult.content() instanceof java.util.Map<?, ?> content
                            && "skill_activation".equals(content.get("type"))) {
                        return true;
                    }
                }
            }
            return false;
        }

        private String latestUserText(List<com.mahitotsu.arachne.strands.types.Message> messages) {
            for (int index = messages.size() - 1; index >= 0; index--) {
                com.mahitotsu.arachne.strands.types.Message message = messages.get(index);
                if (message.role() != com.mahitotsu.arachne.strands.types.Message.Role.USER) {
                    continue;
                }
                for (com.mahitotsu.arachne.strands.types.ContentBlock block : message.content()) {
                    if (block instanceof com.mahitotsu.arachne.strands.types.ContentBlock.Text text) {
                        return text.text();
                    }
                }
            }
            return null;
        }
    }
}