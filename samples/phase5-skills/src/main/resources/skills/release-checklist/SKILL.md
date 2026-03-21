---
name: release-checklist
description: Use this skill when preparing a release.
allowed-tools:
  - git_status
  - git_log
compatibility: java-21
---
Run mvn test before merging.
Summarize the highest remaining risk before recommending release.