# Arachne Roadmap

Java port of [Strands Agents Python SDK](https://github.com/strands-agents/sdk-python)
with Spring Boot integration.

---

## MVP Goal

> "Spring アプリに `@Bean` でエージェントを定義し、文字列を渡すだけで tool-use ループが動く"

```java
@Bean
Agent agent(AgentFactory factory) {
    return factory.builder()
        .tools(new WeatherTool())
        .build();
}

AgentResult result = agent.run("東京の天気は？");
```

---

## Phases

### Phase 1 — Event Loop & BedrockModel `[ ]`

**Goal**: ツールなしで `agent.run("hello")` が返答する。

| Task | Status |
|---|---|
| `EventLoop` — model→tool→model サイクル（同期） | `[ ]` |
| `BedrockModel` — AWS SDK v2 ConverseAPI呼び出し | `[ ]` |
| `ConverseResponse` → `ModelEvent` マッピング | `[ ]` |
| `DefaultAgent` — EventLoopを持ち `run(String)` を実装 | `[ ]` |
| Integration test: Bedrock に接続して文字列が返る | `[ ]` |

---

### Phase 2 — @StrandsTool `[ ]`

**Goal**: `@StrandsTool` を付けたメソッドが自動的にエージェントから呼ばれる。

| Task | Status |
|---|---|
| `@StrandsTool` / `@ToolParam` アノテーション定義 | `[ ]` |
| JavaメソッドシグネチャからJSON Schema自動生成 | `[ ]` |
| `AnnotationToolScanner` — SpringコンテキストからBean自動検出 | `[ ]` |
| `AgentFactory.builder().tools(...)` 連携 | `[ ]` |
| Integration test: ツール呼び出しが1往復で完結する | `[ ]` |

---

### Phase 3 — 品質・UX `[ ]`

**Goal**: 本番に持っていけるエラーハンドリングと設定管理がある。

| Task | Status |
|---|---|
| リトライ戦略（スロットリング、`MAX_ATTEMPTS=6` 相当） | `[ ]` |
| システムプロンプト対応 | `[ ]` |
| `SlidingWindowConversationManager`（トークンあふれ防止） | `[ ]` |
| エラー型整理（`ContextWindowOverflowException` 等） | `[ ]` |
| `application.yml` でモデルID・リージョン・リトライ設定 | `[ ]` |

---

### Phase 4 — 拡張 `[ ]`

**Goal**: Bedrock以外のモデルとSpringのリアクティブスタックに対応する。

| Task | Status |
|---|---|
| `OpenAIModel` / `AnthropicModel` | `[ ]` |
| ストリーミングレスポンス（`Flux<AgentEvent>`） | `[ ]` |
| `HookRegistry`（BeforeInvocation / AfterInvocation イベント） | `[ ]` |
| MCP tool support | `[ ]` |
| マルチエージェント（Orchestrator） | `[ ]` |

---

## Out of Scope (for now)

- OpenTelemetry / tracing
- Session persistence
- Guardrails
- Bidirectional streaming (audio/realtime)
