package com.mahitotsu.arachne.samples.domainseparation.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.mahitotsu.arachne.samples.domainseparation.tool.AccountOperationDelegationTool;
import com.mahitotsu.arachne.samples.domainseparation.tool.AccountSystemTool;
import com.mahitotsu.arachne.strands.model.Model;
import com.mahitotsu.arachne.strands.model.ModelEvent;
import com.mahitotsu.arachne.strands.model.ToolSelection;
import com.mahitotsu.arachne.strands.model.ToolSpec;
import com.mahitotsu.arachne.strands.skills.Skill;
import com.mahitotsu.arachne.strands.skills.SkillParser;
import com.mahitotsu.arachne.strands.tool.annotation.AnnotationToolScanner;
import com.mahitotsu.arachne.strands.tool.annotation.DiscoveredTool;
import com.mahitotsu.arachne.strands.types.ContentBlock;
import com.mahitotsu.arachne.strands.types.Message;

@Configuration(proxyBeanMethods = false)
public class DomainSeparationConfiguration {

	@Bean
	@ConditionalOnProperty(prefix = "sample.domain-separation.model", name = "mode", havingValue = "deterministic", matchIfMissing = true)
	Model domainSeparationModel(ObjectMapper objectMapper) {
		return new DomainSeparationModel(objectMapper);
	}

	@Bean
	PlatformTransactionManager domainSeparationTransactionManager() {
		return new NoOpTransactionManager();
	}

	@Bean(name = "arachneDiscoveredTools")
	List<DiscoveredTool> domainSeparationDiscoveredTools(
			AnnotationToolScanner annotationToolScanner,
			AccountOperationDelegationTool accountOperationDelegationTool,
			AccountSystemTool accountSystemTool) {
		return annotationToolScanner.scanDiscoveredTools(List.of(accountOperationDelegationTool, accountSystemTool));
	}

	@Bean(name = "arachneDiscoveredSkills")
	List<Skill> domainSeparationDiscoveredSkills() {
		return List.of();
	}

	@Bean(name = "domainSeparationCoordinatorSkills")
	List<Skill> domainSeparationCoordinatorSkills(SkillParser skillParser) {
		return List.of(
				parseSkill(skillParser, "skills/account-creation/SKILL.md"),
				parseSkill(skillParser, "skills/password-reset-support/SKILL.md"),
				parseSkill(skillParser, "skills/account-unlock/SKILL.md"),
				parseSkill(skillParser, "skills/account-deletion/SKILL.md"));
	}

	private Skill parseSkill(SkillParser skillParser, String path) {
		return skillParser.parse(new ClassPathResource(path));
	}

	private static final class DomainSeparationModel implements Model {

		private final ObjectMapper objectMapper;

		private DomainSeparationModel(ObjectMapper objectMapper) {
			this.objectMapper = objectMapper;
		}

		@Override
		public Iterable<ModelEvent> converse(
				List<Message> messages,
				List<ToolSpec> tools,
				String systemPrompt,
				ToolSelection toolSelection) {
			return isCoordinator(tools, systemPrompt)
					? coordinatorResponse(messages)
					: executorResponse(messages);
		}

		@Override
		public Iterable<ModelEvent> converse(List<Message> messages, List<ToolSpec> tools) {
			return converse(messages, tools, null, null);
		}

		private Iterable<ModelEvent> coordinatorResponse(List<Message> messages) {
			Map<String, String> request = parsePrompt(firstUserText(messages));
			Map<String, String> latestPrompt = parsePrompt(latestUserText(messages));
			List<ContentBlock.ToolResult> toolResults = toolResults(messages);
			if (firstToolResultByType(toolResults, "skill_activation") == null) {
				return List.of(
						new ModelEvent.ToolUse(
								"coordinator-activate-skill",
								"activate_skill",
								Map.of("name", skillName(request.get("operationType")))),
						new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(8, 4)));
			}

			if (firstToolResultByPhase(toolResults, "preparation") == null) {
				return List.of(
						new ModelEvent.ToolUse("coordinator-prepare", "prepare_account_operation", request),
						new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(10, 4)));
			}

