# Closeout And Readiness

This document explains how to use closeout and readiness in everyday local development for Arachne.

The goal is simple: do not depend on perfect end-of-task discipline, but make it cheap to recover repository health before the next meaningful change.

## The Short Version

- use `Closeout` when you have just finished a bounded task and want to finish it cleanly
- use `Readiness Audit` when you want to check whether one area is easy to resume and what should be repaired
- use `Repository Readiness Audit` when the repository feels scattered, a new theme is about to begin, or you want a wider maintenance sweep

## What Each Workflow Does

### Closeout

Use [.github/prompts/closeout.prompt.md](../.github/prompts/closeout.prompt.md) after a bounded task.

It is the end-of-task protocol.

It should:

- restate the exact finished boundary
- confirm fresh enough evidence
- close the truth surfaces that should now reflect the finished state
- force every leftover item to land somewhere explicit
- leave short re-entry instructions for the next worker

Use it when the work is still fresh and you can still fix obvious drift in the same turn.

### Readiness Audit

Use [.github/prompts/readiness-audit.prompt.md](../.github/prompts/readiness-audit.prompt.md) for one capability area or repository surface.

It is the recovery and repair workflow.

It should:

- judge whether the next worker can restart from a small, trustworthy surface
- identify what makes the area hard to re-enter
- produce concrete fix candidates
- order those candidates by repair priority

Use it when closeout was skipped, when an area feels scattered, or before starting new work in that area.

### Repository Readiness Audit

Use [.github/prompts/repository-readiness-audit.prompt.md](../.github/prompts/repository-readiness-audit.prompt.md) for a broader maintenance sweep.

It partitions the repository into bounded readiness areas, then reports which areas are ready and which need repair.

Use it:

- before a new implementation theme
- before a release or wider cleanup
- when repository context feels like it is spreading across too many surfaces

## Recommended Local Workflow

For normal local development:

1. Finish a bounded task.
2. If the work is still fresh, run `Closeout`.
3. If you skipped closeout or later suspect drift, run `Readiness Audit` for that area.
4. When the repository as a whole feels scattered, run `Repository Readiness Audit`.

This repository does not currently force these workflows with hooks or CI. They are maintainers' operating procedures.

## Sample Reactor Re-Entry Rule

When you are about to use `samples/pom.xml` for sample-side readiness, consistency, or quality evidence, refresh the library snapshot in the local Maven repository first.

Use this sequence:

```bash
mvn -pl arachne -am install -DskipTests
mvn -f samples/pom.xml test
```

The reason is mechanical: the sample reactor resolves `io.arachne:arachne` from the local Maven repository, not from the sibling `arachne/` module's in-memory reactor outputs. Without the install step, sample checks can accidentally evaluate an older local snapshot and produce false drift.

## Bedrock-Specific Re-Entry Rule

When a bounded task changes Bedrock-specific runtime behavior, Bedrock-facing sample wiring, or Bedrock-only documentation claims, treat live Bedrock smoke verification as part of closeout evidence.

Rerun these opt-in checks when credentials and model access are available:

```bash
mvn -Dtest=BedrockModelIntegrationTest -Darachne.integration.bedrock=true test
mvn -f samples/pom.xml -pl domain-separation -Dtest=DomainSeparationBedrockIntegrationTest -Darachne.integration.bedrock=true test
```

If you do not rerun them, leave that gap as explicit residual work instead of implying that live provider evidence is fresh.

## What Counts As Good Enough

An area is in good working shape when all of these are true:

- the next worker can identify the right starting docs, code, and tests quickly
- the shipped boundary and current constraints are easy to find
- code, tests, docs, samples, ADRs, and instructions do not contradict each other in meaningful ways
- leftover work is explicitly classified instead of implied
- readiness can name the highest-value repairs without broad rediscovery

## If You Only Do One Thing

If you do not want to run everything, prefer this rule:

- run `Closeout` when finishing an important bounded task
- run `Readiness Audit` before starting work in an area that feels fuzzy or scattered