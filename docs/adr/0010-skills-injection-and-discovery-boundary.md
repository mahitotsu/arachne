# 0010. Skills Injection And Discovery Boundary

## Status

Accepted as interim foundation

## Context

Phase 5 では AgentSkills.io 互換の `SKILL.md` を扱えるようにする必要があった。今回決めるべき論点は 3 つある。1 つ目は `SKILL.md` の parsing を core と Spring のどちらに置くか。2 つ目は skill を agent runtime にどう載せるか。3 つ目は Spring Boot で packaged skill をどう discovery するかである。

Phase 4 までで、Arachne は runtime-local hook registry と plugin bundling を持っている。一方で、model provider 固有の request shaping や hidden middleware を増やすべきではない。Phase 5 の roadmap では `Skill` データモデル、`AgentSkillsPlugin`、Spring classpath scan、`AgentFactory.Builder#skills(...)` が要求されているが、dedicated activation tool や remote registry はまだ要求されていない。

また AgentSkills.io では progressive disclosure が本質であり、catalog と activation を分ける必要がある。しかし現在の Java 実装には file-read activation tool や dedicated activation tool がまだ存在しない。そのため current branch で提供する public API は、後続フェーズの activation 追加を妨げずに、まず provider 非依存で skill instructions を runtime に載せる foundation を先に固める形になっている。

## Decision

Arachne は Phase 5 の skills について、次の境界を採用する。

- `Skill` と `SkillParser` は core API として `io.arachne.strands.skills` に置く。`SKILL.md` の YAML frontmatter 解析と markdown body 抽出は Spring 依存なしで再利用できる形にする。
- `AgentSkillsPlugin` は `Plugin` として実装し、`BeforeModelCallEvent` で system prompt に skill instructions を注入する。専用の hidden middleware や provider 固有分岐は追加しない。
- `AgentFactory.Builder#skills(...)` は runtime-local な追加 skill API とし、builder で渡した skill はその runtime にだけ適用する。
- Spring Boot の packaged skill discovery は `ClasspathSkillDiscoverer` に閉じ込め、`resources/skills/<skill-name>/SKILL.md` を標準探索場所とする。
- auto-configured discovered skill と builder で追加した skill は同じ runtime で併用可能にし、同名 skill は builder 側を優先する。
- current branch では progressive disclosure 用の activation tool はまだ導入せず、runtime に設定された skill instructions を eager に system prompt へ注入する interim 実装を採る。

## Consequences

- skill parsing は Spring の外でも使えるため、CLI、tests、将来の non-Spring integration でも同じ `Skill` API を再利用できる。
- skill injection は既存の hook/plugin 境界に乗るので、Phase 4 で確定した runtime-local registry と整合する。
- Spring Boot 利用者は `src/main/resources/skills/` に `SKILL.md` を置くだけで skill を runtime に載せられる。
- current branch の skill behavior は eager injection であり、AgentSkills.io の progressive disclosure 全体はまだ完了していない。そのためこの ADR は Phase 5 foundation の整理であり、phase closeout の根拠にはならない。

## Alternatives Considered

### 1. `SKILL.md` parsing と classpath scan を Spring auto-configuration の中にまとめる

採用しない。core から skill parsing API が消え、Spring を使わない利用者や test fixture で再利用しにくくなる。

### 2. provider 層で system prompt shaping を直接書き換える

採用しない。skills の責務が model provider に漏れ、Phase 4 で定めた hook/plugin 境界を壊す。

### 3. Phase 5 で dedicated skill activation tool まで同時に入れる

採用しない。progressive disclosure には有効だが、ROADMAP の current task を越えて tool contract と context management policy を先回りで固定してしまう。