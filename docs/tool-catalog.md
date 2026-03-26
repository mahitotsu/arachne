# Arachne Tool Catalog

This catalog has two jobs.

- explain the tool surfaces that Arachne already ships today
- classify the most plausible next tool families for Arachne-maintained support

Use [docs/project-status.md](docs/project-status.md) for the current shipped contract and deferred boundary. Use this document when you need a more tool-centered view of that same boundary.

## Scope

Arachne already ships a provider-independent tool loop with Spring-first authoring and runtime-local composition.

The current shipped tool surface includes:

- annotation-driven tools through `@StrandsTool` and `@ToolParam`
- programmatic `Tool` implementations
- logical invocation metadata through `ToolInvocationContext`
- runtime validation for tool input and structured output
- manual tool registration on the builder
- discovered-tool scoping through qualifiers and opt-out control
- configurable sequential or parallel execution
- plugin-contributed tools such as skill activation and skill resource loading
- agent-as-tool delegation through normal Spring services

For the underlying contract, see [docs/user-guide.md](docs/user-guide.md), [docs/project-status.md](docs/project-status.md), [docs/adr/0007-phase2-tool-contracts.md](docs/adr/0007-phase2-tool-contracts.md), and [docs/adr/0014-tool-invocation-context-contract.md](docs/adr/0014-tool-invocation-context-contract.md).

## Available Today

If you are evaluating whether Arachne is already usable for tools, the answer is yes. The current model is still intentionally small, but it now includes a default read-only built-in pack alongside application-defined and plugin-provided tools.

The current built-in pack is:

- `current_time`
- `resource_reader`
- `resource_list`

The smallest current-main references are:

- [samples/built-in-tools/README.md](samples/built-in-tools/README.md) for built-in inheritance, named-agent filtering, and allowlisted resource access
- [samples/tool-delegation/README.md](samples/tool-delegation/README.md) for annotation-driven tools, named-agent scoping, and agent-as-tool delegation
- [samples/tool-execution-context/README.md](samples/tool-execution-context/README.md) for the split between `ToolInvocationContext` and `ExecutionContextPropagation`
- [samples/approval-workflow/README.md](samples/approval-workflow/README.md) for plugin-contributed tools and interrupt-aware execution
- [samples/skill-activation/README.md](samples/skill-activation/README.md) for shipped plugin tools that expose delayed skill activation and skill resource reading

## Catalog Policy

Not every Strands Python tool should become an Arachne-maintained Java tool.

The working policy is:

- prefer Java-native tools that fit Spring applications cleanly
- prefer tools with deterministic tests and stable operating assumptions
- prefer tools whose value is visible even when the built-in provider remains Bedrock-only
- avoid turning a tool addition into a hidden new runtime boundary
- avoid tools that smuggle in deferred capabilities such as MCP orchestration, multi-agent orchestration, or provider switching without an ADR
- treat Python-only, OS-heavy, or sidecar-heavy tools as separate integration problems rather than default library features

## Recommended Tool Families

This section focuses on the strongest built-in candidates for Arachne-maintained support.

It distinguishes the tool contract from the likely Spring or Java implementation approach.

### Priority 1: Built-In Candidates

These are the strongest candidates for Arachne-maintained built-ins.

Shipped now on the current main branch:

- `current_time`
- `resource_reader`
- `resource_list`

| Tool family | Typical user value | Suggested Arachne shape | Likely implementation options | Priority | Notes |
| --- | --- | --- | --- | --- | --- |
| `current_time` | high | small built-in Java tool | `java.time`, configurable default timezone | shipped | now part of the default read-only baseline |
| `http_request` | high | small built-in Java tool | Spring `RestClient` first, `WebClient` optional for heavier async needs | high | natural fit for backend agents and API workflows |
| `calculator` | high | built-in Java tool or small companion module | pure Java math libraries, possibly symbolic support in a companion module | high | strong reuse value and deterministic tests |
| `batch` | high | built-in Java tool using the existing executor boundary | existing `ToolExecutor`, current sequential and parallel execution modes | high | aligns well with Arachne's current parallel/sequential execution model |
| safe file operations | high | curated Java tools such as `file_read`, `file_write`, or `editor-lite` | `java.nio.file`, Spring `Resource`, explicit allowlists and path guards | high | should be narrower and safer than the Python kitchen-sink tools |
| `resource_reader` / `resource_list` | medium to high | small built-in Spring-friendly tool family | Spring `ResourcePatternResolver`, classpath patterns, file resources, optional path allowlists | shipped | now part of the default read-only baseline |
| `message_lookup` | medium to high | small built-in Spring-friendly tool | Spring `MessageSource`, locale-aware lookup, optional code allowlists | high | strong fit for i18n, user-facing messages, and backend response assembly |
| `config_lookup` | medium | small built-in Spring-friendly tool | Spring `Environment`, `PropertyResolver`, optional typed `@ConfigurationProperties` adapters | high | should stay read-only and allowlisted |
| `validate_payload` | medium to high | small built-in Spring-friendly tool | Spring or Jakarta `Validator`, explicit DTO allowlists | high | strong fit for backend command validation and structured-input checks |
| `convert_value` | medium | small built-in Spring-friendly tool | Spring `ConversionService`, explicit target-type allowlists | medium | useful for normalization and typed boundary cleanup |

