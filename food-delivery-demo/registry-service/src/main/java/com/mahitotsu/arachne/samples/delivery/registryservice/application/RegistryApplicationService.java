package com.mahitotsu.arachne.samples.delivery.registryservice.application;

import static com.mahitotsu.arachne.samples.delivery.registryservice.domain.RegistryTypes.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.mahitotsu.arachne.samples.delivery.registryservice.infrastructure.RegistryRepository;
import com.mahitotsu.arachne.strands.agent.AgentResult;
import com.mahitotsu.arachne.strands.spring.AgentFactory;
import com.mahitotsu.arachne.strands.tool.Tool;

@Service
public class RegistryApplicationService {

    private static final String DISCOVERY_PROMPT = """
            あなたは capability-registry-agent です。
            capability-match を有効化し、最初に capability_match ツールを使って問い合わせ文に合う候補を確認してください。
            最終回答は structured_output で selectedServiceNames と summary を返してください。存在しないツール名やサービス名を作らないでください。
            選んでよい serviceName は capability_match が返した候補だけです。
            問い合わせが件数制約を含む場合は、その件数に絞ってください。
            クエリに最も直接一致する capability を優先し、曖昧なときだけ複数候補を返してください。
            回答は見つかったサービス名を簡潔に要約してください。
            """;

    private final RegistryRepository repository;
    private final AgentFactory agentFactory;
    private final Tool capabilityMatchTool;

    RegistryApplicationService(RegistryRepository repository, AgentFactory agentFactory, Tool capabilityMatchTool) {
        this.repository = repository;
        this.agentFactory = agentFactory;
        this.capabilityMatchTool = capabilityMatchTool;
    }

    public RegistryServiceDescriptor register(RegistryRegistration request) {
        repository.register(request);
        return repository.describe(request.serviceName());
    }

    public RegistryDiscoverResponse discover(RegistryDiscoverRequest request) {
        boolean availableOnly = request.availableOnly() == null || request.availableOnly();
        List<RegistryServiceDescriptor> matches = repository.discover(request.query(), availableOnly);
        AgentResult decisionResult = agentFactory.builder()
                .systemPrompt(DISCOVERY_PROMPT)
            .tools(capabilityMatchTool)
                .build()
            .run("問い合わせ: " + request.query(), RegistryDiscoveryDecision.class);
        RegistryDiscoveryDecision decision = decisionResult.structuredOutput(RegistryDiscoveryDecision.class);
        List<RegistryServiceDescriptor> selectedMatches = selectMatches(matches, decision.selectedServiceNames());
        return new RegistryDiscoverResponse("registry-service", "capability-registry-agent", decision.summary(), selectedMatches);
    }

    public List<RegistryServiceDescriptor> services() {
        return repository.services();
    }

    public RegistryHealthResponse health() {
        return new RegistryHealthResponse(repository.health());
    }

    private List<RegistryServiceDescriptor> selectMatches(List<RegistryServiceDescriptor> matches, List<String> selectedServiceNames) {
        if (selectedServiceNames == null || selectedServiceNames.isEmpty()) {
            return matches;
        }
        Map<String, RegistryServiceDescriptor> matchByServiceName = matches.stream()
                .collect(LinkedHashMap::new, (map, match) -> map.put(match.serviceName(), match), Map::putAll);
        List<RegistryServiceDescriptor> selected = selectedServiceNames.stream()
                .map(matchByServiceName::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        return selected.isEmpty() ? matches : selected;
    }
}