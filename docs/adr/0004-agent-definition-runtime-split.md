# 0004. Agent Definition And Runtime Split

## Status

Accepted

## Context

0001 では `Agent` を stateful な runtime object として扱い、shared singleton Spring bean を標準利用にしないと定めた。0003 では Spring integration の標準入口を `ArachneAutoConfiguration` と `AgentFactory` に置いた。ここで次に出てくる論点は、`DefaultAgent` が definition と execution state を同時に持つ現行形を維持するのか、それとも definition と runtime instance を明示的に別型へ分けるのかである。

分離型には魅力がある。definition を immutable な構成情報として共有し、runtime だけを短命 instance として生成すれば、lifecycle の説明はより明確になる。また、将来の interrupt/resume、execution backend、hook/plugin 合成でも、definition 側と invocation state 側を別々に扱える余地がある。

一方で、現時点のコードベースはまだその分離を必要とする段階にはない。Arachne の標準利用パターンはすでに `AgentFactory.Builder` に集約されており、model、tools、conversation manager、session manager、retry などの構成要素は factory 側で十分に組み立てられている。Phase 3.5 の主眼は、先回りした抽象追加ではなく、Spring 環境で誤用しにくい利用パターンを固めることである。

さらに、definition/runtime 分離を public API として導入すると、新たな型名、builder の責務分担、tool から nested invocation する場合の取得方法、session をどちらの責務に置くか、resume API をどこへぶら下げるかなど、連鎖的に決めるべき論点が増える。現段階でそれらを一気に固定すると、Phase 4 の hook / interrupt 設計をかえって縛る可能性がある。

## Decision

Arachne は現時点では agent definition と runtime instance を別の public abstraction として導入しない。当面は `AgentFactory` を definition 相当の組み立て入口として扱い、そこから生成される `Agent` を runtime object として扱う。

標準方針は次のとおりとする。

- `AgentFactory` とその builder defaults が definition 相当の役割を担う。
- `Agent` は引き続き runtime object であり、会話状態、session restore/save、invocation ごとの変化を抱える実体として扱う。
- lifecycle 上の安全性は、新しい definition 型を導入するのではなく、factory/provider ベースの標準利用パターンでまず確保する。
- definition/runtime を別 public type に分離する判断は保留とし、Phase 4 以降で interrupt、resume、execution backend、hook/plugin の要求が揃った時点で再評価する。
- 内部実装で definition 的な概念を補助的に整理することは妨げないが、現段階では新しい public abstraction や追加レイヤを標準化しない。

## Consequences

- Phase 3.5 の範囲では、既存 API を大きく増やさずに lifecycle 方針を前進させられる。
- 利用者に対しては「Spring では `AgentFactory` から runtime を作る」という説明で一貫でき、definition/runtime の二重概念を今すぐ覚えさせずに済む。
- 将来的に definition/runtime を分離する余地は残るが、それは hook、interrupt、resume などの要求を踏まえた設計課題として後ろ倒しになる。
- `DefaultAgent` が definition と state を同時に持つ現行構造は当面維持されるため、内部責務の整理や documentation で補う必要がある。
- 次の優先論点は、binding と validation の責務分離、および Spring 標準 bean 再利用範囲の明確化になる。

## Alternatives Considered

### 1. 直ちに `AgentDefinition` と `AgentRuntime` のような別 public type を導入する

今回は採用しない。長期的な候補ではあるが、Phase 3.5 の目的に対して変更範囲が広く、関連する API 決定を一度に増やしすぎる。

### 2. Spring 専用の definition 型だけを追加する

採用しない。core と Spring の概念差を広げ、non-Spring 利用との整合を崩しやすい。definition/runtime 分離は必要なら core 側の意味論として考えるべきである。

### 3. definition/runtime の整理自体を明文化せず、将来必要になったら都度考える

採用しない。0001 と 0003 の次に自然に発生する論点であり、「いまは分離しない」という判断自体を残しておかないと、後続フェーズで同じ議論を繰り返しやすい。

### 4. まず内部実装だけ分離し、外部には説明しない

採用しない。内部整理はありうるが、この論点の本質は public 利用パターンと用語にある。後続設計への影響が大きいため、判断を曖昧なまま hidden refactor に閉じ込めるべきではない。