Why these first:

- they fit the current `Agent -> EventLoop -> Model / Tool` shape cleanly
- they do not require a Python runtime sidecar
- they improve day-one usefulness for backend applications immediately
- they can be documented and sampled without changing the deferred product boundary

### Keep Deferred For Now

Some Strands Python tools are not just tools. They depend on capability areas that Arachne currently keeps out of scope.

| Tool family | Why not now |
| --- | --- |
| `swarm` | depends on multi-agent orchestration staying out of the current shipped boundary |
| `workflow` | risks growing Arachne into a workflow engine rather than a clear tool/runtime boundary |
| `graph` / `agent_graph` | overlaps directly with deferred Graph orchestration |
| `a2a_client` | depends on deferred A2A support |
| `use_agent` / nested-agent helpers | can blur the current runtime-local delegation boundary and provider configuration model |
| `think` | useful, but closer to orchestration or nested-agent behavior than to a simple backend utility |

## Spring-Specific Notes

Because Arachne's main audience is Spring-based backend applications, Spring utilities should usually be described as implementation approaches, not as tool names.

Examples:

- `http_request` can be implemented with Spring `RestClient` and optionally `WebClient`
- `resource_reader` and `resource_list` can be implemented with Spring `ResourcePatternResolver`
- `message_lookup` can be implemented with Spring `MessageSource`
- `config_lookup` can be implemented with Spring `Environment` or `PropertyResolver`
- `validate_payload` can be implemented with Spring or Jakarta `Validator`
- `convert_value` can be implemented with Spring `ConversionService`
- session- or resume-aware custom tools can persist state through the existing `SessionManager` and Spring Session integration

This distinction matters because Arachne should expose clear, portable tool contracts even when the implementation happens to use a Spring abstraction under the hood.

Within the current Spring-oriented discussion, the preferred core candidates are:

- `ResourcePatternResolver`
- `MessageSource`
- `Environment`
- `Validator`
- `ConversionService`

Other Spring-oriented ideas remain lower priority or excluded unless a narrower, safer tool contract emerges.

## Spring Anti-Patterns To Avoid

Even in a Spring-first library, some tool shapes are a bad fit and should not be first-party defaults.

Avoid these as generic built-ins:

- a generic `ApplicationContext` or bean-invocation tool
- a generic JPA repository inspection or mutation tool
- a tool that exposes arbitrary Spring Security state mutation to the model
- a reflection-driven "call any service method" adapter
- a generic unrestricted SpEL execution tool
- a generic unrestricted script-execution tool

Those shapes make it too easy to blur application boundaries, widen the attack surface, and lose the explicit tool contract that Arachne currently keeps readable.

## Porting Strategy

For the current scope, prefer Java-native ports that fit the shipped Spring and backend runtime boundary.

### 1. Java-Native Port

Use this when the tool is a good Spring or backend fit, has stable input and output behavior, and does not depend on a Python runtime.

Use this strategy for:

- `current_time`
- `http_request`
- `calculator`
- `batch`
- curated file tools
- `resource_reader` and `resource_list`
- `message_lookup`
- `config_lookup`
- `validate_payload`
- `convert_value`

Anything that depends on another runtime, system automation, or a deferred protocol should stay deferred and start with an ADR or ADR update before implementation.

## Proposed First Arachne-Maintained Pack

The first shipped Arachne-maintained built-in pack is:

1. `current_time`
2. `resource_reader`
3. `resource_list`

The strongest next additions after that shipped baseline are:

1. `http_request`
2. `calculator`
3. `message_lookup`
4. `config_lookup`
5. `validate_payload`
6. `convert_value`

That pack would give users an immediately useful baseline for:

- API integration
- backend automation
- deterministic utility work
- multi-step tool chaining
- prompt-to-action workflows that do not require remote orchestration

## Relationship To Deferred Features

This document does not change the deferred boundary.

In particular, it does not imply that Arachne already supports:

- MCP tool support
- Swarm orchestration
- Graph orchestration
- A2A protocol support
- provider expansion beyond Bedrock

If a future tool addition would effectively introduce one of those areas, start with an ADR or ADR update first.