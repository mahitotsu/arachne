# Claude Code Workflow Maintenance

Use this guidance when editing the Claude Code workflow files themselves.

## Managed Files

- `CLAUDE.md` — root instructions
- `.claude/commands/` — 2 slash commands: `/ship-changes`, `/session-handoff`
- `.claude/repository-ops/repository-reading-guide.md` — area entry points and bounded read sets
- `memories/repo/status.md` — session-persistent work state
- `arachne/src/main/CLAUDE.md`, `arachne/src/test/CLAUDE.md`, `food-delivery-demo/customer-ui/CLAUDE.md` — scoped guidance

## Synchronization Rules

- When the workflow changes, sync `CLAUDE.md`, affected commands, `repository-reading-guide.md`, and `memories/repo/status.md` in the same work unit.
- When shipped capability boundaries, sample entry points, or verification commands change, update `repository-reading-guide.md` to reflect the new bounded read sets.
- Keep command descriptions accurate. A command that no longer matches its description misleads future work.

## Command Authoring Rules

- Plain markdown only. No frontmatter.
- Use `$ARGUMENTS` for optional user-supplied scope.
- All paths are relative to the repository root.
- Cross-command references use slash syntax: `/close-action`, `/ship-changes`, etc.
- Procedure steps should be short and action-oriented. Avoid restating context the AI already has from `CLAUDE.md`.
