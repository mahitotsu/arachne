# 0010. Skills Injection And Discovery Boundary

## Status

Accepted

## Context

Phase 5 では AgentSkills.io 互換の `SKILL.md` を扱えるようにする必要があった。今回決めるべき論点は 3 つある。1 つ目は `SKILL.md` の parsing を core と Spring のどちらに置くか。2 つ目は skill を agent runtime にどう載せるか。3 つ目は Spring Boot で packaged skill をどう discovery するかである。

Phase 4 までで、Arachne は runtime-local hook registry と plugin bundling を持っている。一方で、model provider 固有の request shaping や hidden middleware を増やすべきではない。Phase 5 の roadmap では `Skill` データモデル、`AgentSkillsPlugin`、Spring classpath scan、`AgentFactory.Builder#skills(...)`、dedicated activation tool、loaded skill の context management が要求されている。

また AgentSkills.io では progressive disclosure が本質であり、catalog と activation を分ける必要がある。Arachne では provider 非依存を維持しつつ、Phase 4 で確定した plugin/hook 境界の上に delayed loading を載せる必要があった。さらに、一度ロードした skill を会話スコープで再利用しつつ、同じ prompt に同じ本文を重複注入しない最小の context management も必要になった。

## Decision

Arachne は Phase 5 の skills について、次の境界を採用する。

- `Skill` と `SkillParser` は core API として `io.arachne.strands.skills` に置く。`SKILL.md` の YAML frontmatter 解析と markdown body 抽出は Spring 依存なしで再利用できる形にする。
- `AgentSkillsPlugin` は `Plugin` として実装し、`BeforeModelCallEvent` で compact な available skill catalog と loaded skill instructions を system prompt に注入する。専用の hidden middleware や provider 固有分岐は追加しない。
- `AgentSkillsPlugin` は plugin tool として dedicated `activate_skill` tool を提供し、model は exact skill name を使って必要な skill 本文だけを遅延ロードする。
- loaded skill 名は `AgentState` に保存し、会話スコープで再利用する。already-loaded な skill の activation は short-circuit し、直前の activation tool result に同じ本文がある turn では再注入を避ける。
- `AgentFactory.Builder#skills(...)` は runtime-local な追加 skill API とし、builder で渡した skill はその runtime にだけ適用する。
- Spring Boot の packaged skill discovery は `ClasspathSkillDiscoverer` に閉じ込め、`resources/skills/<skill-name>/SKILL.md` を標準探索場所とする。
- auto-configured discovered skill と builder で追加した skill は同じ runtime で併用可能にし、同名 skill は builder 側を優先する。

## Consequences

- skill parsing は Spring の外でも使えるため、CLI、tests、将来の non-Spring integration でも同じ `Skill` API を再利用できる。
- skill activation と prompt shaping は既存の hook/plugin 境界に乗るので、Phase 4 で確定した runtime-local registry と整合する。
- Spring Boot 利用者は `src/main/resources/skills/` に `SKILL.md` を置くだけで skill catalog を runtime に載せられる。
- skill body は必要になったときだけ model に開示され、loaded skill は会話中の後続 turn でも有効なまま再利用される。
- loaded skill tracking が `AgentState` ベースなので、既存の session persistence とも自然に整合する。

## Alternatives Considered

### 1. `SKILL.md` parsing と classpath scan を Spring auto-configuration の中にまとめる

採用しない。core から skill parsing API が消え、Spring を使わない利用者や test fixture で再利用しにくくなる。

### 2. provider 層で system prompt shaping を直接書き換える

採用しない。skills の責務が model provider に漏れ、Phase 4 で定めた hook/plugin 境界を壊す。

### 3. eager injection を維持したまま delayed loading を見送る

採用しない。AgentSkills.io 互換の本質である progressive disclosure を満たせず、skill 導入の context cost も下がらないため。