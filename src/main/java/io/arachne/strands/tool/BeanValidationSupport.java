package io.arachne.strands.tool;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * Shared runtime Bean Validation helpers.
 */
public final class BeanValidationSupport {

    private static final Validator DEFAULT_VALIDATOR = createValidator();

    private BeanValidationSupport() {
    }

    public static Validator defaultValidator() {
        return DEFAULT_VALIDATOR;
    }

    public static void validateToolArguments(Validator validator, Object target, Method method, Object[] arguments) {
        Set<ConstraintViolation<Object>> violations = validator.forExecutables()
                .validateParameters(target, method, arguments);
        if (!violations.isEmpty()) {
            throw new ToolValidationException("Tool input validation failed: " + formatViolations(violations));
        }
    }

    public static void validateStructuredOutput(Validator validator, Object value, Class<?> outputType) {
        Set<ConstraintViolation<Object>> violations = validator.validate(value);
        if (!violations.isEmpty()) {
            throw new StructuredOutputException(
                    "Structured output validation failed for " + outputType.getSimpleName() + ": "
                            + formatViolations(violations));
        }
    }

    private static Validator createValidator() {
        ValidatorFactory factory = Validation.byDefaultProvider()
                .configure()
                .messageInterpolator(new ParameterMessageInterpolator())
                .buildValidatorFactory();
        return factory.getValidator();
    }

    private static String formatViolations(Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream()
                .map(violation -> {
                    String path = violation.getPropertyPath() == null ? "" : violation.getPropertyPath().toString();
                    return path.isBlank() ? violation.getMessage() : path + " " + violation.getMessage();
                })
                .sorted()
                .collect(Collectors.joining("; "));
    }
}