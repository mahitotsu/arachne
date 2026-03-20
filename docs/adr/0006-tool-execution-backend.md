# 0006. Tool Execution Backend

## Status

Accepted

## Context

Phase 2 で Arachne は tool 呼び出しを並列または直列で処理できるようになり、`ToolExecutionMode` で policy を切り替えられるようになった。しかし現行実装では、`ToolExecutor` の並列実行 backend は `Executors.newVirtualThreadPerTaskExecutor()` に固定されており、`AgentFactory.Builder` も `new ToolExecutor(toolExecutionMode)` を直接生成して `EventLoop` へ渡している。

この実装はシンプルで、Java 21 の virtual thread を使う既定値としては妥当である。一方で、Phase 3.5 の観点では 2 つの問題がある。1 つ目は、実行ポリシーである `PARALLEL` / `SEQUENTIAL` と、実際にどの execution backend を使うかが同一クラスに埋め込まれていること。2 つ目は、Spring Boot integration を提供しながら、tool 実行の scheduler / executor を Spring 標準インフラと整合させる余地がないことである。

今後の Phase 4 では hook、interrupt、human-in-the-loop 制御が入るため、tool 呼び出しの実行基盤は観測・制御・差し替えの対象になりやすい。利用者によっては virtual thread を維持したい場合もあれば、アプリケーション全体で共有する `TaskExecutor` や制限付き thread pool を使いたい場合もある。Phase 3.5 では、現時点で新しい複雑な async API を導入するのではなく、少なくとも execution backend を固定実装として扱わない方針を明文化する必要がある。

## Decision

Arachne は `ToolExecutionMode` を「直列か並列か」の実行ポリシーとして維持しつつ、並列実行の backend は固定実装にせず、Spring integration では `Executor` または `TaskExecutor` から差し替え可能なものとして扱う。

標準方針は次のとおりとする。

- `ToolExecutionMode` は、execution policy を表す API として維持する。
- `ToolExecutor` の責務は policy に従って tool call を調停することであり、backend 実装を常に自前で固定生成することではない。
- Spring integration では、並列 tool 実行に使う backend を `Executor` または `TaskExecutor` から供給・差し替えできるようにする。
- 既定値として virtual thread ベースの backend を持つこと自体は許容するが、それは標準の唯一実装ではなく default fallback として位置づける。
- Event loop は引き続き同期 API を維持し、Phase 3.5 では reactive 化や非同期 public API までは導入しない。

## Consequences

- Spring Boot application は、アプリケーション全体の実行基盤と tool 実行基盤の整合を取りやすくなる。
- `PARALLEL` と `SEQUENTIAL` の意味を保ったまま、実運用で必要な concurrency policy を executor 側で調整できる。
- `ToolExecutor` と `AgentFactory` の実装は、backend 注入を受けられる方向へ段階的に整理する必要がある。
- virtual thread を既定にした現在の軽量さは維持しつつ、hook / interrupt / observability を考慮した拡張余地を確保できる。
- event loop 自体は同期のままなので、Phase 6 で予定している reactive / streaming API とは切り分けて進められる。

## Alternatives Considered

### 1. `ToolExecutor` の内部で virtual thread backend を固定し続ける

採用しない。小規模用途には十分だが、Spring integration の価値を活かせず、実行基盤をアプリケーション側で制御したい要求に応えにくい。

### 2. `ToolExecutionMode` 自体を廃止し、すべて executor 設定に委ねる

採用しない。利用者が理解する public API としては、直列か並列かという policy は明示されていた方が分かりやすい。backend 選択と policy を完全に同一視すべきではない。

### 3. 直ちに非同期・reactive な tool execution API へ置き換える

採用しない。Phase 3.5 の目的は実行基盤の差し替え性を上げることであり、event loop 全体の非同期化ではない。reactive public API は roadmap 上も後続フェーズの範囲である。

### 4. Spring 専用実装だけを追加し、core 側の既定 backend 方針は定めない

採用しない。Spring integration のみで特別扱いすると、core と Spring の役割分担が曖昧になる。まず「backend は fixed implementation ではなく pluggable」という原則を共有し、その上で Spring が標準差し替え口になる形が望ましい。
