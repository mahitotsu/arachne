# 0013. Proxy-Preserving Spring Tool Discovery And Invocation

## Status

Accepted

## Context

Arachne treats Spring Boot integration as a primary source of value, and its shipped tools contract allows Spring-managed bean methods annotated with `@StrandsTool` to be discovered and invoked as tools. In real Spring applications those beans are frequently proxied for transactions, method security, and other AOP advice.

The repository therefore needs an explicit contract for proxied tool beans. Without that contract, discovery can become proxy-shape-sensitive and invocation can accidentally bypass proxy semantics by resolving annotations from one method while invoking another path.

## Decision

Arachne preserves Spring proxy semantics when discovering and invoking Spring-managed annotation tools.

The standard policy is:

- proxied Spring beans remain eligible for annotation-based tool discovery
- tool invocation must remain proxy-preserving rather than unwrapping the raw target and bypassing Spring advice
- qualifier-based tool scoping must resolve consistently for discovered proxied beans
- if a tool method is not invocable through the Spring bean proxy while preserving proxy semantics, Arachne fails fast instead of silently bypassing the proxy

## Consequences

- `@Transactional`, method security, and other Spring AOP advice remain effective when such beans are invoked as Arachne tools
- users can rely on Spring-managed tool beans behaving like normal Spring bean calls rather than a separate reflection-only execution path
- JDK proxy cases that hide implementation-only tool methods behind non-exposed interfaces are rejected explicitly rather than invoked by bypassing proxy behavior

## Non-Goals

- execution-context propagation across executor boundaries
- tool-author access to agent invocation context
- guarantees about transaction propagation beyond normal Spring proxy semantics

## Alternatives Considered

### 1. Discover annotation tools directly from the proxy class and invoke whatever reflective path is available

Rejected. It makes behavior depend on proxy shape and can silently bypass Spring advice.

### 2. Unwrap the proxy target for invocation when the proxy does not expose the method

Rejected. It would violate the main goal of preserving Spring proxy semantics.

### 3. Ignore proxied beans entirely during tool discovery

Rejected. It would break the main Spring integration use case for transactional and secured service beans.