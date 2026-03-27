# Arachne Documentation Catalog

This directory holds the repository-level documents that define what Arachne ships, how to use it, and how to reason about the current architecture.

Use this page when you are not sure which document to open first.

## Document Roles

- `project-status.md`: the normative shipped-scope snapshot, current constraints, and deliberately deferred boundary
- `user-guide.md`: user-facing setup, configuration, API usage, lifecycle guidance, and links to runnable samples
- `tool-catalog.md`: the current tool authoring surface plus the proposed direction for future Arachne-maintained tool families
- `repository-facts.md`: descriptive repository snapshot with structure, counts, hotspots, and quality-watch areas
- `adr/`: accepted architectural decisions and deferred design boundaries

## Suggested Reading Order

### 1. Product Boundary

Read these first when you need to know what is actually shipped today.

1. `project-status.md`
2. `adr/0012-post-mvp-product-boundary.md`

### 2. Library Usage

Read these when you are integrating Arachne into an application.

1. `user-guide.md`
2. `../samples/README.md`

### 3. Tool Design

Read these when you are implementing tools or evaluating tool-related direction.

1. `tool-catalog.md`
2. `adr/0007-phase2-tool-contracts.md`
3. `adr/0014-tool-invocation-context-contract.md`
4. `adr/0015-execution-context-propagation-boundary.md`
5. `../samples/secure-downstream-tools/README.md`
6. `../samples/stateful-backend-operations/README.md`

### 4. Repository Maintenance

Read these when you are auditing or evolving the repository itself.

1. `repository-facts.md`
2. `adr/README.md`

## What Not To Use Each Document For

- do not use `project-status.md` as a how-to guide
- do not use `user-guide.md` as the source of truth for deferred features
- do not use `tool-catalog.md` as an implementation task list
- do not use `repository-facts.md` as a normative contract

## Sample Catalog

Runnable sample selection and learning tracks live under [samples/README.md](../samples/README.md).