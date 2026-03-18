package io.arachne.strands.tool.annotation;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.arachne.strands.model.ToolSpec;
import io.arachne.strands.schema.JsonSchemaGenerator;
import io.arachne.strands.tool.BeanValidationSupport;
import io.arachne.strands.tool.Tool;
import io.arachne.strands.tool.ToolDefinitionException;
import io.arachne.strands.tool.ToolResult;
import jakarta.validation.Validator;

/**
 * Tool wrapper around a method annotated with {@link StrandsTool}.
 */
public class MethodTool implements Tool {

    private final ObjectMapper objectMapper;
    private final Object target;
    private final Method method;
    private final Validator validator;
    private final ToolSpec spec;

    public MethodTool(Object target, Method method, JsonSchemaGenerator schemaGenerator) {
        this(target, method, schemaGenerator, new ObjectMapper(), BeanValidationSupport.defaultValidator());
    }

    public MethodTool(
            Object target,
            Method method,
            JsonSchemaGenerator schemaGenerator,
            ObjectMapper objectMapper,
            Validator validator) {
        this.target = target;
        this.method = method;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.method.setAccessible(true);

        StrandsTool annotation = method.getAnnotation(StrandsTool.class);
        if (annotation == null) {
            throw new ToolDefinitionException("Method is not annotated with @StrandsTool: " + method);
        }

        String name = annotation.name().isBlank() ? method.getName() : annotation.name();
        String description = annotation.description().isBlank() ? name : annotation.description();
        this.spec = new ToolSpec(name, description, schemaGenerator.schemaForMethod(method));
    }

    @Override
    public ToolSpec spec() {
        return spec;
    }

    @Override
    public ToolResult invoke(Object input) {
        try {
            Object[] arguments = resolveArguments(input);
            BeanValidationSupport.validateToolArguments(validator, target, method, arguments);

            Object result = method.invoke(target, arguments);
            if (result instanceof ToolResult toolResult) {
                return toolResult;
            }
            return ToolResult.success(null, unwrapOptional(result));
        } catch (InvocationTargetException e) {
            Throwable targetException = e.getTargetException();
            if (targetException instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new ToolDefinitionException("Tool method threw checked exception: " + method, targetException);
        } catch (IllegalAccessException e) {
            throw new ToolDefinitionException("Failed to access tool method: " + method, e);
        }
    }

    private Object[] resolveArguments(Object input) {
        Map<String, Object> values = inputAsMap(input);
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];

        for (int index = 0; index < parameters.length; index++) {
            Parameter parameter = parameters[index];
            String name = parameterName(parameter);
            Object value = values.get(name);
            if (value == null && Optional.class.isAssignableFrom(parameter.getType())) {
                args[index] = Optional.empty();
            } else if (value == null && parameter.getType().isPrimitive()) {
                throw new ToolDefinitionException("Missing required primitive tool parameter: " + name);
            } else if (Optional.class.isAssignableFrom(parameter.getType())) {
                args[index] = Optional.ofNullable(convertOptionalValue(parameter, value));
            } else {
                args[index] = objectMapper.convertValue(value, parameter.getType());
            }
        }

        return args;
    }

    private static Object unwrapOptional(Object result) {
        return result instanceof Optional<?> optional ? optional.orElse(null) : result;
    }

    private static String parameterName(Parameter parameter) {
        ToolParam annotation = parameter.getAnnotation(ToolParam.class);
        if (annotation != null && !annotation.name().isBlank()) {
            return annotation.name();
        }
        return parameter.getName();
    }

    private Object convertOptionalValue(Parameter parameter, Object value) {
        if (!(parameter.getParameterizedType() instanceof java.lang.reflect.ParameterizedType parameterizedType)) {
            return value;
        }
        java.lang.reflect.Type optionalType = parameterizedType.getActualTypeArguments()[0];
        if (optionalType instanceof Class<?> optionalClass) {
            return objectMapper.convertValue(value, optionalClass);
        }
        return value;
    }

    private static Map<String, Object> inputAsMap(Object input) {
        if (input == null) {
            return Map.of();
        }
        if (input instanceof Map<?, ?> map) {
            Map<String, Object> values = new LinkedHashMap<>();
            map.forEach((key, value) -> values.put(String.valueOf(key), value));
            return values;
        }
        throw new ToolDefinitionException("Tool input must be an object map but was: " + input.getClass().getName());
    }
}