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

### Bucket E: Telemetry (Current Arachne Boundary)
- telemetry span/usage attribute fixes that can map onto Arachne's existing hook or observation bridge without introducing a new tracing/runtime boundary.

### Deferred Reference-Only Areas (Not Current Arachne Parity Scope)
- MCP error/cleanup fixes.
- A2A lifecycle support expansion.
- Remaining telemetry changes that require a first-class OpenTelemetry/span tracer boundary rather than Arachne's current hook/application-event observation surface.

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

### Slice 7: Telemetry Per-Invocation Usage Parity (Non-breaking Subset)
- Exposed per-invocation `ModelEvent.Usage` through `AfterInvocationEvent` so hook-based telemetry can observe invocation-local usage without re-deriving it from accumulated conversation state.
- Extended Spring lifecycle observation payloads so `ArachneLifecycleApplicationEvent.InvocationObservation` carries usage on the `afterInvocation` phase.
- Kept the change provider-agnostic and additive:
   - no model-provider-specific telemetry integration added
   - no default runtime behavior changed when observation hooks are unused
- Added deterministic regression coverage for:
   - `DefaultAgentTest` asserting after-invocation hooks receive the same usage as `AgentResult.metrics()`
   - `ArachneAutoConfigurationTest` asserting the Spring application-event bridge publishes `afterInvocation` usage

### Telemetry Delta Reassessment (After Slice 7)
- Re-extracted telemetry-related sdk-python commits in the analyzed range and classified them against Arachne's current shipped boundary.
- Remaining telemetry-tagged reference commits currently fall into two groups:
   - **Span-tracer-specific, not currently applicable to Arachne without a new public tracing boundary**
      - `194c69b` emit system prompt on chat spans per GenAI semconv
      - `cda2a55` restore explicit `span.end()` to fix end-time regression
      - `ca6f599` add common GenAI attributes to event-loop cycle spans
      - `4e3ad44` remove `force_flush` from tracer shutdown path
   - **Better classified outside telemetry**
      - `888c98c` estimate input tokens before model calls: this is a token/accounting feature touching hooks and metrics, and fits Bucket B better than Bucket E
- Conclusion for current Arachne parity scope:
   - telemetry parity is complete for the existing hook/application-event observation boundary
   - MCP and A2A remain deferred and are not current parity targets
   - remaining sdk-python telemetry tracer changes are not actionable until Arachne explicitly adopts a first-class tracing/span API

### Slice 8: Token Projection Before Model Calls (Non-breaking Subset)
- Added provider-optional token estimation to the `Model` contract with `countTokens(...)` for pre-invocation request sizing.
- Implemented Bedrock-backed token counting via the AWS `CountTokens` API using the same message/system/tool request shape as normal `Converse` requests.
- Extended `BeforeModelCallEvent` with `projectedInputTokens` so hooks can see the estimated size of the upcoming request before the model call starts.
- Added `AgentResult.Metrics.projectedContextSize()` as the derived size of the next turn's baseline context (`inputTokens + outputTokens`) when usage metadata is available.
- Kept the feature additive and non-fatal:
   - providers may return `null` for token estimation
   - event-loop estimation failures fall back to `null` projected tokens and do not block inference
- Added deterministic regression coverage for:
   - before-model hooks receiving projected token counts when supported
   - graceful fallback to `null` when token estimation fails
   - Bedrock token-count request shaping and exception translation

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
- Focused (module dir): `cd arachne && mvn -Dtest=DefaultAgentTest,ArachneAutoConfigurationTest test` passed.
- Module regression re-run (module dir): `cd arachne && mvn test` passed.

## Next Integration Slices

1. **Current parity buckets**
   - All current Arachne-boundary parity buckets in this sdk-python analysis range are integrated.
   - Remaining reference deltas are deferred-only areas (`MCP`, `A2A`) or tracer-boundary work that Arachne does not currently ship.

## Scope Notes
- Arachne docs and ADRs currently treat MCP and A2A as deliberately deferred, not shipped parity gaps.
- Do not count sdk-python MCP/A2A deltas as current integration backlog unless Arachne's public boundary is explicitly widened by a separate proposal/ADR.

## Guardrails
- Keep `Agent -> EventLoop -> Model / Tool` readability unchanged.
- Preserve opt-in behavior by default (especially streaming/steering/provider specifics).
- Add tests in the same commit scope for each behavioral change.

## Bedrock Live Smoke Evidence (2026-05-09)

Status: completed with live smoke evidence after credential-source alignment and approval-resume enforcement.

Executed commands (from `arachne/docs/repository-facts.md`):

```bash
cd /home/akring/arachne/arachne
mvn -Dtest=BedrockModelIntegrationTest \
   -Darachne.integration.bedrock=true \
   -Darachne.integration.bedrock.region=ap-northeast-1 \
   -Darachne.integration.bedrock.model-id=jp.amazon.nova-2-lite-v1:0 \
   test

cd /home/akring/arachne
mvn -f samples/pom.xml -pl domain-separation \
   -Dtest=DomainSeparationBedrockIntegrationTest \
   -Darachne.integration.bedrock=true \
   -Darachne.integration.bedrock.region=ap-northeast-1 \
   -Darachne.integration.bedrock.model-id=jp.amazon.nova-2-lite-v1:0 \
   test
```

Observed results:

- Initial direct runs failed with AWS SDK credential-chain resolution (`ProfileCredentialsProvider ... Token is expired`) while `aws sts get-caller-identity` was still successful.
- Root cause: Java SDK profile-based resolution and AWS CLI credential refresh path were not aligned in this environment.
- Re-run with explicit short-lived credentials exported from AWS CLI JSON (`aws configure export-credentials --format process`) resolved credential failures.
- `BedrockModelIntegrationTest`: `BUILD SUCCESS` (`Tests run: 2, Failures: 0, Errors: 0`)
- `DomainSeparationBedrockIntegrationTest`: `BUILD SUCCESS` (`Tests run: 1, Failures: 0, Errors: 0`)
- Root cause of the earlier sample failure: after approval resume, Bedrock occasionally produced end-turn text without issuing `execute_account_operation`, leaving workflow state as `RUNNING`.
- Fix applied in sample workflow hook: when approval is already granted and execution is still missing, the pre-model hook now forces `ToolSelection.force("execute_account_operation")` to preserve the approval-boundary contract.

Credential export pattern that worked for Java SDK execution:

```bash
CREDS_JSON="$(aws configure export-credentials --profile default --format process)"
export AWS_ACCESS_KEY_ID="$(jq -r '.AccessKeyId' <<< "$CREDS_JSON")"
export AWS_SECRET_ACCESS_KEY="$(jq -r '.SecretAccessKey' <<< "$CREDS_JSON")"
export AWS_SESSION_TOKEN="$(jq -r '.SessionToken' <<< "$CREDS_JSON")"
unset AWS_PROFILE AWS_DEFAULT_PROFILE
```

Next action:

1. Keep using explicit exported session credentials (or otherwise ensure Java SDK and CLI use the same valid source) when running Bedrock smoke from non-interactive shells.
2. Preserve the new hook regression test so approved resume cannot silently regress to `RUNNING` without execution.
