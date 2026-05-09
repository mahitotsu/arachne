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
        String serviceTier = modelProperties.getBedrock().getServiceTier();
        boolean strictTools = modelProperties.getBedrock().isStrictTools();

        String modelId = modelProperties.getId();
        String region = modelProperties.getRegion();
        if (hasText(modelId)) {
            return new BedrockModel(modelId, region, promptCaching, serviceTier, strictTools);
        }
        if (hasText(region)) {
            return new BedrockModel(BedrockModel.DEFAULT_MODEL_ID, region, promptCaching, serviceTier, strictTools);
        }
        return new BedrockModel(
                BedrockModel.DEFAULT_MODEL_ID,
                BedrockModel.DEFAULT_REGION,
                promptCaching,
                serviceTier,
                strictTools);
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
        if (overrides.getBedrock() != null && hasText(overrides.getBedrock().getServiceTier())) {
            merged.getBedrock().setServiceTier(overrides.getBedrock().getServiceTier());
        }
        if (overrides.getBedrock() != null && overrides.getBedrock().getStrictTools() != null) {
            merged.getBedrock().setStrictTools(overrides.getBedrock().getStrictTools());
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
        copy.getBedrock().setServiceTier(source.getBedrock().getServiceTier());
        copy.getBedrock().setStrictTools(source.getBedrock().isStrictTools());
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
        if (bedrockProperties == null) {
            return false;
        }
        boolean cacheOverride = bedrockProperties.getCache() != null
                && (bedrockProperties.getCache().getSystemPrompt() != null
                || bedrockProperties.getCache().getTools() != null);
        return cacheOverride
            || hasText(bedrockProperties.getServiceTier())
            || bedrockProperties.getStrictTools() != null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    record ResolvedModelDefaults(
            ArachneProperties.ModelProperties modelProperties,
            Model defaultModel) {
    }
}