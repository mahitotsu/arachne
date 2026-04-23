---
description: "Use when implementing or refactoring Java production code across Arachne modules, marketplace services, and samples. Reinforces bounded exploration, module ownership, and contract-first tracing."
applyTo: "{arachne,food-delivery-demo,samples}/**/src/main/**"
---

# Java Implementation Workflow

- Start from the repository-wide exploration discipline, then identify the owning Maven module, package, or service before widening.
- Use the nearest `pom.xml`, module-local `src/main`, and matching `src/test` tree as the first-pass scope.
- Expand to adjacent modules, docs, ADRs, or samples only when the current surface cannot answer a concrete contract, integration, or regression question.
- Treat `arachne/docs/project-status.md` as the shipped-scope map. Use `arachne/docs/README.md` and `arachne/docs/user-guide.md` as maps or targeted follow-up references, not as default front-to-back reads.
- Preserve published contracts and opt-in behavior by default. If a change crosses a module boundary, verify the owning tests and documentation in the same turn.
- Prefer deterministic local verification first. Keep provider-specific or external-service paths opt-in unless the task explicitly targets them.
- After modifying observable behavior, write tests for the new contract in the same turn: at least one test that names the new feature's specification, and run the owning module's existing tests to confirm no regressions.