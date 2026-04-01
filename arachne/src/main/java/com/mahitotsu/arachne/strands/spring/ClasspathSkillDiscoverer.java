package com.mahitotsu.arachne.strands.spring;

import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import com.mahitotsu.arachne.strands.skills.Skill;
import com.mahitotsu.arachne.strands.skills.SkillParseException;
import com.mahitotsu.arachne.strands.skills.SkillParser;

/**
 * Discovers skills packaged under {@code resources/skills/<skill-name>/SKILL.md} on the classpath.
 */
public final class ClasspathSkillDiscoverer {

    static final String DEFAULT_RESOURCE_PATTERN = "classpath*:skills/*/SKILL.md";
    private static final List<String> RESOURCE_DIRS = List.of("scripts", "references", "assets");

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
                Skill parsed = skillParser.parse(resource);
                Skill skill = new Skill(
                        parsed.name(),
                        parsed.description(),
                        parsed.instructions(),
                        parsed.allowedTools(),
                        parsed.metadata(),
                        parsed.compatibility(),
                        parsed.license(),
                        parsed.location(),
                        discoverResourceFiles(parsed.name()));
                discovered.put(skill.name(), skill);
            } catch (SkillParseException ignored) {
                // Skip malformed skill resources so one bad asset does not break application startup.
            }
        }
        return List.copyOf(discovered.values());
    }

    private List<String> discoverResourceFiles(String skillName) {
        String skillRootPattern = skillRootPattern(skillName);
        if (skillRootPattern == null) {
            return List.of();
        }

        LinkedHashSet<String> resourceFiles = new LinkedHashSet<>();
        for (String resourceDir : RESOURCE_DIRS) {
            try {
                Resource[] resources = resourcePatternResolver.getResources(skillRootPattern + resourceDir + "/**");
                Arrays.sort(resources, Comparator.comparing(Resource::getDescription, String.CASE_INSENSITIVE_ORDER));
                for (Resource resource : resources) {
                    String relativePath = relativeSkillResourcePath(resourceDir, resource);
                    if (relativePath != null) {
                        resourceFiles.add(relativePath);
                    }
                }
            } catch (IOException e) {
                throw new IllegalStateException("Failed to scan classpath skill resources for " + skillName, e);
            }
        }
        return List.copyOf(resourceFiles);
    }

    private String skillRootPattern(String skillName) {
        int wildcardIndex = resourcePattern.lastIndexOf('*');
        if (wildcardIndex < 0) {
            return null;
        }
        return resourcePattern.substring(0, wildcardIndex) + skillName + "/";
    }

    private String relativeSkillResourcePath(String resourceDir, Resource resource) {
        if (!resource.isReadable() || resource.getFilename() == null) {
            return null;
        }

        String description = resourceLocation(resource).replace('\\', '/');
        String marker = "/" + resourceDir + "/";
        int markerIndex = description.lastIndexOf(marker);
        if (markerIndex < 0) {
            return null;
        }

        String relativePath = description.substring(markerIndex + 1);
        if (relativePath.endsWith("/")) {
            return null;
        }
        return relativePath;
    }

    private String resourceLocation(Resource resource) {
        try {
            return resource.getURI().toString();
        } catch (IOException ignored) {
            return resource.getDescription();
        }
    }
}