package io.arachne.strands.tool.annotation;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.MethodIntrospector;
import org.springframework.util.ClassUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

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
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public AnnotationToolScanner() {
        this(new JsonSchemaGenerator(), new ObjectMapper(), BeanValidationSupport.defaultValidator());
    }

    public AnnotationToolScanner(JsonSchemaGenerator schemaGenerator, Validator validator) {
        this(schemaGenerator, new ObjectMapper(), validator);
    }

    public AnnotationToolScanner(JsonSchemaGenerator schemaGenerator, ObjectMapper objectMapper, Validator validator) {
        this.schemaGenerator = schemaGenerator;
        this.objectMapper = objectMapper;
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
            for (ToolCandidate candidate : discoverToolCandidates(bean)) {
                Tool tool = new MethodTool(
                        bean,
                        candidate.annotatedMethod(),
                        candidate.bindingMethod(),
                        candidate.invocationMethod(),
                        schemaGenerator,
                        objectMapper,
                        validator);
                DiscoveredTool discoveredTool = new DiscoveredTool(
                        tool,
                        qualifierList(candidate.userClass(), candidate.annotatedMethod(), candidate.bindingMethod()));
                DiscoveredTool previous = toolsByName.putIfAbsent(tool.spec().name(), discoveredTool);
                if (previous != null) {
                    throw new ToolDefinitionException("Duplicate tool name discovered: " + tool.spec().name());
                }
            }
        }
        return List.copyOf(new ArrayList<>(toolsByName.values()));
    }

    private List<ToolCandidate> discoverToolCandidates(Object bean) {
        Class<?> beanClass = bean.getClass();
        Class<?> userClass = AopUtils.getTargetClass(bean);
        Map<String, ToolCandidate> candidatesBySignature = new LinkedHashMap<>();

        collectAnnotatedMethods(userClass, userClass, beanClass, candidatesBySignature);
        for (Class<?> interfaceClass : ClassUtils.getAllInterfacesForClassAsSet(userClass)) {
            collectAnnotatedMethods(interfaceClass, userClass, beanClass, candidatesBySignature);
        }
        if (!beanClass.equals(userClass)) {
            collectAnnotatedMethods(beanClass, userClass, beanClass, candidatesBySignature);
            for (Class<?> interfaceClass : ClassUtils.getAllInterfacesForClassAsSet(beanClass)) {
                collectAnnotatedMethods(interfaceClass, userClass, beanClass, candidatesBySignature);
            }
        }

        return List.copyOf(candidatesBySignature.values());
    }

    private void collectAnnotatedMethods(
            Class<?> scanClass,
            Class<?> userClass,
            Class<?> beanClass,
            Map<String, ToolCandidate> candidatesBySignature) {
        for (Method annotatedMethod : scanClass.getMethods()) {
            if (!annotatedMethod.isAnnotationPresent(StrandsTool.class)) {
                continue;
            }

            Method bindingMethod = resolveBindingMethod(annotatedMethod, userClass);
            Method invocationMethod = resolveInvocationMethod(annotatedMethod, bindingMethod, beanClass);
            candidatesBySignature.putIfAbsent(
                    methodSignature(bindingMethod),
                    new ToolCandidate(userClass, annotatedMethod, bindingMethod, invocationMethod));
        }
    }

    private static Method resolveBindingMethod(Method annotatedMethod, Class<?> userClass) {
        if (!annotatedMethod.getDeclaringClass().isInterface()) {
            return annotatedMethod;
        }
        try {
            return userClass.getMethod(annotatedMethod.getName(), annotatedMethod.getParameterTypes());
        } catch (NoSuchMethodException e) {
            return annotatedMethod;
        }
    }

    private static Method resolveInvocationMethod(Method annotatedMethod, Method bindingMethod, Class<?> beanClass) {
        Class<?> invocableTargetClass = java.util.Objects.requireNonNull(beanClass, "beanClass must not be null");
        Method annotatedCandidate = java.util.Objects.requireNonNull(annotatedMethod, "annotatedMethod must not be null");
        Method bindingCandidate = java.util.Objects.requireNonNull(bindingMethod, "bindingMethod must not be null");
        try {
            return MethodIntrospector.selectInvocableMethod(annotatedCandidate, invocableTargetClass);
        } catch (IllegalStateException ignored) {
            try {
                return MethodIntrospector.selectInvocableMethod(bindingCandidate, invocableTargetClass);
            } catch (IllegalStateException e) {
                throw new ToolDefinitionException(
                        "Annotated tool method is not invocable through the Spring bean proxy: " + bindingCandidate,
                        e);
            }
        }
    }

    private static String methodSignature(Method method) {
        return method.getName() + java.util.Arrays.toString(method.getParameterTypes());
    }

    private static Set<String> qualifierList(Class<?> userClass, Method annotatedMethod, Method bindingMethod) {
        List<String> qualifiers = new ArrayList<>();
        StrandsTool annotation = annotatedMethod.getAnnotation(StrandsTool.class);
        if (annotation != null) {
            qualifiers.addAll(List.of(annotation.qualifiers()));
        }

        Qualifier methodQualifier = annotatedMethod.getAnnotation(Qualifier.class);
        if (methodQualifier != null) {
            qualifiers.add(methodQualifier.value());
        }

        if (!bindingMethod.equals(annotatedMethod)) {
            Qualifier bindingMethodQualifier = bindingMethod.getAnnotation(Qualifier.class);
            if (bindingMethodQualifier != null) {
                qualifiers.add(bindingMethodQualifier.value());
            }
        }

        Qualifier classQualifier = userClass.getAnnotation(Qualifier.class);
        if (classQualifier != null) {
            qualifiers.add(classQualifier.value());
        }

        return Set.copyOf(qualifiers);
    }

    private record ToolCandidate(
            Class<?> userClass,
            Method annotatedMethod,
            Method bindingMethod,
            Method invocationMethod) {
    }
}