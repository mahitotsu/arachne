---
name: repository-ops
description: 'Use when you need a single entry point for repository restart, metrics checks, snapshot refresh, locality checks, ship workflow, or session handoff.'
argument-hint: 'Optional intent. Example: repo restart / measure repo health / refresh snapshot / prepare handoff'
user-invocable: true
---

# Repository Operations

Use this skill as the umbrella entry point for repository operation workflows in Arachne.

This skill does not replace the specialized prompts and skills. Its job is to re-establish current state and route to the right workflow.

## Read First

- [workspace instructions](../../copilot-instructions.md)
- [repository snapshot](../../repository-ops/repository-snapshot.md)
- `/memories/repo/status.md`
- [repository reading guide](../../repository-ops/repository-reading-guide.md) only when more detailed entry points are needed

## Use This Skill When

- you want to restart context at the beginning of a session
- you are unsure which repository-side prompt or skill to use
- you changed workflow, prompt, skill, or repo-ops files
- you need to decide between snapshot refresh, repository metrics, locality check, readiness check, ship workflow, or session handoff

## Routing Guide

1. refresh the current repository cross-section: `/repo-snapshot`
2. inspect reading-structure drift or threshold health: `/repository-structure-health`
3. collect and evaluate quantitative repository health: `/repository-metrics`
4. decide whether the current work is still localized: `/locality-check`
5. judge commit or session-close readiness without changing files: `/close-action`
6. execute commit or push workflow: `/ship-changes`
7. generate a paste-ready next-session prompt: `/session-handoff`

## Procedure

1. Read [repository snapshot](../../repository-ops/repository-snapshot.md) and `/memories/repo/status.md` to re-establish current state.
2. Classify the current intent as one of: `restart | refresh | metrics | structure-check | locality-check | ship | handoff`.
3. Route to the matching specialized prompt or skill.
4. If you changed repository workflow files, check whether [arachne/docs/project-status.md](../../../arachne/docs/project-status.md), [arachne/docs/repository-facts.md](../../../arachne/docs/repository-facts.md), and `/memories/repo/status.md` also need synchronization.

## Response Rules

- Do not create a competing source of truth. `.github/repository-ops/` remains the workflow source of truth.
- Do not duplicate the specialized behavior of existing prompts and skills inside this router.
- If the user clearly needs a specialized workflow already, route there directly instead of expanding this skill.