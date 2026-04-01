package com.mahitotsu.arachne.strands.schema;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.temporal.Temporal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.mahitotsu.arachne.strands.tool.ToolDefinitionException;
import com.mahitotsu.arachne.strands.tool.ToolInvocationContext;
import com.mahitotsu.arachne.strands.tool.annotation.ToolParam;

/**
 * Minimal JSON Schema generator shared by annotation tools and structured output.
 */
public class JsonSchemaGenerator {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final ObjectMapper objectMapper;

    public JsonSchemaGenerator() {
        this(new ObjectMapper());
    }

    public JsonSchemaGenerator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public JsonNode schemaForType(Class<?> type) {
        return schemaForType(type, Map.of());
    }

    public JsonNode schemaForMethod(Method method) {
        ObjectNode root = baseObjectSchema();
        ObjectNode properties = root.putObject("properties");
        ArrayNode required = JSON.arrayNode();

        for (Parameter parameter : method.getParameters()) {
            if (isInvocationContextParameter(parameter)) {
                continue;
            }
            String name = parameterName(parameter);
            ToolParam toolParam = parameter.getAnnotation(ToolParam.class);
            JsonNode schema = schemaForType(parameterType(parameter), Map.of());
            if (toolParam != null && !toolParam.description().isBlank() && schema instanceof ObjectNode objectSchema) {
                objectSchema.put("description", toolParam.description());
            }
            properties.set(name, schema);
            if (isRequired(parameter, toolParam)) {
                required.add(name);
            }
        }

        if (!required.isEmpty()) {
            root.set("required", required);
        }
        return root;
    }

    private JsonNode schemaForType(Class<?> rawType, Map<Class<?>, JsonNode> visiting) {
        if (rawType == String.class || rawType == Character.class || rawType == char.class || Temporal.class.isAssignableFrom(rawType)) {
            return simpleType("string");
        }
        if (rawType == boolean.class || rawType == Boolean.class) {
            return simpleType("boolean");
        }
        if (rawType == int.class || rawType == Integer.class
                || rawType == long.class || rawType == Long.class
                || rawType == short.class || rawType == Short.class
                || rawType == byte.class || rawType == Byte.class
                || rawType == BigInteger.class) {
            return simpleType("integer");
        }
        if (rawType == float.class || rawType == Float.class
                || rawType == double.class || rawType == Double.class
                || rawType == BigDecimal.class) {
            return simpleType("number");
        }
        if (rawType.isEnum()) {
            ObjectNode node = simpleType("string");
            ArrayNode values = node.putArray("enum");
            for (Object constant : rawType.getEnumConstants()) {
                values.add(constant.toString());
            }
            return node;
        }
        if (rawType.isArray()) {
            ObjectNode node = simpleType("array");
            node.set("items", schemaForType(rawType.getComponentType(), visiting));
            return node;
        }
        if (Optional.class.isAssignableFrom(rawType)) {
            return JSON.objectNode();
        }
        if (List.class.isAssignableFrom(rawType)) {
            ObjectNode node = simpleType("array");
            node.set("items", JSON.objectNode());
            return node;
        }
        if (Map.class.isAssignableFrom(rawType)) {
            ObjectNode node = simpleType("object");
            node.set("additionalProperties", JSON.objectNode());
            return node;
        }
        if (rawType == Object.class) {
            return JSON.objectNode();
        }
        if (visiting.containsKey(rawType)) {
            return objectMapper.valueToTree(Map.of("type", "object"));
        }

        visiting = new LinkedHashMap<>(visiting);
        visiting.put(rawType, JSON.objectNode());
        return objectSchema(rawType, visiting);
    }

    private JsonNode objectSchema(Class<?> rawType, Map<Class<?>, JsonNode> visiting) {
        if (rawType.isRecord()) {
            return recordSchema(rawType, visiting);
        }

        if (rawType.getPackageName().startsWith("java.")) {
            throw new ToolDefinitionException("Unsupported Java type for schema generation: " + rawType.getName());
        }

        ObjectNode node = baseObjectSchema();
        ObjectNode properties = node.putObject("properties");
        ArrayNode required = JSON.arrayNode();

        for (Method method : rawType.getMethods()) {
            if (method.getParameterCount() != 0 || method.getDeclaringClass() == Object.class) {
                continue;
            }
            String name = beanPropertyName(method);
            if (name == null) {
                continue;
            }
            properties.set(name, schemaForJavaType(method.getGenericReturnType(), visiting));
            if (method.getReturnType().isPrimitive()) {
                required.add(name);
            }
        }

        if (!required.isEmpty()) {
            node.set("required", required);
        }
        return node;
    }

