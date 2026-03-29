# Arachne Sample Catalog

This directory contains runnable samples for the features that Arachne provides now.

The concept-only `marketplace-agent-platform` directory is design material for a future sample. It is not yet part of the runnable sample catalog and is not yet included in the Maven sample reactor.

Use this page to choose the right sample. Use each sample's own README for setup, run commands, and what to inspect in the code.

## Start Here

If you are new to Arachne, start in this order:

1. `conversation-basics`
2. `built-in-tools`
3. `tool-delegation`
4. `tool-execution-context`

After that, move to the narrower sample that matches your current integration problem.

## Before Running Samples

Most samples depend on the local snapshot of this repository, so install the root project first:

```bash
mvn install
```

Then run the sample from its own directory.

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

| Sample | Main topic | External dependency |
| --- | --- | --- |
| `conversation-basics` | in-memory multi-turn runtime | Bedrock |
| `built-in-tools` | built-in tools | none |
| `secure-downstream-tools` | secure downstream tool calls | none |
| `stateful-backend-operations` | idempotent backend mutations | none |
| `tool-delegation` | delegation and structured output | Bedrock |
| `tool-execution-context` | invocation vs execution context | none |
| `session-redis` | Spring Session Redis restore | Docker + Redis |
| `session-jdbc` | Spring Session JDBC restore | none |
| `approval-workflow` | interrupts and resume | none |
| `skill-activation` | skills and skill resources | none |
| `streaming-steering` | streaming and steering | none |
| `domain-separation` | composed backend workflow | none by default, Bedrock optional |

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
- `session-redis` and `session-jdbc` show the same restore boundary with different persistence backends. Choose the one that matches your deployment style.
- Bedrock-backed samples require AWS credentials and model access. Deterministic samples do not.
- `marketplace-agent-platform` is a design-only concept directory under `samples/`; it is not yet included in this runnable catalog.