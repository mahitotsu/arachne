# ADR 0016: Bedrock Prompt Caching And Usage Metrics

## Status

Accepted

## Context

Arachne ships only a Bedrock-backed provider on the current branch, but the public Bedrock surface had stayed close to the minimum request path: model id, region, system prompt, tools, and streaming. That left Bedrock prompt caching unavailable even though it is one of the more practical Bedrock-specific optimizations for long-lived agents with stable system prompts and tool definitions.

At the same time, the runtime already collected provider usage at the event-loop boundary, but `AgentResult` did not expose any usage information to callers. That made cache behavior impossible to observe without adding provider-specific code or custom instrumentation.

The change touches public API and Spring configuration:

- new Spring properties for Bedrock system-prompt and tool cache points
- a new `BedrockModel.PromptCaching` configuration value in the direct-Java API
- a new `AgentResult.metrics()` surface for accumulated usage counters

## Decision

Adopt the following boundary:

- Keep Bedrock cache-point placement localized in `BedrockModel`.
- Support only two opt-in cache placements for now: the static system prompt and the emitted tool definitions.
- Keep message-level cache placement deferred until the core content model is intentionally widened.
- Expose cache usage through provider-neutral accumulated usage on `AgentResult.metrics()` rather than leaking Bedrock SDK types through the core result API.

The concrete API shape is:

- Spring configuration under `arachne.strands.model.bedrock.cache.*` and `arachne.strands.agents.<name>.model.bedrock.cache.*`
- direct-Java configuration through `BedrockModel.PromptCaching`
- accumulated invocation usage through `AgentResult.metrics().usage()`

## Consequences

- Arachne gains a practical Bedrock optimization without widening the core content model to provider-specific cache-point blocks.
- Bedrock-specific request construction remains localized to `BedrockModel`, which preserves the core `Agent -> EventLoop -> Model / Tool` flow.
- Callers can inspect cache write/read token counts on ordinary agent results without depending on AWS SDK classes.
- Message caching is still not available because the core `ContentBlock` model does not yet express cache-point placement in conversation history.

## Alternatives Considered

### Expose Bedrock SDK usage objects directly on `AgentResult`

Rejected. That would leak provider-specific SDK types into the core runtime contract and make later provider additions harder to align.

### Add a generic core cache-point concept before implementing Bedrock caching

Rejected for now. The current task only requires Bedrock system-prompt and tool-definition caching. Forcing a generic content-model expansion now would widen the surface more than needed.

### Implement message caching in the same change

Rejected for now. Message caching needs an explicit decision about where cache-point markers live in the conversation content model and how they interact with conversation management, summarization, and structured output retries.