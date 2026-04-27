package com.mahitotsu.arachne.samples.delivery.registryservice;

import java.util.List;

import org.springframework.stereotype.Service;

import com.mahitotsu.arachne.strands.spring.AgentFactory;
import com.mahitotsu.arachne.strands.tool.Tool;

@Service
class RegistryApplicationService {

    private static final String DISCOVERY_PROMPT = """
            あなたは capability-registry-agent です。
            capability_match ツールを使って、問い合わせ文に合う利用可能なサービス候補を探してください。
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

    RegistryServiceDescriptor register(RegistryRegistration request) {
        repository.register(request);
        return repository.describe(request.serviceName());
    }

    RegistryDiscoverResponse discover(RegistryDiscoverRequest request) {
        boolean availableOnly = request.availableOnly() == null || request.availableOnly();
        List<RegistryServiceDescriptor> matches = repository.discover(request.query(), availableOnly);
        String summary = agentFactory.builder()
                .systemPrompt(DISCOVERY_PROMPT)
                .tools(capabilityMatchTool)
                .build()
                .run("問い合わせ: " + request.query())
                .text();
        return new RegistryDiscoverResponse("registry-service", "capability-registry-agent", summary, matches);
    }

    List<RegistryServiceDescriptor> services() {
        return repository.services();
    }

    RegistryHealthResponse health() {
        return new RegistryHealthResponse(repository.health());
    }
}