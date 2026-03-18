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

### Phase 1 — Event Loop & BedrockModel `[x]`

**Goal**: ツールなしで `agent.run("hello")` が返答する。

| Task | Status |
|---|---|
| `EventLoop` — model→tool→model サイクル（同期） | `[x]` |
| `BedrockModel` — AWS SDK v2 ConverseAPI呼び出し | `[x]` |
| `ConverseResponse` → `ModelEvent` マッピング | `[x]` |
| `DefaultAgent` — EventLoopを持ち `run(String)` を実装 | `[x]` |
| `AgentFactory` + Spring Boot auto-configuration — 既定 `Model` と builder を提供 | `[x]` |
| `application.yml` によるデフォルト model / region / system prompt 設定 | `[x]` |
| EventLoop に hook コールサイトの骨格を仕込む（no-op） | `[x]` |
| Integration test: Bedrock に接続して文字列が返る | `[x]` |
| User guide + runnable sample app — Phase 1 の使い方と multi-turn を確認可能にする | `[x]` |

---

### Phase 2 — @StrandsTool & Structured Output `[x]`

**Goal**: `@StrandsTool` を付けたメソッドが自動的にエージェントから呼ばれる。
型付き戻り値（Structured Output）も取得できる。

| Task | Status |
|---|---|
| `@StrandsTool` / `@ToolParam` アノテーション定義 | `[x]` |
| JavaクラスからJSON Schema自動生成（共通インフラ） | `[x]` |
| `AnnotationToolScanner` — SpringコンテキストからBean自動検出 | `[x]` |
| `AgentFactory.builder().tools(...)` 連携 | `[x]` |
| qualifier ベースの discovered tool スコープ制御 — `toolQualifiers(...)` / `useDiscoveredTools(false)` / Spring `@Qualifier` bridge | `[x]` |
| `ToolExecutor` — 並行（デフォルト）と直列の切り替え | `[x]` |
| `StructuredOutputTool` — Javaクラスを ToolSpec に変換し最終ツールとして強制呼び出し | `[x]` |
| `agent.run("...", MyRecord.class)` API — 型付き戻り値のサポート | `[x]` |
| runtime validation — tool input と structured output に Bean Validation を適用 | `[x]` |
| Integration test: ツール呼び出しが1往復で完結する | `[x]` |
| Integration test: Structured Output で型付きオブジェクトが返る | `[x]` |
| **Agent-as-tool パターンのドキュメント・サンプル** — `@StrandsTool` を付けた `@Service` が別エージェントのツールになる Spring idiom を明示 | `[x]` |

---

### Phase 3 — 品質・設定UX・Session `[ ]`

**Goal**: 本番に持っていけるエラーハンドリング、複数エージェント向け設定UX、会話の永続化がある。

| Task | Status |
|---|---|
| リトライ戦略（スロットリング、`MAX_ATTEMPTS=6` 相当） | `[ ]` |
| `SlidingWindowConversationManager`（トークンあふれ防止） | `[ ]` |
| `SummarizingConversationManager`（LLMで要約して圧縮） | `[ ]` |
| エラー型整理（`ContextWindowOverflowException` 等） | `[ ]` |
| `application.yml` でリトライ・会話管理・セッション設定 | `[ ]` |
| **Named Agent** 設定 — `arachne.strands.agents.<name>.*` で agent ごとの model / system prompt / tools / policy を宣言 | `[ ]` |
| `AgentFactory.builder("name")` または `NamedAgentFactory` — named default から agent を構築 | `[ ]` |
| Spring 連携 — named agent の bean / qualifier / sample を提供 | `[ ]` |
| `AgentState` — ツールや Hook から参照できるセッションスコープの key-value ストア | `[ ]` |
| `SessionManager` インターフェース + `InMemorySessionManager` | `[ ]` |
| `FileSessionManager` — 会話履歴をファイルに永続化、Spring `@Bean` で差し替え可能 | `[ ]` |

---

### Phase 4 — Plugin & Hooks & Interrupts `[ ]`

**Goal**: Spring Bean として hook を登録し、エージェントのライフサイクルに AOP 的に介入できる。
Interrupts で Human-in-the-loop ワークフローを実現できる。

| Task | Status |
|---|---|
| `HookRegistry` + `HookProvider` — 可変イベントによる制御フロー介入 | `[ ]` |
| 全 hook イベント型: `BeforeInvocationEvent`, `AfterInvocationEvent`, `BeforeToolCallEvent`, `AfterToolCallEvent`, `BeforeModelCallEvent`, `AfterModelCallEvent`, `MessageAddedEvent` | `[ ]` |
| `Plugin` インターフェース — hook + tool を一体でバンドルする単位 | `[ ]` |
| `@ArachneHook` アノテーション — Spring Bean として自動検出・登録 | `[ ]` |
| Spring `ApplicationEvent` ブリッジ — 観測専用の通知を Spring イベントとして publish | `[ ]` |
| **Interrupts** — `BeforeToolCallEvent.interrupt()` で実行を一時停止し人間の承認を待つ | `[ ]` |
| `AgentResult.interrupts` — 発生した interrupt の一覧と resume API | `[ ]` |

---

### Phase 5 — Skills `[ ]`

**Goal**: SKILL.md ファイルで定義した命令セットをエージェントに動的に注入できる。

Skills は [AgentSkills.io](https://agentskills.io) 仕様に準拠する。
Hook（Phase 4）の上に乗る機能のため、Phase 4 完了後に着手する。

| Task | Status |
|---|---|
| `Skill` データモデル — SKILL.md（YAMLフロントマター + Markdown本文）のパース | `[ ]` |
| `AgentSkillsPlugin` — Skill を受け取り `BeforeInvocationEvent` でシステムプロンプトに注入 | `[ ]` |
| Spring classpath スキャン — `resources/skills/` 配下の SKILL.md を自動検出 | `[ ]` |
| `AgentFactory.builder().skills(...)` API | `[ ]` |

---

### Phase 6 — 拡張 `[ ]`

**Goal**: Bedrock以外のモデル、リアクティブスタック、安全機能に対応する。

| Task | Status |
|---|---|
| `OpenAIModel` / `AnthropicModel` | `[ ]` |
| ストリーミングレスポンス（`Flux<AgentEvent>`） | `[ ]` |
| MCP tool support | `[ ]` |
| マルチエージェント Swarm — 複数エージェントが協調して一つのタスクを解く | `[ ]` |
| マルチエージェント Graph — 有向グラフでエージェントの依存関係と実行順を定義 | `[ ]` |
| ※ Agent-as-tool（単純な委譲）は Phase 2 の `@StrandsTool` で実現済み | — |
| Guardrails — Bedrock Guardrails 統合（コンテンツフィルタリング、PII保護） | `[ ]` |

---

## Out of Scope (for now)

- OpenTelemetry / tracing（Hooks 経由で後付けは可能）
- Bidirectional streaming (audio/realtime)
- Agent Config (experimental)
- Evals SDK
