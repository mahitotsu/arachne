# 0010. Skills Injection And Discovery Boundary

## Status

Accepted

## Context

Phase 5 needed support for AgentSkills.io-compatible `SKILL.md` documents. Three questions had to be settled: whether `SKILL.md` parsing belongs in the core or Spring layer, how skills should be attached to an agent runtime, and how Spring Boot should discover packaged skills.

By the end of Phase 4, Arachne already had a runtime-local hook registry and plugin bundling. At the same time, it should not grow provider-specific request shaping or hidden middleware. Phase 5 required a `Skill` data model, `AgentSkillsPlugin`, Spring classpath scanning, `AgentFactory.Builder#skills(...)`, a dedicated activation tool, and context management for loaded skills.

AgentSkills.io also relies on progressive disclosure, which means catalog exposure and activation must be separate. Arachne needed to preserve provider independence while layering delayed loading on top of the Phase 4 plugin and hook boundary. It also needed minimal context management so a loaded skill could be reused within a conversation without injecting the same body twice into the same prompt.

## Decision

Arachne adopts the following boundary for Phase 5 skills.

- `Skill` and `SkillParser` live in `io.arachne.strands.skills` as core APIs. YAML frontmatter parsing and Markdown body extraction from `SKILL.md` remain reusable without Spring.
- `AgentSkillsPlugin` is implemented as a `Plugin` and injects a compact available-skill catalog plus loaded-skill instructions into the system prompt from `BeforeModelCallEvent`. It does not add hidden middleware or provider-specific branching.
- `AgentSkillsPlugin` exposes a dedicated `activate_skill` tool as a plugin tool so the model can lazy-load only the required skill body by exact skill name.
- Loaded skill names are stored in `AgentState` and reused within the conversation scope. Activation of an already loaded skill short-circuits, and turns that already contain the same body in the immediately preceding activation tool result avoid re-injection.
- `AgentFactory.Builder#skills(...)` is the runtime-local API for adding skills explicitly, and builder-supplied skills apply only to that runtime.
- Packaged skill discovery in Spring Boot is confined to `ClasspathSkillDiscoverer`, with `resources/skills/<skill-name>/SKILL.md` as the standard search location.
- Auto-configured discovered skills and builder-supplied skills can coexist in the same runtime, with builder-supplied skills winning on name conflicts.

## Consequences

- Skill parsing remains usable outside Spring, so CLI flows, tests, and future non-Spring integrations can all reuse the same `Skill` API.
- Skill activation and prompt shaping ride on the existing hook and plugin boundary, which keeps them aligned with the runtime-local registry established in Phase 4.
- Spring Boot users can place `SKILL.md` files under `src/main/resources/skills/` and have the skill catalog wired into the runtime automatically.
- Skill bodies are disclosed to the model only when needed, and loaded skills remain active for later turns in the same conversation.
- Because loaded-skill tracking is based on `AgentState`, it fits naturally with the existing session-persistence model.

## Alternatives Considered

### 1. Put `SKILL.md` parsing and classpath scanning together inside Spring auto-configuration

Rejected. It would remove the skill parsing API from the core and make reuse harder for non-Spring users and test fixtures.

### 2. Rewrite system prompt shaping directly inside the provider layer

Rejected. It would leak skill responsibilities into the model provider and break the hook/plugin boundary established in Phase 4.

### 3. Keep eager injection and skip delayed loading

Rejected. It would fail to satisfy the progressive-disclosure property that matters for AgentSkills.io compatibility and would not reduce the context cost of skill injection.