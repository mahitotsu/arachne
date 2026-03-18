package io.arachne.strands.tool.annotation;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Qualifier;

import io.arachne.strands.schema.JsonSchemaGenerator;
import io.arachne.strands.tool.BeanValidationSupport;
import io.arachne.strands.tool.Tool;
import io.arachne.strands.tool.ToolDefinitionException;
import jakarta.validation.Validator;

/**
 * Discovers tool methods from application beans.
 */
public class AnnotationToolScanner {

    private final JsonSchemaGenerator schemaGenerator;
    private final Validator validator;

    public AnnotationToolScanner() {
        this(new JsonSchemaGenerator(), BeanValidationSupport.defaultValidator());
    }

    public AnnotationToolScanner(JsonSchemaGenerator schemaGenerator, Validator validator) {
        this.schemaGenerator = schemaGenerator;
        this.validator = validator;
    }

    public List<Tool> scan(Collection<?> beans) {
        return scanDiscoveredTools(beans).stream().map(DiscoveredTool::tool).toList();
    }

    public List<DiscoveredTool> scanDiscoveredTools(Collection<?> beans) {
        Map<String, DiscoveredTool> toolsByName = new LinkedHashMap<>();
        for (Object bean : beans) {
            if (bean == null) {
                continue;
            }
            for (Method method : bean.getClass().getMethods()) {
                if (!method.isAnnotationPresent(StrandsTool.class)) {
                    continue;
                }
                Tool tool = new MethodTool(bean, method, schemaGenerator, new com.fasterxml.jackson.databind.ObjectMapper(), validator);
                DiscoveredTool discoveredTool = new DiscoveredTool(tool, qualifierList(bean.getClass(), method));
                DiscoveredTool previous = toolsByName.putIfAbsent(tool.spec().name(), discoveredTool);
                if (previous != null) {
                    throw new ToolDefinitionException("Duplicate tool name discovered: " + tool.spec().name());
                }
            }
        }
        return List.copyOf(new ArrayList<>(toolsByName.values()));
    }

    private static Set<String> qualifierList(Class<?> beanClass, Method method) {
        List<String> qualifiers = new ArrayList<>();
        StrandsTool annotation = method.getAnnotation(StrandsTool.class);
        if (annotation != null) {
            qualifiers.addAll(List.of(annotation.qualifiers()));
        }

        Qualifier methodQualifier = method.getAnnotation(Qualifier.class);
        if (methodQualifier != null) {
            qualifiers.add(methodQualifier.value());
        }

        Qualifier classQualifier = beanClass.getAnnotation(Qualifier.class);
        if (classQualifier != null) {
            qualifiers.add(classQualifier.value());
        }

        return Set.copyOf(qualifiers);
    }
}