package io.arachne.strands.spring;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import io.arachne.strands.skills.Skill;
import io.arachne.strands.skills.SkillParseException;
import io.arachne.strands.skills.SkillParser;

/**
 * Discovers skills packaged under {@code resources/skills/<skill-name>/SKILL.md} on the classpath.
 */
public final class ClasspathSkillDiscoverer {

    static final String DEFAULT_RESOURCE_PATTERN = "classpath*:skills/*/SKILL.md";

    private final ResourcePatternResolver resourcePatternResolver;
    private final SkillParser skillParser;
    private final String resourcePattern;

    public ClasspathSkillDiscoverer(SkillParser skillParser) {
        this(new PathMatchingResourcePatternResolver(), skillParser, DEFAULT_RESOURCE_PATTERN);
    }

    public ClasspathSkillDiscoverer(ResourcePatternResolver resourcePatternResolver, SkillParser skillParser) {
        this(resourcePatternResolver, skillParser, DEFAULT_RESOURCE_PATTERN);
    }

    ClasspathSkillDiscoverer(
            ResourcePatternResolver resourcePatternResolver,
            SkillParser skillParser,
            String resourcePattern) {
        this.resourcePatternResolver = Objects.requireNonNull(resourcePatternResolver, "resourcePatternResolver must not be null");
        this.skillParser = Objects.requireNonNull(skillParser, "skillParser must not be null");
        this.resourcePattern = Objects.requireNonNull(resourcePattern, "resourcePattern must not be null");
    }

    public List<Skill> discover() {
        Resource[] resources;
        try {
            resources = resourcePatternResolver.getResources(Objects.requireNonNull(resourcePattern));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to scan classpath skills", e);
        }

        Arrays.sort(resources, Comparator.comparing(Resource::getDescription, String.CASE_INSENSITIVE_ORDER));
        LinkedHashMap<String, Skill> discovered = new LinkedHashMap<>();
        for (Resource resource : resources) {
            if (!resource.isReadable()) {
                continue;
            }
            try {
                Skill skill = skillParser.parse(resource);
                discovered.put(skill.name(), skill);
            } catch (SkillParseException ignored) {
                // Skip malformed skill resources so one bad asset does not break application startup.
            }
        }
        return List.copyOf(discovered.values());
    }
}