# Arachne Sample Catalog

The samples are now named after the usage pattern they teach rather than the roadmap phase that introduced the feature.

Use this catalog to choose the smallest runnable sample that matches your intended integration style.

## How To Use This Catalog

Read this file in two passes:

1. choose the smallest sample that matches the question you have right now
2. follow one of the learning tracks below when you want a broader mental model

The main rule is to prefer the narrowest sample that demonstrates one idea clearly before jumping to the composed backend reference.

## Sample Matrix

- `conversation-basics`: smallest runner-owned agent, in-memory multi-turn conversation, optional Bedrock cache metrics
- `built-in-tools`: built-in tool inheritance, allowlisted resource access, named-agent filtering
- `secure-downstream-tools`: `SecurityContext` propagation, safe capability views, downstream `RestClient` calls
- `stateful-backend-operations`: idempotent backend mutations, transaction ownership, `AgentState`, `ToolInvocationContext`
- `tool-delegation`: agent-as-tool wiring, agent-scoped tool surfaces, typed structured output
- `tool-execution-context`: logical tool metadata vs executor-boundary propagation
- `session-redis`: Redis-backed restore of conversation and `AgentState`
- `session-jdbc`: JDBC-backed restore of conversation and `AgentState`
- `approval-workflow`: hook-driven interrupt and `resume(...)` flow
- `skill-activation`: packaged `SKILL.md` discovery and delayed activation
- `streaming-steering`: callback streaming plus tool and model steering
- `domain-separation`: composed backend reference with delegation, skills, state, approval, and deterministic mutations

## Choose By Goal

- "I want the smallest end-to-end agent": `conversation-basics`
- "I want to understand built-in tools": `built-in-tools`
- "I need secure backend tools that call downstream services": `secure-downstream-tools`
- "I need safe backend mutations and replay handling": `stateful-backend-operations`
- "I need to understand what `ToolInvocationContext` is for": `tool-execution-context`
- "I want one agent to call another through tools": `tool-delegation`
- "I need session restore": `session-redis` or `session-jdbc`
- "I need approval pauses": `approval-workflow`
- "I need packaged skills": `skill-activation`
- "I need streaming or steering": `streaming-steering`
- "I want a higher-level backend reference": `domain-separation`

## Conversation And Tooling

- `conversation-basics`: one runner-owned `Agent` reused across turns for a CLI or batch-style multi-turn conversation, with an optional Bedrock prompt-caching metrics demo
- `built-in-tools`: framework-provided built-in tool inheritance, named-agent filtering, and allowlisted resource access with a deterministic in-process model
- `secure-downstream-tools`: `SecurityContext` propagation, safe operator capability views, and downstream `RestClient` calls without model-supplied credentials
- `stateful-backend-operations`: idempotent backend mutations, explicit transaction ownership, and safe `AgentState` plus `ToolInvocationContext` usage
- `tool-delegation`: agent-scoped tools, named-agent defaults, agent-as-tool delegation, and typed structured output
- `tool-execution-context`: the split between `ToolInvocationContext` and `ExecutionContextPropagation`

## Session Persistence

- `session-redis`: restore conversation history and `AgentState` through Spring Session Redis
- `session-jdbc`: restore conversation history and `AgentState` through Spring Session JDBC

These two samples intentionally stay separate because the operational setup differs meaningfully. Redis is the better reference when your production app already has shared infrastructure; JDBC is the smaller starting point when you want a local restore demo without Docker.

## Runtime Control And Extension

- `approval-workflow`: hook-driven interrupts, plugin-contributed tools, lifecycle observation, and `resume(...)`
- `skill-activation`: packaged `SKILL.md` discovery, delayed activation, and persisted loaded-skill state
- `streaming-steering`: callback-based streaming plus runtime-local tool and model steering

## Composed Backend Patterns

- `domain-separation`: a higher-level backend sample that composes named agents, capability-oriented delegation, packaged skills, approval pause/resume, session continuity, and deterministic executor-side mutations

## Learning Tracks

### 1. Core Runtime Track

Use this when you are new to Arachne and want the smallest path from one agent to practical tools.

1. `conversation-basics`
2. `built-in-tools`
3. `tool-delegation`
4. `tool-execution-context`

### 2. Backend Tooling Track

Use this when your main goal is Spring backend integration rather than chat UX.

1. `tool-execution-context`
2. `secure-downstream-tools`
3. `stateful-backend-operations`
4. `domain-separation`

### 3. Stateful Backend Track

Use this when you care about restore, replay, and long-lived workflow state.

1. `stateful-backend-operations`
2. `session-jdbc` or `session-redis`
3. `domain-separation`

### 4. Extension Track

Use this when you want to change runtime behavior after the basics are clear.

1. `approval-workflow`
2. `skill-activation`
3. `streaming-steering`
4. `domain-separation`

## Recommended Reading Order

1. `conversation-basics`
2. `built-in-tools`
3. `secure-downstream-tools`
4. `stateful-backend-operations`
5. `tool-delegation`
6. `session-redis` or `session-jdbc`
7. `approval-workflow`
8. `skill-activation`
9. `streaming-steering`
10. `domain-separation`

`tool-execution-context` is intentionally orthogonal. Read it whenever you need to understand executor-boundary propagation or tool-call metadata.

`domain-separation` is intentionally later in the reading order because it composes several narrower patterns into one backend-oriented reference sample.

## Suggested First Hour

If you want the shortest useful study plan, use this order:

1. `conversation-basics`
2. `built-in-tools`
3. `tool-execution-context`
4. `secure-downstream-tools` or `stateful-backend-operations`, depending on whether your first concern is external I/O or backend mutation

At that point you will usually know whether you need the narrower state/session samples or the broader `domain-separation` sample.