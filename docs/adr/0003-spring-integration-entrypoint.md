# 0003. Spring Integration Entrypoint

## Status

Accepted (retrospective)

## Context

Arachne は Spring Boot integration を主要な提供価値の 1 つとしており、MVP の時点から「Spring アプリに `@Bean` でエージェントを定義し、文字列を渡すだけで tool-use loop が動く」ことを目標にしてきた。そのため、利用者が最低限の配線で model、tool discovery、validation、session、retry を利用できる標準入口が必要だった。

現行実装では `ArachneAutoConfiguration` が `Model`、`Validator`、annotation tool scanner、discovered tools、`SessionManager`、retry strategy、`AgentFactory` を組み立てる。利用者はこの `AgentFactory` を通じて named agent defaults や builder overrides を使いながら runtime instance を構築する。

Phase 3 までの実装で、設定の集約点は `ArachneProperties`、runtime の組み立て点は `AgentFactory.Builder` という形に収束している。Phase 3.5 で 0001 が `Agent` shared singleton を標準利用にしないと定めたため、Spring integration では「何を bean として共有するか」をさらに明確にする必要がある。

また、Arachne には Spring を使わない direct constructor も残っているが、それらは後方互換や限定的利用のための低レベル API であり、Spring 環境における標準 wiring を代表させるには不十分である。

## Decision

Arachne は Spring Boot における標準統合入口として `ArachneAutoConfiguration` と `AgentFactory` を採用し、この 2 つを中心に model/tool/session/retry の wiring を構成する。

標準方針は次のとおりとする。

- Spring 利用時の第一選択は starter-style な auto-configuration であり、利用者は必要な bean を override することで既定動作を差し替える。
- `AgentFactory` は Arachne runtime を構築する標準 API であり、named agent defaults、tool selection、conversation manager、session、retry を 1 か所で解決する。
- shared bean として長寿命に扱う対象は `AgentFactory`、`Model`、`Validator`、discovered tools、`SessionManager` などの definition / infrastructure 側であり、`Agent` runtime 自体ではない。
- Spring integration のドキュメント、sample、test は、原則として `AgentFactory` または provider 経由の利用を基準に説明する。
- `DefaultAgent` や `EventLoop` の direct constructor は維持してよいが、Spring 標準利用パターンの代表としては扱わない。

## Consequences

- Spring Boot application では、Arachne の標準配線を `ArachneAutoConfiguration` と `AgentFactory` の 2 段構成として理解できる。
- named agent settings、tool discovery、session backend、retry policy などの拡張点が `AgentFactory` 周辺に集約され、利用者が低レベル wiring を繰り返さずに済む。
- 0001 の lifecycle 方針と整合し、`Agent` runtime の生成を factory/provider に寄せる方向が明確になる。
- 将来 `Executor`、`ObjectMapper`、`ConversionService`、hook registry などの Spring 標準 bean 再利用範囲を整理する際も、まず auto-configuration と factory の責務境界を基準に検討できる。
- direct constructor を使う non-Spring 利用は引き続き可能だが、Spring 利用時のサポート対象や sample は `AgentFactory` 中心に寄るため、低レベル API の露出は相対的に下がる。

## Alternatives Considered

### 1. `Agent` bean を標準統合入口とみなす

採用しない。0001 で `Agent` は stateful runtime であり shared singleton を標準化しないと決めたため、統合入口としては不適切である。

### 2. 利用者に `DefaultAgent` / `EventLoop` / `ToolExecutor` の手動配線を求める

採用しない。Spring Boot integration の価値を大きく下げ、named defaults や discovered tools のような Arachne 固有の設定 UX も分散してしまう。

### 3. Spring Boot starter は提供するが `AgentFactory` を持たず、設定 bean を個別組み合わせさせる

採用しない。拡張点は増えるが、標準利用パターンが散らばり、どこで agent runtime を完成させるべきかが不明瞭になる。

### 4. Spring integration を薄い convenience layer にとどめ、core API をそのまま使わせる

採用しない。Arachne の roadmap は Spring Boot integration を主要機能として扱っており、tool discovery、named agent、session backend の配線は convenience の範囲を超えて標準 UX の一部である。
