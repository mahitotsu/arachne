package io.arachne.strands.skills;

/**
 * Raised when a SKILL.md document cannot be parsed into a {@link Skill}.
 */
public class SkillParseException extends IllegalArgumentException {

    public SkillParseException(String message) {
        super(message);
    }

    public SkillParseException(String message, Throwable cause) {
        super(message, cause);
    }
}