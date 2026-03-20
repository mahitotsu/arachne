# 0009. Interrupt Resume And Observation Bridge

## Status

Accepted

## Context

Phase 4 の残作業では 2 つの境界を確定する必要があった。1 つ目は interrupt をどこで止め、resume をどの API で再開するかである。2 つ目は Spring `ApplicationEvent` ブリッジをどう実装すれば、hook の観測はできても control flow を書き換える入口にはならないかである。

tool 実行前 interrupt については、Arachne の current runtime が同期 loop であり、Phase 4 では tool・model・session の責務を混同しないことが求められている。そのため「tool 実行を途中 suspend して同じ stack frame に戻る」よりも、conversation history 上で一貫した resume 境界を定義する必要があった。

また Spring event bridge は observability 用であり、hook と同じ mutable event object を publish すると listener 側から runtime 制御に影響できてしまう。Phase 4 instruction の観測専用要件を守るには、hook dispatch と ApplicationEvent publish の境界を明示する必要がある。

## Decision

Arachne は Phase 4 の interrupt / resume と Spring event bridge について、次の方針を採用する。

- interrupt は `BeforeToolCallEvent` で発生させ、実際の tool invocation を始める前に loop を止める。
- interrupt の返却面は `AgentResult.interrupts()` と `AgentResult.resume(...)` に置く。resume は元の runtime instance に対して行う。
- resume 時の external response は、その interrupt に対応する `tool_result` user message として conversation history に追加し、その次の model call から loop を再開する。
- 現フェーズでは resume 後に元の tool invocation を自動再実行しない。human response を model に返し、次の行動は model と hook が決める。
- Spring `ApplicationEvent` bridge は `ApplicationEventPublishingHookProvider` が担い、hook event そのものではなく immutable snapshot を `ArachneLifecycleApplicationEvent` として publish する。
- Spring listener は観測だけを行い、runtime control flow の変更は `HookProvider` / `@ArachneHook` 経由に限定する。

## Consequences

- interrupt は tool 実行境界に明確に閉じ込められ、model loop と session restore の整合を保ちやすい。
- `AgentResult` が human-in-the-loop の public API 入口になり、Phase 5 以降の higher-level plugin や skill が同じ再開境界を前提にできる。
- resume は conversation history 上の `tool_result` 追加として表現されるため、Bedrock 固有処理を core に持ち込まずに済む。
- Spring event bridge は listener 側から mutable hook event を触れないため、observability と control hook の責務分離が保たれる。

## Alternatives Considered

### 1. resume 後に元の tool invocation を自動再開する

採用しない。tool side effect をどの時点で保証するかが不明瞭になり、Phase 4 の責務境界を超える。

### 2. interrupt の resume API を `Agent` 側に置き、`AgentResult` には一覧だけ返す

採用しない。resume 対象の interrupt 群は直前の結果に結び付いているため、結果オブジェクト側に寄せた方が誤用しにくい。

### 3. Spring listener に mutable hook event をそのまま publish する

採用しない。observability 用の API が実質的な制御フロー hook になってしまい、Phase 4 instruction に反する。