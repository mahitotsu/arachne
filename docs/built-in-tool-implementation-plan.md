# Built-In Tool Implementation Plan

This document turns the current tool-catalog direction into an implementation task list for Arachne-maintained built-in tools.

It assumes the following working decisions:

- built-in tools are Spring-managed beans that exist in the `ApplicationContext` unless explicitly opted out
- each agent can control tool availability through properties and qualifier-based filtering
- read-only built-in tools are inherited by default when an agent does not specify its own tools
- agents that specify their own tools still inherit the default built-in tools unless configured not to
- built-in tool behavior and user-discovered tool behavior are configured separately
- built-in tool samples live in a dedicated sample rather than being folded into an existing sample

## Scope

This plan covers the current built-in candidates from [docs/tool-catalog.md](docs/tool-catalog.md) and turns them into phased implementation work.

The plan uses these risk buckets:

- Phase 1: low-risk read-only tools that fit the current Spring and runtime boundaries directly
- Phase 2: read-only Spring integration tools that need stable allowlist and configuration policy
- Phase 3: higher-value integrations that need additional public contract decisions
- Phase 4: tools that can mutate external state or blur the existing tool/runtime boundary if added too early

## Working Classification

### Default Read-Only Built-In Candidates

- `current_time`
- `resource_reader`
- `resource_list`
- `message_lookup`
- `config_lookup`
- `validate_payload`
- `convert_value`
- `calculator`
- `http_request` only if the contract is explicitly limited to read-only request semantics

### Non-Default Or Later Built-In Candidates

- mutating `http_request` variants
- safe file write or edit tools
- `batch`

`batch` is not treated as read-only by default because it can compose mutating tools even if the batch tool itself does not write state directly.

## Cross-Cutting Design Tasks

These tasks should be completed before the first tool ships.

- [x] Add an ADR for built-in tool exposure, inheritance, and agent-level opt-out behavior
- [x] Decide whether built-in tools are registered through a dedicated built-in registry, plugin, or explicit bean collection rather than normal annotation discovery
- [x] Add `ArachneProperties` support for built-in tool inheritance and opt-out at the global and named-agent level
- [x] Keep built-in default inheritance separate from discovered-tool enablement so existing qualifier behavior stays readable
- [x] Implement built-in-specific selectors for tool names and built-in qualifier groups without overloading discovered-tool qualifiers
- [x] Define a shared allowlist model for read-only resource, config, and network access where applicable
- [x] Decide the package layout for built-in tools and supporting policy classes
- [x] Add a dedicated built-in tools sample project

## Phase 1: Foundation Read-Only Tools

Goal: ship the smallest useful built-in pack without changing the current runtime boundary.

### Tools

- `current_time`
- `resource_reader`
- `resource_list`

### Tasks

- [x] Add the built-in tool registration path to Spring auto-configuration and `AgentFactory`
- [x] Implement `current_time` as a narrow Java-native tool with optional timezone input and deterministic formatting
- [x] Implement `resource_reader` with explicit read-only allowlists and path normalization
- [x] Implement `resource_list` with the same allowlist model as `resource_reader`
- [x] Add unit tests for successful invocation, invalid input, and allowlist rejection
- [x] Add agent-builder tests that prove default built-ins are inherited when no per-agent override is present
- [x] Add agent-builder tests that prove explicit opt-out disables built-in inheritance
- [x] Add a sample that demonstrates default built-ins, named-agent overrides, and qualifier-based filtering
- [x] Update [docs/tool-catalog.md](docs/tool-catalog.md), [docs/user-guide.md](docs/user-guide.md), and [docs/project-status.md](docs/project-status.md) when the behavior ships

### Completion Criteria

- all three tools work without introducing a new execution boundary
- built-in inheritance is documented and tested
- the sample demonstrates both default and opt-out behavior
- `mvn test` passes with the new coverage

## Phase 2: Spring Lookup And Validation Tools

Goal: add Spring-first tools that reuse existing Arachne and Spring boundaries without exposing arbitrary bean access.

### Tools

- `message_lookup`
- `config_lookup`
- `validate_payload`
- `convert_value`

### Tasks

- [ ] Define the narrow input and output contracts for each tool before implementation
- [ ] Implement `message_lookup` using `MessageSource` with locale-aware lookup and optional code allowlists
- [ ] Implement `config_lookup` as a read-only key lookup with explicit property allowlists
- [ ] Implement `validate_payload` against explicit DTO allowlists rather than arbitrary class loading
- [ ] Implement `convert_value` against explicit target-type allowlists
- [ ] Add tests that prove unrestricted bean or type access is not exposed through these tools
- [ ] Extend the built-in sample or add scenario coverage that shows backend-oriented use cases for these tools
- [ ] Update documentation and the shipped scope once the tools land

### Completion Criteria

- none of the tools require exposing `ApplicationContext` or arbitrary Spring beans
- all class or type-based behavior is allowlisted and documented
- default behavior remains unchanged when the tools are not used

## Phase 3: Read-Only HTTP Tool

Goal: add a high-value integration tool without turning Arachne into a generic outbound execution surface.

### Tool

- read-only `http_request`

### Tasks

- [ ] Decide whether the initial contract allows only `GET` and `HEAD`, or another narrow read-only subset
- [ ] Add configuration for base URL allowlists, timeout, header policy, and maximum response body size
- [ ] Implement the tool on Spring `RestClient`
- [ ] Add tests for allowlist rejection, timeout handling, response truncation, and invalid method rejection
- [ ] Document how authentication is supplied without exposing arbitrary secret mutation to the model
- [ ] Re-evaluate whether an ADR update is needed once the final contract is fixed

### Completion Criteria

- the contract is explicitly read-only and enforced by tests
- the tool cannot be used as an unrestricted outbound HTTP client
- documentation explains the security and configuration model clearly

## Phase 4: Mutating Or Boundary-Expanding Tools

Goal: handle the remaining candidates only after the built-in tool model is stable.

### Candidates

- mutating file tools
- mutating `http_request`
- `batch`

### Tasks

- [ ] Split file operations into read-only and mutating surfaces rather than keeping one mixed family
- [ ] Decide whether mutating file tools need interrupt or approval support before execution
- [ ] Decide whether mutating HTTP behavior belongs in the same tool family or a separate opt-in surface
- [ ] Define the `batch` contract carefully so it does not become ad hoc orchestration or nested runtime control
- [ ] Add ADR coverage before implementation if any of these tools widen the runtime boundary materially

### Completion Criteria

- no tool in this phase ships without an explicit contract for mutation and policy control
- no tool in this phase weakens the current `Agent -> EventLoop -> Model / Tool` readability

## Recommended Delivery Order

1. ADR for built-in tool exposure and inheritance
2. built-in registration path in Spring auto-configuration and `AgentFactory`
3. `current_time`
4. `resource_reader`
5. `resource_list`
6. dedicated built-in tools sample
7. `message_lookup`
8. `config_lookup`
9. `validate_payload`
10. `convert_value`
11. read-only `http_request`
12. `calculator`
13. mutating file tools
14. mutating `http_request`
15. `batch`

## Verification Checklist

- [x] unit tests for each new built-in tool
- [x] builder and auto-configuration tests for default inheritance and opt-out behavior
- [x] documentation updates for shipped behavior
- [x] sample coverage for the intended wiring model
- [x] repository verification with `mvn test`

## Open Decisions

- whether `calculator` should stay in the default read-only set or be held until after the Spring lookup tools
- whether the built-in sample should be a single broad sample or multiple minimal samples
- how much common policy infrastructure should be shared between resource, config, and HTTP allowlists without over-abstracting the first implementation