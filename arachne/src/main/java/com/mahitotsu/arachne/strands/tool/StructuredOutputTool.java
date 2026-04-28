package com.mahitotsu.arachne.strands.tool;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.mahitotsu.arachne.strands.model.ToolSpec;
import com.mahitotsu.arachne.strands.schema.JsonSchemaGenerator;
import jakarta.validation.Validator;

/**
 * Final tool used to capture typed structured output from the model.
 */
public class StructuredOutputTool<T> implements Tool {

    public static final String DEFAULT_NAME = "structured_output";
    private static final String DEFAULT_FORCE_PROMPT =
            "Respond by calling the structured_output tool with the final answer as JSON. Do not reply with plain text.";

    private final Class<T> outputType;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final ToolSpec spec;
    private final String forcePrompt;
    private T value;

    public StructuredOutputTool(Class<T> outputType) {
        this(
                outputType,
                new JsonSchemaGenerator(),
                new ObjectMapper(),
                BeanValidationSupport.defaultValidator(),
                DEFAULT_FORCE_PROMPT);
    }

    public StructuredOutputTool(Class<T> outputType, String forcePrompt) {
        this(
                outputType,
                new JsonSchemaGenerator(),
                new ObjectMapper(),
                BeanValidationSupport.defaultValidator(),
                forcePrompt);
    }

    public StructuredOutputTool(
            Class<T> outputType,
            JsonSchemaGenerator schemaGenerator,
            ObjectMapper objectMapper,
            Validator validator) {
        this(outputType, schemaGenerator, objectMapper, validator, DEFAULT_FORCE_PROMPT);
    }

    public StructuredOutputTool(
            Class<T> outputType,
            JsonSchemaGenerator schemaGenerator,
            ObjectMapper objectMapper,
            Validator validator,
            String forcePrompt) {
        this.outputType = outputType;
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.forcePrompt = forcePrompt == null || forcePrompt.isBlank() ? DEFAULT_FORCE_PROMPT : forcePrompt;
        this.spec = new ToolSpec(
                DEFAULT_NAME,
                "Return the final response as a structured JSON object matching the required schema.",
                schemaGenerator.schemaForType(outputType));
    }

    @Override
    public ToolSpec spec() {
        return spec;
    }

    @Override
    public ToolResult invoke(Object input) {
        try {
            value = objectMapper.convertValue(input, outputType);
            BeanValidationSupport.validateStructuredOutput(validator, value, outputType);
            return ToolResult.success(null, input);
        } catch (IllegalArgumentException e) {
            throw new StructuredOutputException("Structured output did not match " + outputType.getSimpleName(), e);
        }
    }

    public boolean hasValue() {
        return value != null;
    }

    public T requireValue() {
        if (value == null) {
            throw new StructuredOutputException("Structured output tool was not invoked");
        }
        return value;
    }

    public String forcePrompt() {
        return forcePrompt;
    }

    public String toolName() {
        return spec.name();
    }
}