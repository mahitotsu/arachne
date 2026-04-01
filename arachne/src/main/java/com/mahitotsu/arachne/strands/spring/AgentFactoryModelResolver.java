package com.mahitotsu.arachne.strands.spring;

import com.mahitotsu.arachne.strands.model.Model;
import com.mahitotsu.arachne.strands.model.bedrock.BedrockModel;

final class AgentFactoryModelResolver {

    private AgentFactoryModelResolver() {
    }

    static Model createDefaultModel(ArachneProperties properties) {
        return createDefaultModel(properties.getModel());
    }

    static Model createDefaultModel(ArachneProperties.ModelProperties modelProperties) {
        String provider = modelProperties.getProvider();
        if (!hasText(provider) || !"bedrock".equalsIgnoreCase(provider)) {
            throw new UnsupportedModelProviderException(provider);
        }

        BedrockModel.PromptCaching promptCaching = new BedrockModel.PromptCaching(
                modelProperties.getBedrock().getCache().isSystemPrompt(),
                modelProperties.getBedrock().getCache().isTools());

        String modelId = modelProperties.getId();
        String region = modelProperties.getRegion();
        if (hasText(modelId)) {
            return new BedrockModel(modelId, region, promptCaching);
        }
        if (hasText(region)) {
            return new BedrockModel(BedrockModel.DEFAULT_MODEL_ID, region, promptCaching);
        }
        return new BedrockModel(BedrockModel.DEFAULT_MODEL_ID, BedrockModel.DEFAULT_REGION, promptCaching);
    }

    static ResolvedModelDefaults resolveNamedModelDefaults(
            ArachneProperties.ModelProperties defaults,
            ArachneProperties.ModelOverrideProperties overrides,
            Model defaultModel) {
        ArachneProperties.ModelProperties mergedModel = mergeModelProperties(defaults, overrides);
        if (!hasModelOverride(overrides)) {
            return new ResolvedModelDefaults(mergedModel, defaultModel);
        }
        return new ResolvedModelDefaults(mergedModel, createDefaultModel(mergedModel));
    }

    static ArachneProperties.ModelProperties mergeModelProperties(
            ArachneProperties.ModelProperties defaults,
            ArachneProperties.ModelOverrideProperties overrides) {
        ArachneProperties.ModelProperties merged = copyModelProperties(defaults);
        if (overrides == null) {
            return merged;
        }
        if (hasText(overrides.getProvider())) {
            merged.setProvider(overrides.getProvider());
        }
        if (hasText(overrides.getId())) {
            merged.setId(overrides.getId());
        }
        if (hasText(overrides.getRegion())) {
            merged.setRegion(overrides.getRegion());
        }
        if (overrides.getBedrock() != null && overrides.getBedrock().getCache() != null) {
            if (overrides.getBedrock().getCache().getSystemPrompt() != null) {
                merged.getBedrock().getCache().setSystemPrompt(overrides.getBedrock().getCache().getSystemPrompt());
            }
            if (overrides.getBedrock().getCache().getTools() != null) {
                merged.getBedrock().getCache().setTools(overrides.getBedrock().getCache().getTools());
            }
        }
        return merged;
    }

    static ArachneProperties.ModelProperties copyModelProperties(ArachneProperties.ModelProperties source) {
        ArachneProperties.ModelProperties copy = new ArachneProperties.ModelProperties();
        copy.setProvider(source.getProvider());
        copy.setId(source.getId());
        copy.setRegion(source.getRegion());
        copy.getBedrock().getCache().setSystemPrompt(source.getBedrock().getCache().isSystemPrompt());
        copy.getBedrock().getCache().setTools(source.getBedrock().getCache().isTools());
        return copy;
    }

    private static boolean hasModelOverride(ArachneProperties.ModelOverrideProperties modelProperties) {
        return modelProperties != null
                && (hasText(modelProperties.getProvider())
                || hasText(modelProperties.getId())
                || hasText(modelProperties.getRegion())
                || hasBedrockOverride(modelProperties.getBedrock()));
    }

    private static boolean hasBedrockOverride(ArachneProperties.BedrockOverrideProperties bedrockProperties) {
        return bedrockProperties != null
                && bedrockProperties.getCache() != null
                && (bedrockProperties.getCache().getSystemPrompt() != null
                || bedrockProperties.getCache().getTools() != null);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    record ResolvedModelDefaults(
            ArachneProperties.ModelProperties modelProperties,
            Model defaultModel) {
    }
}