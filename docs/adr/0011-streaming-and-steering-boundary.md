# 0011. Streaming And Steering Boundary

## Status

Accepted

## Context

Phase 6 では、既存の同期 `Agent.run(...)` 契約を壊さずに 2 つの機能を追加する必要があった。1 つ目は逐次購読できる streaming invocation API、2 つ目は context-aware な steering である。

ここで閉じるべき論点は次の 4 つだった。

- streaming を reactive 前提にせず、既存の blocking runtime にどう追加するか
- Bedrock の provider streaming をどこまで core API に露出させるか
- tool steering と model steering をどの hook/event 境界に載せるか
- streaming と model steering retry が衝突する場合、discard/retry をどう表現するか

Phase 4 と Phase 5 で hook/plugin 境界はすでに public contract 化されている。そのため Phase 6 では新しい middleware layer や policy engine を足すのではなく、既存 runtime-local hook 境界の延長として最小追加に留める必要があった。

## Decision

Arachne は Phase 6 の streaming / steering について、次の方針を採用する。

- agent-level streaming API は `Agent.stream(String, Consumer<AgentStreamEvent>)` とする。既存の `Agent.run(...)` は維持し、streaming は opt-in の追加経路として扱う。
- stream event の public contract は `AgentStreamEvent` に置き、最低限 `TextDelta`、`ToolUseRequested`、`ToolResultObserved`、`Retry`、`Complete` を含める。
- model provider 側の optional capability は `StreamingModel` として切り出す。通常の `Model` 実装は既存の `converse(...)` のままでよく、streaming 未対応 model は `converse(...)` を反復して fallback できる。
- Bedrock 固有の `converseStream` / async client / event mapping は `BedrockModel` の近傍に閉じ込め、core event loop には `ModelEvent` だけを流す。
- steering は `SteeringHandler` を `Plugin` の一種として提供し、runtime-local plugin として builder から opt-in で載せる。
- tool steering は `BeforeToolCallEvent` の上に載せる。`Proceed` は何もしない、`Guide` は tool 実行を行わず guidance を error `ToolResult` として返す、`Interrupt` は既存 interrupt 契約に接続する。
- model steering は `AfterModelCallEvent` の上に載せる。`Guide` は現在の model response を conversation history に追加せず破棄し、guidance を新しい user message として追加して次の model turn を retry する。
- streaming 中に model steering が retry を要求した場合は、すでに流れた provisional delta を巻き戻さず、`AgentStreamEvent.Retry` を emit して subscriber 側に discard/retry 境界を明示する。
- Spring integration での steering opt-in は `AgentFactory.Builder#steeringHandlers(...)` とし、streaming opt-in は factory から build した agent の `stream(...)` 呼び出しで行う。

## Consequences

- `Agent.run(...)` と既存の Phase 1 から Phase 5 の同期利用パターンは既定で維持される。
- streaming は callback-based で追加されるため、reactive stack や別 runtime model を利用者に強制しない。
- Bedrock provider streaming は `StreamingModel` 実装に閉じ込められ、core は provider 非依存の `ModelEvent` だけを扱う。
- steering は hook/plugin の上に載るため、skills と同じく runtime-local 拡張として組み合わせられる。
- model steering retry は sync path では message discard を正しく表現できる一方、streaming path では subscriber が `Retry` event を理解する必要がある。

## Alternatives Considered

### 1. reactive / `Publisher` ベースの streaming API を public contract にする

採用しない。Phase 6 の goal は既存の同期 runtime を維持したまま opt-in で streaming を追加することであり、利用者に reactive 前提を押し付けるのは過剰である。

### 2. steering 用に独立した policy engine / middleware chain を導入する

採用しない。既存の `HookRegistry` / `Plugin` 境界で閉じる責務に対して抽象度が高すぎ、Phase 6 の roadmap row を超える。

### 3. model steering retry 時に streaming delta を内部で buffer し、承認されるまで外へ出さない

採用しない。逐次購読 API の価値を下げるうえ、tool-use 前の text delta も見えなくなる。代わりに `Retry` event で provisional response の discard 境界を明示する。

### 4. tool guidance を別の event type として会話履歴に追加し、tool result にはしない

採用しない。現在の Bedrock/core 契約では tool 実行境界から model へ戻す標準形が `tool_result` であり、Phase 6 ではそこを再利用する方が小さい。