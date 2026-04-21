# Arachne Sample Catalog

This directory contains runnable samples for the features that Arachne provides now.

Use [arachne/docs/project-status.md](../arachne/docs/project-status.md) as the canonical source of truth for whether a capability is shipped. This page is the sample-selection and sample-verification map for that shipped surface.

`food-delivery-demo` has moved out of `samples/` and now lives at the repository root as its own multi-module product track.

Use this page to choose the right sample. Use each sample's own README for setup, run commands, and what to inspect in the code.

## Start Here

If you are new to Arachne, start in this order:

1. `conversation-basics`
2. `built-in-tools`
3. `tool-delegation`
4. `tool-execution-context`

After that, move to the narrower sample that matches your current integration problem.

## Before Running Samples

Most samples depend on the local `com.mahitotsu.arachne:arachne` snapshot, so install the library module first:

```bash
mvn -pl arachne -am install
```

Then run the sample from its own directory.

For sample-reactor verification or readiness checks, prefer this stricter sequence so the sample build does not pick up a stale local snapshot:

```bash
mvn -pl arachne -am install -DskipTests
mvn -f samples/pom.xml test
```

## Choose By Goal

| Goal | Sample |
| --- | --- |
| smallest end-to-end runtime | `conversation-basics` |
| built-in tools and resource allowlists | `built-in-tools` |
| secure downstream API access | `secure-downstream-tools` |
| state-changing backend tools | `stateful-backend-operations` |
| agent-to-agent delegation and typed output | `tool-delegation` |
| tool-call metadata vs thread-local propagation | `tool-execution-context` |
| Redis-backed session restore | `session-redis` |
| JDBC-backed session restore | `session-jdbc` |
| interrupt / resume approval flow | `approval-workflow` |
| packaged skills and delayed activation | `skill-activation` |
| callback streaming and steering | `streaming-steering` |
| higher-level composed backend reference | `domain-separation` |

## Sample Matrix

Status legend:

- `tested in sample reactor`: the sample has sample-local tests that run under `mvn -f samples/pom.xml test`
- `compile-checked in sample reactor`: the sample is built by the sample reactor, but does not yet have sample-local tests
- `concept-only`: the directory is design material and is excluded from the runnable sample reactor

| Sample | Main topic | External dependency | Status |
| --- | --- | --- | --- |
| `conversation-basics` | in-memory multi-turn runtime | Bedrock | `compile-checked in sample reactor` |
| `built-in-tools` | built-in tools | none | `tested in sample reactor` |
| `secure-downstream-tools` | secure downstream tool calls | none | `tested in sample reactor` |
| `stateful-backend-operations` | idempotent backend mutations | none | `tested in sample reactor` |
| `tool-delegation` | delegation and structured output | Bedrock | `compile-checked in sample reactor` |
| `tool-execution-context` | invocation vs execution context | none | `tested in sample reactor` |
| `session-redis` | Spring Session Redis restore | Docker + Redis | `tested in sample reactor` |
| `session-jdbc` | Spring Session JDBC restore | none | `tested in sample reactor` |
| `approval-workflow` | interrupts and resume | none | `tested in sample reactor` |
| `skill-activation` | skills and skill resources | none | `tested in sample reactor` |
| `streaming-steering` | streaming and steering | none | `tested in sample reactor` |
| `domain-separation` | composed backend workflow | none by default, Bedrock optional | `tested in sample reactor` |

## Concept-Only Sample Directories

| Directory | Role | Status |
| --- | --- | --- |

## Recommended Paths

### Core Runtime

1. `conversation-basics`
2. `built-in-tools`
3. `tool-delegation`
4. `tool-execution-context`

### Backend Integration

1. `secure-downstream-tools`
2. `stateful-backend-operations`
3. `session-jdbc` or `session-redis`
4. `domain-separation`

### Extensions And Control Flow

1. `approval-workflow`
2. `skill-activation`
3. `streaming-steering`
4. `domain-separation`

## Notes

- `domain-separation` is the composed reference sample. Read it after the narrower feature samples unless you specifically want the higher-level backend picture first.
- `domain-separation`, `session-jdbc`, `session-redis`, `approval-workflow`, `skill-activation`, `streaming-steering`, and `tool-execution-context` currently have sample-local tests in the sample reactor.
- `built-in-tools`, `secure-downstream-tools`, and `stateful-backend-operations` also now have sample-local tests in the sample reactor.
- `session-redis` and `session-jdbc` show the same restore boundary with different persistence backends. Choose the one that matches your deployment style.
- Bedrock-backed samples require AWS credentials and model access. Deterministic samples do not.
- `tool-delegation` remains `compile-checked in sample reactor` because its published value is live Bedrock-backed delegation plus structured output. It now also has an opt-in Bedrock smoke test for live evidence.
- `conversation-basics` remains `compile-checked in sample reactor` because its published value is a live Bedrock-backed multi-turn conversation and prompt-cache metrics path rather than a deterministic in-process model. It now also has an opt-in Bedrock smoke test for live evidence.
- before trusting `mvn -f samples/pom.xml ...` results after library changes, refresh the local `com.mahitotsu.arachne:arachne` snapshot with `mvn -pl arachne -am install -DskipTests`.
- `food-delivery-demo` is no longer part of the samples catalog; it now lives at the repository root as its own module tree.