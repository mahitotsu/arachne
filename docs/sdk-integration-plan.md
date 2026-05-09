# Python SDK Integration Plan for Arachne

## Objective
Integrate high-value changes from `refs/sdk-python` commit range `566e5ada..f8621853` into Arachne incrementally, with deterministic tests per slice.

## Verified Baseline
- Arachne pin: `566e5ada67d1e96234cea3f41b991c346f4defaf` (`git ls-tree HEAD refs/sdk-python`)
- Submodule HEAD for analysis: `f8621853d3bbd1e69b59d3871f4ca363fdcec0e0`
- Delta size: 83 commits (v1.31.0 -> v1.39.0)

## Change Buckets In sdk-python Delta

### Bucket A: Conversation Management
- `window_size=0` support and negative validation tightening.
- Tool-heavy history fallback trim behavior.
- Proactive context compression capability.

### Bucket B: Token/Context Accounting
- Native token counting fallbacks and provider-specific corrections.
- Context window metadata and model limit handling improvements.

### Bucket C: Bedrock/OpenAI Provider Details
- Bedrock strict tools / service tier / default model updates.
- OpenAI Responses and stateful-context related fixes.

### Bucket D: Snapshot/State APIs
- Agent snapshot save/load additions and message metadata/state extensions.

### Bucket E: MCP/A2A/Telemetry
- MCP error/cleanup fixes.
- A2A lifecycle support expansion.
- telemetry span/usage attribute fixes.

## Integrated In This Session (Completed)

### Slice 1: Sliding Window Parity
- Implemented `windowSize=0` as "clear all conversation messages" mode.
- Validation changed to reject negative values only.
- Added fallback trim behavior for tool-heavy histories when no clean boundary is found.
- Added/updated regression tests for all above behaviors.

### Slice 2: Summarizing Split Safety
- Added fallback split behavior for tool-heavy tails when no clean tool boundary is found.
- Ensured `preserveRecentMessages` remains effective in that edge case.
- Added regression test that proves recent tail messages are preserved and only the intended prefix is summarized.

### Slice 3: Proactive Compression Hook Path
- Extended `ConversationManager` to participate in runtime hook registration.
- Wired builder-created `ConversationManager` into `AgentFactory` hook resolution.
- Added opt-in proactive before-model-call compression hooks for sliding and summarizing managers.
- Added regression tests for both manager-local hook behavior and AgentFactory hook wiring.

### Slice 4: Bedrock Service Tier Parity
- Added optional `serviceTier` support to `BedrockModel` (opt-in, default unchanged).
- Applied configured service tier to both `ConverseRequest` and `ConverseStreamRequest`.
- Added Spring property surface for default and named model overrides:
   - `arachne.strands.model.bedrock.service-tier`
   - `arachne.strands.agents.<name>.model.bedrock.service-tier`
- Wired service-tier propagation through `AgentFactoryModelResolver` merge/copy logic.
- Added request-level and resolver-level regression tests.

### Slice 5: Bedrock Strict Tools Parity (Opt-in Subset)
- Added optional `strictTools` support to `BedrockModel` (default `false`, no behavior change when unset).
- When enabled, tool request shaping now:
   - sets `ToolSpecification.strict=true`
   - recursively injects `additionalProperties: false` into JSON-schema `object` nodes
   - preserves caller-owned schema objects (non-mutating deep-copy transform)
- Added Spring property surface for default and named model overrides:
   - `arachne.strands.model.bedrock.strict-tools`
   - `arachne.strands.agents.<name>.model.bedrock.strict-tools`
- Wired strict-tools propagation through `AgentFactoryModelResolver` default/merge/copy/override detection paths.
- Added deterministic regression tests for request shaping, default propagation, named override propagation, and property binding containers.

### Slice 6: Snapshot/State Parity (Non-breaking Subset)
- Added new in-memory snapshot contract to `Agent`:
   - `takeSnapshot()`
   - `takeSnapshot(Map<String, Object> appData)`
   - `loadSnapshot(AgentSnapshot snapshot)`
- Introduced `AgentSnapshot` as a versioned runtime snapshot payload with:
   - `scope=agent`
   - `schemaVersion=1.0`
   - `createdAt`
   - `data`
   - `appData`
- Snapshot `data` fields currently captured/restored in Java parity subset:
   - `messages`
   - `state`
   - `conversation_manager_state`
   - `interrupt_state`
- `loadSnapshot(...)` restores only fields present in `snapshot.data`, leaving absent fields unchanged.
- Snapshot restore path reuses existing runtime/session contracts (`AgentSession` + `ConversationManager.restore(...)`) to avoid contract drift.
- Added deterministic `DefaultAgentTest` coverage for:
   - snapshot deep-copy behavior for state/appData
   - partial restore semantics (field-presence based)
   - conversation-manager state + pending interrupt restore

### Validation Result
- Focused: `mvn -pl arachne -Dtest=SlidingWindowConversationManagerTest test` passed.
- Focused: `mvn -pl arachne -Dtest=SummarizingConversationManagerTest test` passed.
- Focused: `mvn -pl arachne -Dtest=SlidingWindowConversationManagerTest,SummarizingConversationManagerTest,AgentFactoryTest test` passed.
- Focused: `mvn -pl arachne -Dtest=BedrockModelRequestTest,AgentFactoryTest,AgentFactoryDefaultsResolverTest,ArachnePropertiesTest test` passed.
- Module regression: `mvn -pl arachne test` passed.
- Focused (module dir): `cd arachne && mvn -Dtest=BedrockModelRequestTest,AgentFactoryTest,AgentFactoryDefaultsResolverTest,ArachnePropertiesTest test` passed.
- Module regression (module dir): `cd arachne && mvn test` passed.
- Focused (module dir): `cd arachne && mvn -Dtest=DefaultAgentTest test` passed.
- Module regression re-run (module dir): `cd arachne && mvn test` passed.

## Next Integration Slices

1. **Telemetry parity subset**
   - Port deterministic, provider-agnostic telemetry improvements first.

## Guardrails
- Keep `Agent -> EventLoop -> Model / Tool` readability unchanged.
- Preserve opt-in behavior by default (especially streaming/steering/provider specifics).
- Add tests in the same commit scope for each behavioral change.