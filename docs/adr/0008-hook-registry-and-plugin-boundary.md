# 0008. Hook Registry And Plugin Boundary

## Status

Accepted

## Context

Phase 4 では hook、plugin、interrupt を導入し、agent runtime の lifecycle に対して AOP 的に介入できるようにする必要がある。Phase 3 までの Arachne には `HookRegistry` の callsite だけが存在し、実際の dispatch、Spring bean discovery、plugin bundling は未実装だった。

この時点で決めるべき論点は 3 つある。1 つ目は hook callback の public API をどの粒度で表すか。2 つ目は `EventLoop` や `ToolExecutor` に middleware 的な分岐を広げずに、どこで hook を dispatch するか。3 つ目は Spring integration で hook bean をどう runtime に載せるかである。

また、Phase 5 の skills は hook の上に載る予定であり、`BeforeInvocationEvent` や `BeforeModelCallEvent` で system prompt や会話状態へ介入できる前提が必要になる。一方で、Spring `ApplicationEvent` bridge は観測専用に保つ必要があり、制御フロー用 hook と混同すべきではない。

## Decision

Arachne は Phase 4 の hook 基盤として、typed mutable event を dispatch する runtime-local `HookRegistry` と、callback を登録する `HookProvider` / `HookRegistrar` を採用する。

標準方針は次のとおりとする。

- hook の public API は event type ごとに明示する。対象は `BeforeInvocationEvent`、`AfterInvocationEvent`、`BeforeModelCallEvent`、`AfterModelCallEvent`、`BeforeToolCallEvent`、`AfterToolCallEvent`、`MessageAddedEvent` である。
- lifecycle callsite は `DefaultAgent`、`EventLoop`、`ToolExecutor` に留め、hook の探索と callback dispatch は `DispatchingHookRegistry` に集約する。
- event は mutable にし、Phase 4 で必要な範囲の制御フロー介入を event object 経由で行う。具体的には prompt、system prompt、model response、tool input、tool result を hook から調整できる。
- `Plugin` は `HookProvider` を継承し、同時に `Tool` 群を返せる bundling 単位とする。plugin は runtime ごとに builder から追加される。
- Spring integration では `@ArachneHook` が付いた `HookProvider` bean を auto-discovery し、`AgentFactory.Builder#build()` ごとに新しい runtime-local registry へ登録する。
- Spring 側の hook bean discovery は制御フロー hook の入口であり、観測専用の `ApplicationEvent` bridge とは別責務として扱う。

## Consequences

- hook logic は provider 非依存の core API として実装でき、Bedrock 固有型を event API に露出しない。
- `DefaultAgent` と `EventLoop` は hook 分岐だらけにならず、Phase 4 の責務を registry 側へ閉じ込められる。
- plugin は `Tool` と hook の同時配布単位になり、後続の skills や human-in-the-loop 機能を組み合わせやすくなる。
- Spring 利用者は `AgentFactory` を標準入口のまま維持しつつ、hook bean を application context に置くだけで runtime へ介入できる。
- interrupt と Spring `ApplicationEvent` bridge はこの境界の上に追加できるが、今回はまだ public API として完結していないため別タスクで実装する必要がある。

## Alternatives Considered

### 1. `HookRegistry` を no-op callback 群のまま維持し、各 callsite で provider を直接解決する

採用しない。`DefaultAgent`、`EventLoop`、`ToolExecutor` の各所に hook 解決ロジックが散らばり、Phase 4 の責務境界が崩れる。

### 2. generic middleware chain を導入して model/tool/invocation をすべて同一 abstraction に寄せる

採用しない。Phase 4 の roadmap 要件に比べて抽象度が高すぎ、現在必要な event 型と Spring bridge の責務が見えにくくなる。

### 3. Spring hook bean を singleton registry に直接登録して全 runtime で共有する

採用しない。0001 と 0003 の runtime-local lifecycle 方針に反し、stateful event mutation や後続 interrupt state を shared singleton に寄せてしまう。

### 4. plugin を Spring bean discovery のみで扱い、builder からの明示追加を持たない

採用しない。non-Spring 利用や runtime ごとの組み合わせを制限してしまい、plugin を core API として再利用しにくい。