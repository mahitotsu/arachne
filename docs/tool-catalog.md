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

If you are evaluating whether Arachne is already usable for tools, the answer is yes, but the current model is "bring or build the tools you need" rather than "consume a large built-in tool pack".

The smallest current-main references are:

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

This section separates three different questions that should not be mixed together.

- which tool families are good built-in candidates
- which ones are better as first-party opt-in modules
- which ones are better documented as custom-tool patterns rather than shipped tools

It also distinguishes the tool contract from the likely Spring or Java implementation approach.

### Priority 1: Built-In Candidates

These are the strongest candidates for Arachne-maintained built-ins.

| Tool family | Typical user value | Suggested Arachne shape | Likely implementation options | Priority | Notes |
| --- | --- | --- | --- | --- | --- |
| `current_time` | high | small built-in Java tool | `java.time`, configurable default timezone | high | trivial portability, low regression risk |
| `http_request` | high | small built-in Java tool | Spring `RestClient` first, `WebClient` optional for heavier async needs | high | natural fit for backend agents and API workflows |
| `calculator` | high | built-in Java tool or small companion module | pure Java math libraries, possibly symbolic support in a companion module | high | strong reuse value and deterministic tests |
| `batch` | high | built-in Java tool using the existing executor boundary | existing `ToolExecutor`, current sequential and parallel execution modes | high | aligns well with Arachne's current parallel/sequential execution model |
| safe file operations | high | curated Java tools such as `file_read`, `file_write`, or `editor-lite` | `java.nio.file`, Spring `Resource`, explicit allowlists and path guards | high | should be narrower and safer than the Python kitchen-sink tools |
| `resource_reader` / `resource_list` | medium to high | small built-in Spring-friendly tool family | Spring `ResourcePatternResolver`, classpath patterns, file resources, optional path allowlists | high | stronger fit than plain `ResourceLoader` because agents often need both lookup and listing |
| `message_lookup` | medium to high | small built-in Spring-friendly tool | Spring `MessageSource`, locale-aware lookup, optional code allowlists | high | strong fit for i18n, user-facing messages, and backend response assembly |
| `config_lookup` | medium | small built-in Spring-friendly tool | Spring `Environment`, `PropertyResolver`, optional typed `@ConfigurationProperties` adapters | high | should stay read-only and allowlisted |
| `validate_payload` | medium to high | small built-in Spring-friendly tool | Spring or Jakarta `Validator`, explicit DTO allowlists | high | strong fit for backend command validation and structured-input checks |
| `convert_value` | medium | small built-in Spring-friendly tool | Spring `ConversionService`, explicit target-type allowlists | medium | useful for normalization and typed boundary cleanup |

Why these first:

- they fit the current `Agent -> EventLoop -> Model / Tool` shape cleanly
- they do not require a Python runtime sidecar
- they improve day-one usefulness for backend applications immediately
- they can be documented and sampled without changing the deferred product boundary

### Priority 2: First-Party Opt-In Modules

These are useful, but better as separate opt-in modules than as core defaults.

| Tool family | Suggested Arachne shape | Likely implementation options | Priority | Notes |
| --- | --- | --- | --- | --- |
| `use_aws` subset | separate module | AWS SDK v2 clients, explicit service allowlists | medium | useful for AWS-heavy applications, but less central than generic backend tools for the main Spring audience |
| RSS | separate module | Rome or a similar Java feed library plus local persistence | medium | useful and testable, but narrower audience than core utilities |
| Tavily / Exa search | separate module | provider-specific HTTP clients plus typed response mapping | medium | clear user value, but adds external API dependencies and configuration burden |
| retrieval and memory helpers | separate module | Bedrock KB adapters, vector-store clients, explicit backend-specific configuration | medium | useful for applications already using Bedrock or vector backends |
| diagram generation | separate module | Mermaid-first generation or selected Java diagram libraries | medium | useful, but not a core agent runtime need |

### Priority 3: Custom Tool Patterns

These can be valuable in Spring applications, but they are usually too application-specific to ship as generic built-ins.

| Tool family | Recommended role | Likely implementation options | Priority | Notes |
| --- | --- | --- | --- | --- |
| approval or human handoff | sample pattern, helper SPI, or small reference module | existing interrupt and `resume(...)` boundary, DB persistence, controller endpoints, messaging | medium | valuable pattern, but usually business- and workflow-specific |
| queue or command gateway dispatch | custom tool or integration sample | Spring messaging, Spring Integration, task queue clients, outbox patterns | medium | backend-relevant, but strongly dependent on application topology |
| cached reference-data lookup | custom tool pattern first | Spring services, caches, repository facades, read-only projections | medium | good fit for business applications, but usually domain-specific |

These patterns are still worth documenting because they fit Arachne's shipped surfaces well:

- `ToolInvocationContext` for logical metadata and `AgentState`
- `ExecutionContextPropagation` for request, MDC, tracing, or security propagation across executor boundaries
- interrupt and `resume(...)` for approval-style workflows
- Spring `ApplicationEvent` integration for observation today and explicit application-side publication when intentionally requested

### Integration-Only Or MCP-Or-Later Candidates

These should not be treated as straightforward Java ports.

| Tool family | Recommended strategy | Likely implementation options | Priority | Notes |
| --- | --- | --- | --- | --- |
| dynamic `mcp_client` | wait for Arachne MCP ADR and static MCP boundary first | MCP transport adapters and tool wrappers only after the boundary is defined | low | the Python tool itself warns about significant security risk |
| browser automation | integration-only or MCP later | Playwright-like adapter, remote browser service, or MCP tool bridge | low | heavy runtime assumptions, local state, and platform variance |
| desktop automation | integration-only or MCP later | OS-specific automation bridge or remote tool boundary | low | similar risk profile to browser and shell-heavy tooling |
| `python_repl` | integration-only | isolated external runtime or sandbox service | low | directly imports a Python execution boundary into a Java SDK |
| shell-style tools | integration-only | explicit consent boundary, sandboxed process launch, or remote execution service | low | security, consent, and portability policy should be explicit first |

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

When a tool family is selected, prefer one of these strategies explicitly.

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

### 2. Separate Opt-In Module

Use this when the tool is useful but adds vendor SDKs, heavier dependencies, or configuration that should not live in the core starter.

Use this strategy for:

- search integrations such as Tavily or Exa
- retrieval or memory helpers
- RSS or diagram support

### 3. External Integration Boundary

Use this when the tool depends on another runtime, untrusted code loading, system automation, or a deferred protocol.

Use this strategy for:

- Python execution tools
- browser and desktop automation
- dynamic MCP client behavior
- shell-like tools with significant security surface

This can later become an MCP-based integration path, but that should start with the MCP architecture decision rather than with ad hoc wrappers.

## Proposed First Arachne-Maintained Pack

If Arachne starts shipping a small built-in or first-party-maintained tool catalog, the best first pack is:

1. `current_time`
2. `http_request`
3. `calculator`
4. `batch`
5. a curated file-operations surface

For a Spring-oriented expansion beyond that first utility pack, the strongest next additions are:

1. `resource_reader` / `resource_list`
2. `message_lookup`
3. `config_lookup`
4. `validate_payload`
5. `convert_value`

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