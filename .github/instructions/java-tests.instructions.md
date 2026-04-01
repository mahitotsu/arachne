---
description: "Use when adding or updating Java tests across Arachne modules, marketplace services, and samples. Reinforces target-test-first reading, contract-focused assertions, and bounded cross-module expansion."
applyTo: "{arachne,marketplace-agent-platform,samples}/**/src/test/**"
---

# Java Test Workflow

- Start from the target test class or the nearest existing regression test before reading wider implementation or documentation surfaces.
- Confirm expected behavior from `docs/project-status.md`, the owning module contract, and only the minimum implementation needed to explain the test.
- Add or update deterministic tests in the same turn when behavior changes. Avoid timing-sensitive or provider-random assertions unless the task explicitly requires them.
- Expand to samples, `docs/user-guide.md`, or adjacent modules only when the expected contract remains ambiguous or a published integration boundary is under test.
- Keep cross-module assertions focused on published boundaries. Prefer module-local tests for module-owned behavior.