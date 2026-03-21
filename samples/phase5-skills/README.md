# Phase 5 Skills Sample

This sample is a runnable, Bedrock-free demo for the Phase 5 skills feature set.

It demonstrates these current-main concepts together:

- Spring classpath discovery from `src/main/resources/skills/<skill-name>/SKILL.md`
- compact available-skill catalog injection before activation
- delayed loading through the dedicated `activate_skill` tool
- loaded-skill persistence through `AgentState` and `session.id`
- follow-up turns on a fresh runtime that reuse the loaded skill without reloading it

The sample uses a deterministic in-process `Model` bean, so you can verify the delayed-loading flow without AWS credentials.

## Prerequisites

- Java 21
- Maven

The sample depends on the local snapshot of this repository, so install the root project first:

```bash
mvn install
```

## Run The Demo

```bash
cd samples/phase5-skills
mvn spring-boot:run
```

Expected output shape:

```text
Arachne Phase 5 skills sample
tools> activate_skill
first.request> Prepare today's release.
first.reply> Loaded release-checklist. Run mvn test before merging and summarize the remaining risk.
state.loadedSkills> [release-checklist]
second.request> What should I do next?
second.reply> Reusing loaded release-checklist. Run mvn test before merging and summarize the remaining risk.
restored.loadedSkills> [release-checklist]
prompt.catalogPresent> true
prompt.activeSkillPresentAfterRestore> true
```

## What To Look For

The sample is centered on three pieces:

- `DemoSkillsModel`: a deterministic model that first requests `activate_skill`, then answers from the loaded skill
- `SampleSkillsRunner`: builds one runtime, runs the first request, then builds a fresh runtime with the same session id to prove restore
- `skills/release-checklist/SKILL.md`: a packaged AgentSkills.io-style skill discovered automatically from the classpath

This intentionally shows the current Phase 5 boundaries.

- the model sees only a compact catalog before activation
- the full skill body arrives through the dedicated activation tool
- the loaded skill stays active across later turns and across a restored runtime
- redundant loading is unnecessary once the skill name is present in agent state

## Configuration

The sample ships with this default configuration in `src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: arachne-phase5-skills
  main:
    banner-mode: off
    web-application-type: none

arachne:
  strands:
    agent:
      system-prompt: "You are a release assistant. Use skills when they are relevant."
      session:
        id: phase5-skills-demo
```

No Bedrock model configuration is required because the sample provides its own deterministic `Model` bean.