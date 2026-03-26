# ADR 0017: Built-In Tool Exposure And Inheritance

## Status

Accepted

## Context

Arachne currently ships a provider-independent tool loop, Spring-first tool discovery, and agent-level control over discovered tools through qualifiers and `useDiscoveredTools(false)`. That contract is already part of the accepted Spring and Phase 2 tool boundaries.

At the same time, the repository now has an explicit built-in tool catalog direction centered on Arachne-maintained utilities such as `current_time`, read-only resource access, Spring-backed lookup helpers, and a later read-only HTTP tool. Those tools are different from application-defined `@StrandsTool` methods in two important ways:

- they are framework-provided infrastructure rather than user-defined application tools
- some of them are intended to be available by default as a baseline utility surface

That creates a design problem that user-guide prose alone does not settle:

- where built-in tools should live in the Spring wiring model
- whether they should be resolved through the same path as application-discovered tools
- how agent-level defaults and opt-out behavior should work
- whether configuring explicit tools for an agent replaces or augments the default built-in surface
- how built-in selection should coexist with the existing qualifier-based scope control for discovered tools

Because these decisions affect Spring wiring, `AgentFactory` behavior, and the public default tool contract, they need an explicit architectural record rather than implementation notes alone.

## Decision

Arachne adopts the following built-in tool boundary.

- Built-in tools are treated as framework-provided infrastructure and are published as dedicated Spring-managed beans or bean collections through Arachne auto-configuration rather than through normal annotation discovery.
- `AgentFactory` resolves built-in tools separately from application-discovered tools, plugin tools, and manually registered tools.
- Read-only built-in tools are inherited by default for an agent runtime unless that inheritance is explicitly disabled.
- When an agent specifies additional tools, the default read-only built-in tools remain visible unless built-in inheritance is explicitly disabled.
- Built-in inheritance and application-discovered tool enablement are configured separately so that `useDiscoveredTools(false)` does not implicitly disable built-in defaults.
- Agent-level configuration may filter built-in tools through built-in-specific selectors such as explicit tool names and built-in qualifier groups, but those selectors are kept conceptually separate from the existing discovered-tool qualifier bridge.
- Built-in tools that can mutate external state are not part of the default inherited surface and require explicit opt-in.

The intended runtime layering is:

1. built-in tools resolved from Arachne-managed infrastructure
2. application-discovered tools resolved from `@StrandsTool` discovery
3. plugin-provided tools resolved from runtime-local plugins
4. manually registered tools resolved from the builder

The exact property names and helper classes may evolve during implementation, but the architecture above is the contract to preserve.

## Consequences

- Arachne can ship a small baseline tool surface without requiring every Spring application to define those tools manually.
- The current discovered-tool contract remains readable because built-in inheritance is not overloaded onto `useDiscoveredTools`.
- Spring Boot users get a clearer mental model: framework-provided built-ins, application-discovered tools, plugin tools, and manual tools are related but distinct sources.
- Configuration surface grows because built-in inheritance and built-in selection need explicit settings at global and named-agent scope.
- Built-in tools can still participate in per-agent scope control, but that control no longer depends on pretending that framework-provided tools are ordinary user-discovered beans.
- Later mutating tools such as write-capable file tools, mutating HTTP operations, or `batch` can be kept out of the default inherited surface without weakening the default read-only baseline.

## Alternatives Considered

### Treat built-in tools as ordinary discovered tools

Rejected. This would blur the distinction between framework-provided utilities and application-defined `@StrandsTool` methods, and it would overload existing discovered-tool settings with a second meaning.

### Make built-in tools explicit opt-in only through plugins or manual builder registration

Rejected. That would preserve a very small core boundary, but it would also undercut the purpose of shipping Arachne-maintained built-ins as a standard baseline utility surface.

### Make built-in tools always visible with no agent-level opt-out or filtering

Rejected. Arachne already treats agent-scoped tool selection as an important part of the public contract. Removing per-agent control for built-ins would regress that model and make multi-agent applications harder to reason about.

### Document the behavior only in the user guide and skip an ADR

Rejected. The issue is not only tool usage syntax. It changes Spring wiring, `AgentFactory` resolution behavior, default inheritance rules, and the relationship between built-in and discovered tools. Those are architectural decisions that need a stable record of the chosen boundary and rejected alternatives.