# Skill Activation Sample

This sample is a runnable, Bedrock-free demo for delayed skill activation.

It demonstrates these current-main concepts together:

- Spring classpath discovery from `src/main/resources/skills/<skill-name>/SKILL.md`
- packaged `scripts/`, `references/`, and `assets/` discovery alongside the skill document
- compact available-skill catalog injection before activation
- delayed loading through the dedicated `activate_skill` tool
- on-demand resource reading through `read_skill_resource`
- loaded-skill persistence through `AgentState` and `session.id`
- follow-up turns on a fresh runtime that reuse the loaded skill without reloading it

The sample uses a deterministic in-process `Model` bean, so you can verify the delayed-loading flow without AWS credentials.

## Prerequisites

- Java 25
- Maven

The sample depends on the local `io.arachne:arachne` snapshot, so install the library module first:

```bash
mvn -pl arachne -am install
```

## Run The Demo

```bash
cd samples/skill-activation
mvn spring-boot:run
```

Expected output shape:

```text
Arachne skill activation sample
tools> activate_skill, read_skill_resource
first.request> Prepare today's release.
first.reply> Loaded release-checklist. Run mvn test before merging and summarize the remaining risk. Reference says: # Release Template - Verify the diff. - Run the test suite. - Note the highest remaining risk.
state.loadedSkills> [release-checklist]
first.resourceReads> [skill_activation, skill_resource]
first.referencePath> references/release-template.md
second.request> What should I do next?
second.reply> Reusing loaded release-checklist. Run mvn test before merging and summarize the remaining risk. Reference says: # Release Template - Verify the diff. - Run the test suite. - Note the highest remaining risk.
restored.loadedSkills> [release-checklist]
prompt.catalogPresent> true
prompt.activeSkillPresentAfterRestore> true
prompt.resourceListPresentAfterRestore> true
```

## What To Look For

The sample is centered on three pieces:

- `DemoSkillsModel`: a deterministic model that first requests `activate_skill`, then `read_skill_resource`, then answers from the loaded skill and reference content
- `SkillActivationRunner`: builds one runtime, runs the first request, then builds a fresh runtime with the same session id to prove restore
- `skills/release-checklist/SKILL.md`: a packaged AgentSkills.io-style skill discovered automatically from the classpath
- `skills/release-checklist/{scripts,references,assets}/`: packaged resource folders whose relative paths are surfaced after activation

This intentionally shows the current skills boundary.

- the model sees only a compact catalog before activation
- the full skill body and packaged resource paths arrive through the dedicated activation tool
- listed resources can then be read on demand through `read_skill_resource`
- the loaded skill stays active across later turns and across a restored runtime
- redundant loading is unnecessary once the skill name is present in agent state

## Configuration

The sample ships with this default configuration in `src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: arachne-skill-activation
  main:
    banner-mode: off
    web-application-type: none

arachne:
  strands:
    agent:
      system-prompt: "You are a release assistant. Use skills when they are relevant."
      session:
        id: skill-activation-demo
```

No Bedrock model configuration is required because the sample provides its own deterministic `Model` bean.