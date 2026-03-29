# Arachne Documentation Guide

This directory contains the documents that explain what Arachne provides now, how to use it, and where to find runnable examples.

## Start Here

If you are new to Arachne, read these in order:

1. `user-guide.md`
2. `project-status.md`
3. `../samples/README.md`

That path gives you:

- the current runtime and API surface
- the main configuration and usage model
- the current constraints that affect real integration work
- the smallest runnable sample for each capability area

## Choose By Need

Open the document that matches the question you have now.

- `user-guide.md`: setup, configuration, agent creation, tools, sessions, skills, streaming, steering, and sample links
- `project-status.md`: concise snapshot of the features, samples, and constraints available on the current branch
- `tool-catalog.md`: current built-in tools, tool authoring surfaces, and tool-related cautions
- `repository-facts.md`: repository layout, verification commands, sample/module map, and code-location guide
- `closeout-and-readiness.md`: local maintainer workflow for task closeout and later readiness recovery
- `adr/`: architecture decisions that explain why the current public model looks the way it does

## Recommended Reading Paths

### I want to build with Arachne now

1. `user-guide.md`
2. `project-status.md`
3. `../samples/conversation-basics/README.md`

### I need tools

1. `tool-catalog.md`
2. `../samples/built-in-tools/README.md`
3. `../samples/tool-delegation/README.md`
4. `../samples/tool-execution-context/README.md`

### I need stateful backend behavior

1. `user-guide.md`
2. `../samples/stateful-backend-operations/README.md`
3. `../samples/session-jdbc/README.md` or `../samples/session-redis/README.md`

### I need architecture background

1. `project-status.md`
2. `adr/README.md`

## Practical Notes

- `user-guide.md` is the main usage document. Start there unless you already know the surface and only need a capability snapshot.
- `project-status.md` is the fastest way to confirm whether a feature is available on the current branch.
- `tool-catalog.md` describes the current tool surface. It is not a roadmap.
- `repository-facts.md` is a reference document for navigating the repository, not a getting-started guide.

## Samples

Runnable sample selection lives in [samples/README.md](../samples/README.md).