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
| EventLoop に hook コールサイトの骨格を仕込む（no-op） | `[ ]` |
| Integration test: Bedrock に接続して文字列が返る | `[ ]` |

---

### Phase 2 — @StrandsTool & Structured Output `[ ]`

**Goal**: `@StrandsTool` を付けたメソッドが自動的にエージェントから呼ばれる。
型付き戻り値（Structured Output）も取得できる。

| Task | Status |
|---|---|
| `@StrandsTool` / `@ToolParam` アノテーション定義 | `[ ]` |
| JavaクラスからJSON Schema自動生成（共通インフラ） | `[ ]` |
| `AnnotationToolScanner` — SpringコンテキストからBean自動検出 | `[ ]` |
| `AgentFactory.builder().tools(...)` 連携 | `[ ]` |
| `StructuredOutputTool` — Javaクラスを ToolSpec に変換し最終ツールとして強制呼び出し | `[ ]` |
| `agent.run("...", MyRecord.class)` API — 型付き戻り値のサポート | `[ ]` |
| Integration test: ツール呼び出しが1往復で完結する | `[ ]` |
| Integration test: Structured Output で型付きオブジェクトが返る | `[ ]` |

---

### Phase 3 — 品質・UX・Hooks `[ ]`

**Goal**: 本番に持っていけるエラーハンドリング、設定管理、および AOP 的な hook 介入ができる。

| Task | Status |
|---|---|
| リトライ戦略（スロットリング、`MAX_ATTEMPTS=6` 相当） | `[ ]` |
| システムプロンプト対応 | `[ ]` |
| `SlidingWindowConversationManager`（トークンあふれ防止） | `[ ]` |
| エラー型整理（`ContextWindowOverflowException` 等） | `[ ]` |
| `application.yml` でモデルID・リージョン・リトライ設定 | `[ ]` |
| `HookRegistry` + `HookProvider` — 可変イベントによる制御フロー介入 | `[ ]` |
| 全 hook イベント型: `BeforeInvocationEvent`, `AfterInvocationEvent`, `BeforeToolCallEvent`, `AfterToolCallEvent`, `BeforeModelCallEvent`, `AfterModelCallEvent`, `MessageAddedEvent` | `[ ]` |
| `@ArachneHook` アノテーション — Spring Bean として自動検出・登録 | `[ ]` |
| Spring `ApplicationEvent` ブリッジ — 観測専用の通知を Spring イベントとして publish | `[ ]` |

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
