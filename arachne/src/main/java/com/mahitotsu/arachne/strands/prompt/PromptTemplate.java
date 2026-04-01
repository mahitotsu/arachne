package com.mahitotsu.arachne.strands.prompt;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A text template with named placeholders that can be rendered into a prompt string.
 *
 * <h2>Placeholder syntax</h2>
 * <p>Placeholders are written as {@code {{name}}} where {@code name} is a valid identifier
 * ({@code [a-zA-Z_][a-zA-Z0-9_]*}).
 *
 * <pre>{@code
 * PromptTemplate t = PromptTemplate.of("Hello, {{name}}! You are a {{role}}.");
 * String text = t.render(PromptVariables.of("name", "Alice", "role", "helpful assistant"));
 * }</pre>
 *
 * <h2>Escaping</h2>
 * <p>Prefix a placeholder with a backslash to produce the literal double-brace sequence:
 * {@code \{{name}}} → {@code {{name}}}. This is the only escaping rule; backslashes that are
 * not immediately followed by {@code {{} are kept as-is.
 *
 * <h2>Missing variables</h2>
 * <p>Rendering fails with {@link IllegalArgumentException} if the variable map does not contain
 * a value for every placeholder found in the template text. The error message names the missing
 * variable so callers can fix the map without inspecting the template.
 */
public final class PromptTemplate {

    /** Matches an optional leading backslash followed by {{identifier}}. */
    private static final Pattern PLACEHOLDER_PATTERN =
            Pattern.compile("(\\\\)?\\{\\{([a-zA-Z_][a-zA-Z0-9_]*)\\}\\}");

    private final String template;

    private PromptTemplate(String template) {
        this.template = template;
    }

    /**
     * Creates a {@code PromptTemplate} from the given template string.
     *
     * @param template the template text; must not be {@code null}
     * @throws IllegalArgumentException if {@code template} is {@code null}
     */
    public static PromptTemplate of(String template) {
        if (template == null) {
            throw new IllegalArgumentException("Template text must not be null");
        }
        return new PromptTemplate(template);
    }

    /**
     * Renders this template by substituting all placeholders with values from {@code variables}.
     *
     * @param variables the variable map; must not be {@code null}
     * @return the rendered prompt text
     * @throws IllegalArgumentException if a placeholder references a variable that is absent
     *         from {@code variables}
     */
    public String render(PromptVariables variables) {
        if (variables == null) {
            throw new IllegalArgumentException("PromptVariables must not be null");
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        int last = 0;
        while (matcher.find()) {
            sb.append(template, last, matcher.start());
            String escape = matcher.group(1);
            String name = matcher.group(2);
            if (escape != null) {
                // Escaped placeholder: emit literal {{name}} without consuming the backslash
                sb.append("{{").append(name).append("}}");
            } else {
                if (!variables.contains(name)) {
                    throw new IllegalArgumentException("Missing template variable: " + name);
                }
                sb.append(variables.get(name));
            }
            last = matcher.end();
        }
        sb.append(template, last, template.length());
        return sb.toString();
    }

    /**
     * Returns the raw template string.
     */
    public String template() {
        return template;
    }

    @Override
    public String toString() {
        return "PromptTemplate{" + template + "}";
    }
}