    private JsonNode recordSchema(Class<?> rawType, Map<Class<?>, JsonNode> visiting) {
        ObjectNode node = baseObjectSchema();
        ObjectNode properties = node.putObject("properties");
        ArrayNode required = JSON.arrayNode();

        for (RecordComponent component : rawType.getRecordComponents()) {
            properties.set(component.getName(), schemaForJavaType(component.getGenericType(), visiting));
            required.add(component.getName());
        }

        node.set("required", required);
        return node;
    }

    private JsonNode schemaForJavaType(Type type, Map<Class<?>, JsonNode> visiting) {
        if (type instanceof Class<?> clazz) {
            return schemaForType(clazz, visiting);
        }
        if (type instanceof ParameterizedType parameterizedType) {
            Type raw = parameterizedType.getRawType();
            if (raw instanceof Class<?> rawClass) {
                if (Optional.class.isAssignableFrom(rawClass)) {
                    return schemaForJavaType(parameterizedType.getActualTypeArguments()[0], visiting);
                }
                if (List.class.isAssignableFrom(rawClass)) {
                    ObjectNode node = simpleType("array");
                    node.set("items", schemaForJavaType(parameterizedType.getActualTypeArguments()[0], visiting));
                    return node;
                }
                if (Map.class.isAssignableFrom(rawClass)) {
                    ObjectNode node = simpleType("object");
                    node.set("additionalProperties", schemaForJavaType(parameterizedType.getActualTypeArguments()[1], visiting));
                    return node;
                }
                return schemaForType(rawClass, visiting);
            }
        }
        return JSON.objectNode();
    }

    private static ObjectNode simpleType(String type) {
        ObjectNode node = JSON.objectNode();
        node.put("type", type);
        return node;
    }

    private static ObjectNode baseObjectSchema() {
        ObjectNode node = simpleType("object");
        node.put("additionalProperties", false);
        return node;
    }

    private static boolean isRequired(Parameter parameter, ToolParam toolParam) {
        if (Optional.class.isAssignableFrom(parameter.getType())) {
            return false;
        }
        return toolParam == null || toolParam.required();
    }

    private static boolean isInvocationContextParameter(Parameter parameter) {
        return ToolInvocationContext.class.isAssignableFrom(parameter.getType());
    }

    private static Class<?> parameterType(Parameter parameter) {
        if (Optional.class.isAssignableFrom(parameter.getType()) && parameter.getParameterizedType() instanceof ParameterizedType parameterizedType) {
            Type type = parameterizedType.getActualTypeArguments()[0];
            if (type instanceof Class<?> clazz) {
                return clazz;
            }
        }
        return parameter.getType();
    }

    private static String parameterName(Parameter parameter) {
        ToolParam toolParam = parameter.getAnnotation(ToolParam.class);
        if (toolParam != null && !toolParam.name().isBlank()) {
            return toolParam.name();
        }
        if (!parameter.isNamePresent()) {
            throw new ToolDefinitionException("Parameter names must be retained with -parameters: " + parameter);
        }
        return parameter.getName();
    }

    private static String beanPropertyName(Method method) {
        String name = method.getName();
        if (name.startsWith("get") && name.length() > 3) {
            return decapitalize(name.substring(3));
        }
        if (name.startsWith("is") && name.length() > 2 && (method.getReturnType() == boolean.class || method.getReturnType() == Boolean.class)) {
            return decapitalize(name.substring(2));
        }
        return null;
    }

    private static String decapitalize(String value) {
        if (value.isEmpty()) {
            return value;
        }
        if (value.length() > 1 && Character.isUpperCase(value.charAt(1)) && Character.isUpperCase(value.charAt(0))) {
            return value;
        }
        return Character.toLowerCase(value.charAt(0)) + value.substring(1);
    }
}