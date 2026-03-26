package io.arachne.strands.tool.builtin;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Allowlist policy for built-in read-only resource tools.
 */
public final class BuiltInResourceAccessPolicy {

    private final List<String> allowedClasspathLocations;
    private final List<Path> allowedFileLocations;

    public BuiltInResourceAccessPolicy(List<String> allowedClasspathLocations, List<String> allowedFileLocations) {
        this.allowedClasspathLocations = normalizeClasspathLocations(allowedClasspathLocations);
        this.allowedFileLocations = normalizeFileLocations(allowedFileLocations);
    }

    public String normalizeResourceLocation(String location) {
        Objects.requireNonNull(location, "location must not be null");
        String trimmed = location.trim().replace('\\', '/');
        if (trimmed.startsWith("classpath*:/")) {
            return "classpath:/" + trimmed.substring("classpath*:/".length());
        }
        if (trimmed.startsWith("classpath:")) {
            if (trimmed.equals("classpath:")) {
                return "classpath:/";
            }
            if (!trimmed.startsWith("classpath:/")) {
                return "classpath:/" + trimmed.substring("classpath:".length());
            }
            return trimmed;
        }
        if (trimmed.startsWith("file:")) {
            Path path = Path.of(URI.create(trimmed)).normalize();
            return path.toUri().toString();
        }
        throw new IllegalArgumentException("Built-in resource locations must start with classpath: or file:");
    }

    public String normalizeDirectoryLocation(String location) {
        String normalized = normalizeResourceLocation(location);
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }

    public boolean isAllowed(String location) {
        String normalized = normalizeResourceLocation(location);
        if (normalized.startsWith("classpath:/")) {
            return allowedClasspathLocations.stream().anyMatch(normalized::startsWith);
        }
        if (normalized.startsWith("file:")) {
            Path candidate = Path.of(URI.create(normalized)).normalize();
            return allowedFileLocations.stream().anyMatch(candidate::startsWith);
        }
        return false;
    }

    private static List<String> normalizeClasspathLocations(List<String> locations) {
        List<String> rawLocations = locations == null || locations.isEmpty() ? List.of("classpath:/") : locations;
        return rawLocations.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.replace('\\', '/'))
                .map(value -> {
                    if (value.startsWith("classpath*:/")) {
                        return "classpath:/" + value.substring("classpath*:/".length());
                    }
                    if (value.startsWith("classpath:/")) {
                        return value;
                    }
                    if (value.startsWith("classpath:")) {
                        return "classpath:/" + value.substring("classpath:".length());
                    }
                    throw new IllegalArgumentException("Classpath allowlist entries must start with classpath:");
                })
                .map(value -> value.endsWith("/") ? value : value + "/")
                .map(value -> value.toLowerCase(Locale.ROOT).startsWith("classpath:/") ? value : value)
                .toList();
    }

    private static List<Path> normalizeFileLocations(List<String> locations) {
        if (locations == null || locations.isEmpty()) {
            return List.of();
        }
        return locations.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> {
                    String normalized = value.startsWith("file:") ? value : Path.of(value).toUri().toString();
                    return Path.of(URI.create(normalized)).normalize();
                })
                .toList();
    }
}