			if (firstToolResultByPhase(toolResults, "execution") == null) {
				return List.of(
						new ModelEvent.ToolUse("coordinator-execute", "execute_account_operation", request),
						new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(12, 4)));
			}

			Map<String, Object> preparation = firstToolResultByPhase(toolResults, "preparation");
			String preparedStatus = String.valueOf(preparation.get("preparedStatus"));
			if (!"LOCKED".equals(preparedStatus)) {
				return List.of(
						new ModelEvent.TextDelta("Preparation completed without an executable unlock path."),
						new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(12, 4)));
			}

			if ("resume".equalsIgnoreCase(latestPrompt.get("mode"))
					&& !Boolean.parseBoolean(latestPrompt.getOrDefault("approvalApproved", "false"))) {
				return List.of(
						new ModelEvent.TextDelta("Approval rejected. Workflow closed without execution."),
						new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(12, 4)));
			}

			if (firstToolResultByPhase(toolResults, "execution") == null) {
				return List.of(
						new ModelEvent.ToolUse("coordinator-execute", "execute_account_operation", request),
						new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(12, 4)));
			}

			return List.of(
					new ModelEvent.TextDelta("Workflow completed."),
					new ModelEvent.Metadata("end_turn", new ModelEvent.Usage(16, 6)));
		}

		private Iterable<ModelEvent> executorResponse(List<Message> messages) {
			Map<String, String> prompt = parsePrompt(firstUserText(messages));
			List<ContentBlock.ToolResult> toolResults = toolResults(messages);
			String mode = prompt.get("mode");

			if (toolResults.isEmpty()) {
				String toolName = "prepare".equals(mode) ? "find_account" : "unlock_account";
				return List.of(
						new ModelEvent.ToolUse("executor-" + mode, toolName, prompt),
						new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(8, 3)));
			}

			Map<String, Object> toolResult = contentAsMap(toolResults.getLast().content());
			if ("prepare".equals(mode)) {
				Map<String, Object> structured = new LinkedHashMap<>();
				structured.put("phase", "preparation");
				structured.put("operationType", prompt.get("operationType"));
				structured.put("accountId", prompt.get("accountId"));
				structured.put("preparedStatus", toolResult.get("currentStatus"));
				structured.put("preparedSummary", "Account is locked and can be unlocked safely.");
				structured.put("authorizedOperatorId", toolResult.get("observedOperatorId"));
				return List.of(
						new ModelEvent.ToolUse("executor-prepare-summary", "structured_output", structured),
						new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(11, 5)));
			}

			Map<String, Object> structured = new LinkedHashMap<>();
			structured.put("phase", "execution");
			structured.put("operationType", prompt.get("operationType"));
			structured.put("accountId", prompt.get("accountId"));
			structured.put("outcome", toolResult.get("outcome"));
			structured.put("auditMessage", toolResult.get("auditMessage"));
			structured.put("authorizedOperatorId", toolResult.get("observedOperatorId"));
			return List.of(
					new ModelEvent.ToolUse("executor-execute-summary", "structured_output", structured),
					new ModelEvent.Metadata("tool_use", new ModelEvent.Usage(11, 5)));
		}

		private boolean isCoordinator(List<ToolSpec> tools, String systemPrompt) {
			return tools.stream().map(ToolSpec::name).anyMatch("prepare_account_operation"::equals)
					|| (systemPrompt != null && systemPrompt.contains("operations coordinator"));
		}

		private List<ContentBlock.ToolResult> toolResults(List<Message> messages) {
			return messages.stream()
					.flatMap(message -> message.content().stream())
					.filter(ContentBlock.ToolResult.class::isInstance)
					.map(ContentBlock.ToolResult.class::cast)
					.toList();
		}

		private Map<String, Object> firstToolResultByType(List<ContentBlock.ToolResult> toolResults, String type) {
			for (ContentBlock.ToolResult toolResult : toolResults) {
				Map<String, Object> content = contentAsMap(toolResult.content());
				if (type.equals(content.get("type"))) {
					return content;
				}
			}
			return null;
		}

		private Map<String, Object> firstToolResultByPhase(List<ContentBlock.ToolResult> toolResults, String phase) {
			for (ContentBlock.ToolResult toolResult : toolResults) {
				Map<String, Object> content = contentAsMap(toolResult.content());
				if (phase.equals(content.get("phase"))) {
					return content;
				}
			}
			return null;
		}

		private String firstUserText(List<Message> messages) {
			return messages.stream()
					.filter(message -> message.role() == Message.Role.USER)
					.flatMap(message -> message.content().stream())
					.filter(ContentBlock.Text.class::isInstance)
					.map(ContentBlock.Text.class::cast)
					.map(ContentBlock.Text::text)
					.findFirst()
					.orElseThrow(() -> new IllegalStateException("Expected a text prompt from the user."));
		}

		private String latestUserText(List<Message> messages) {
			List<String> userTexts = messages.stream()
					.filter(message -> message.role() == Message.Role.USER)
					.flatMap(message -> message.content().stream())
					.filter(ContentBlock.Text.class::isInstance)
					.map(ContentBlock.Text.class::cast)
					.map(ContentBlock.Text::text)
					.toList();
			if (userTexts.isEmpty()) {
				throw new IllegalStateException("Expected a text prompt from the user.");
			}
			return userTexts.getLast();
		}

		private Map<String, String> parsePrompt(String prompt) {
			Map<String, String> values = new LinkedHashMap<>();
			for (String line : prompt.split("\\R")) {
				int separator = line.indexOf('=');
				if (separator <= 0) {
					continue;
				}
				values.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
			}
			return values;
		}

		private String skillName(String operationType) {
			if (operationType == null || operationType.isBlank()) {
				throw new IllegalStateException("Expected operationType in the coordinator request.");
			}
			return operationType.toLowerCase().replace('_', '-');
		}

		@SuppressWarnings("unchecked")
		private Map<String, Object> contentAsMap(Object content) {
			if (content == null || content instanceof CharSequence || content instanceof Number || content instanceof Boolean) {
				return Map.of();
			}
			if (content instanceof Map<?, ?> map) {
				Map<String, Object> values = new LinkedHashMap<>();
				map.forEach((key, value) -> values.put(String.valueOf(key), value));
				return values;
			}
			return objectMapper.convertValue(content, Map.class);
		}
	}

	private static final class NoOpTransactionManager extends AbstractPlatformTransactionManager {

		@Override
		@org.springframework.lang.NonNull
		protected Object doGetTransaction() {
			return new Object();
		}

		@Override
		protected void doBegin(@org.springframework.lang.NonNull Object transaction,
				@org.springframework.lang.NonNull TransactionDefinition definition) {
			// Sample-local no-op transaction boundary for deterministic mutation tools.
		}

		@Override
		protected void doCommit(@org.springframework.lang.NonNull DefaultTransactionStatus status) {
			// No external resource to commit in the deterministic sample.
		}

		@Override
		protected void doRollback(@org.springframework.lang.NonNull DefaultTransactionStatus status) {
			// No external resource to roll back in the deterministic sample.
		}
	}
}