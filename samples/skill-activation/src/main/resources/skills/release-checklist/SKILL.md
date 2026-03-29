---
name: release-checklist
description: Use this skill when preparing a release.
compatibility: java-25
metadata:
  allowed-tools:
    - git_status
    - git_log
---
Run mvn test before merging.
Summarize the highest remaining risk before recommending release.