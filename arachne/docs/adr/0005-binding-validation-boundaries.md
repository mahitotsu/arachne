# 0005. Binding And Validation Boundaries

## Status

Accepted

## Context

Phase 2 introduced tool-input validation and structured-output validation. In the current implementation, both tool invocation and structured-output capture first convert values through `ObjectMapper` and then apply Bean Validation. `MethodTool` converts the model-provided input map into method argument types and then calls `validator.forExecutables().validateParameters(...)`. `StructuredOutputTool` does the same by converting into the output type and then calling `validator.validate(...)`.

That flow is sound, but from the Phase 3.5 perspective it still leaves two problems. First, binding failure and validation failure are easy to discuss as if they were the same thing, which blurs responsibility boundaries. Second, Spring integration already treats `Validator` as a shared auto-configured bean, while `ObjectMapper` is still created ad hoc in several places, so the reuse boundary for standard Spring beans is not yet fixed.

Spring also offers `ConversionService` as a standard conversion infrastructure, but Arachne's current tool binding is implemented as a JSON-like object-graph-to-Java conversion problem, so this is not yet the right stage to make `ConversionService` mandatory. Before further implementation changes, Phase 3.5 needs a clear statement of both the semantics of binding versus validation and the priority order for reusing Spring-managed beans.

## Decision

Arachne treats tool input handling and structured output handling as a two-stage flow of binding and validation. In Spring integration, `Validator` is the standard reused entrypoint for Bean Validation, while `ObjectMapper` used for object binding should preferentially reuse the shared Spring-managed bean. `ConversionService` is not part of the required contract at this time.

The standard policy is:

- binding is the stage that converts JSON-like model input into Java tool arguments or a structured-output type
- validation is the stage that applies Bean Validation constraints to the bound Java object
- binding failure and validation failure are treated as distinct error categories so their causes and responsibilities are not conflated
- Spring integration reuses `Validator` as the standard validation hook
- when Spring integration performs application-level data binding, it should preferentially reuse the Spring-managed `ObjectMapper`
- `ConversionService` is not included in Arachne's standard binding contract unless a concrete need emerges; if that happens, its introduction should be reconsidered narrowly
- protocol-specific or provider-specific internal serialization does not have to be forced onto the shared Spring mapper; reuse should first be organized around application-facing boundaries such as tool binding, structured output, and session payloads

## Consequences

- Tool-input errors and Bean Validation errors can be explained separately, making failures easier for users to understand.
- In Spring Boot integration, not only `Validator` but also the reuse policy for `ObjectMapper` becomes clearer, so application-level customization can flow into Arachne binding more naturally.
- At the same time, broader `ObjectMapper` reuse means the code paths that assume `new ObjectMapper()` will need gradual cleanup.
- Keeping `ConversionService` out of the standard contract for now avoids premature abstraction without a real use case.
- The next implementation and cleanup work should focus on aligning mapper/validator injection paths around `AnnotationToolScanner`, `MethodTool`, `StructuredOutputTool`, and the session-manager area.

## Alternatives Considered

### 1. Continue treating binding and validation as a single "input validation" concept

Rejected. Type-conversion failure and Bean Validation constraint violations have different causes and different remedies, so continuing to blur them would undermine the Phase 3.5 goal of clarifying responsibility boundaries.

### 2. Make `ObjectMapper` and `ConversionService`, not just `Validator`, mandatory Spring contracts immediately

Rejected. Reusing `ObjectMapper` is useful, but making `ConversionService` mandatory at the same time would be an overreaction to the current binding problem.

### 3. Avoid Spring-managed beans and have Arachne create its own mapper and validator each time

Rejected. It would weaken Spring Boot integration consistency and make user customization harder to propagate.

### 4. Reuse only `ObjectMapper` and move validation to an Arachne-specific implementation

Rejected. Jakarta Bean Validation is already part of the public behavior introduced in Phase 2, so reusing `Validator` remains the natural choice in Spring environments.
