# Arachne Sample Catalog

The samples are now named after the usage pattern they teach rather than the roadmap phase that introduced the feature.

Use this catalog to choose the smallest runnable sample that matches your intended integration style.

## Conversation And Tooling

- `conversation-basics`: one runner-owned `Agent` reused across turns for a CLI or batch-style multi-turn conversation
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

## Recommended Reading Order

1. `conversation-basics`
2. `tool-delegation`
3. `session-redis` or `session-jdbc`
4. `approval-workflow`
5. `skill-activation`
6. `streaming-steering`

`tool-execution-context` is intentionally orthogonal. Read it whenever you need to understand executor-boundary propagation or tool-call metadata.