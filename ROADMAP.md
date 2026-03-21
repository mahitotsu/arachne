# Arachne Roadmap

Java port of [Strands Agents Python SDK](https://github.com/strands-agents/sdk-python)
with Spring Boot integration.

---

## MVP Goal

> "Spring アプリに `AgentFactory` を注入し、会話スコープごとに agent runtime を作って、文字列を渡すだけで tool-use ループが動く"

```java
@Service
class ChatService {

    private final AgentFactory factory;

    ChatService(AgentFactory factory) {
        this.factory = factory;
    }

    AgentResult reply(String prompt) {
        return factory.builder()
            .tools(new WeatherTool())
            .build()
            .run(prompt);
    }
}
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

### Phase 3 — 品質・設定UX・Session `[x]`

**Goal**: 本番に持っていけるエラーハンドリング、複数エージェント向け設定UX、会話の永続化がある。

| Task | Status |
|---|---|
| リトライ戦略（スロットリング、`MAX_ATTEMPTS=6` 相当） | `[x]` |
| `SlidingWindowConversationManager`（トークンあふれ防止） | `[x]` |
| `SummarizingConversationManager`（LLMで要約して圧縮） | `[x]` |
| エラー型整理（`ContextWindowOverflowException` 等） | `[x]` |
| `application.yml` でリトライ・会話管理・セッション設定 | `[x]` |
| **Named Agent** 設定 — `arachne.strands.agents.<name>.*` で agent ごとの model / system prompt / tools / policy を宣言 | `[x]` |
| `AgentFactory.builder("name")` または `NamedAgentFactory` — named default から agent を構築 | `[x]` |
| Spring 連携 — named agent の bean / qualifier / sample を提供 | `[x]` |
| `AgentState` — ツールや Hook から参照できるセッションスコープの key-value ストア | `[x]` |
| `SessionManager` インターフェース + `InMemorySessionManager` / Spring Session adapter | `[x]` |
| `FileSessionManager` — 会話履歴をファイルに永続化、Spring `@Bean` で差し替え可能 | `[x]` |
| Spring Session Redis adapter — explicit `sessionId` を維持したまま Redis backend に保存/復元 | `[x]` |
| Spring Session JDBC adapter — explicit `sessionId` を維持したまま JDBC backend に保存/復元 | `[x]` |
| Redis integration test — Testcontainers で新しい agent instance への restore を検証 | `[x]` |
| JDBC integration test — 新しい agent instance への restore を検証 | `[x]` |
| Runnable Redis sample — Docker Compose で Redis を起動し、再起動を跨いだ session restore を確認 | `[x]` |
| Runnable JDBC sample — ローカル DB で再起動を跨いだ session restore を確認 | `[x]` |

---

### Phase 3.5 — Spring 整合性レビュー `[x]`

**Goal**: Phase 4 に進む前に、Agent / Tool / Executor の Spring 統合方針を見直し、
マルチスレッド環境でも誤用しにくい API と wiring に整える。

| Task | Status |
|---|---|
| Agent lifecycle 方針の確定 — stateful な `Agent` を singleton `@Bean` として共有しない前提を明文化し、短命 instance / provider 経由の利用を標準化 | `[x]` |
| Agent 定義と実行時状態の境界レビュー — session 永続化と会話状態を踏まえ、singleton-safe な definition と短命 runtime の分離が必要か結論を出す | `[x]` |
| ADR 運用の導入 — Phase 3.5 以降の重要判断を `docs/adr/` に記録するルールと対象範囲を定める | `[x]` |
| 既存判断の ADR 化 — 今回見直す項目だけでなく、現時点で採用済み・保留中の重要判断も ADR として棚卸しする | `[x]` |
| tool input / structured output の binding パイプライン見直し — data binding と constraint validation の責務を分離し、Bean Validation をどこに使うか整理する | `[x]` |
| Spring 標準インフラとの整合 — `Validator`, `ObjectMapper`, `ConversionService`, `Executor` / `TaskExecutor` の再利用方針を定める | `[x]` |
| `ToolExecutor` 実行基盤の見直し — 並列実行を固定実装にせず、Spring から差し替え可能な execution backend に寄せる | `[x]` |
| Web / multi-thread 利用ガイドと sample の更新 — `AgentFactory` / `ObjectProvider` ベースの安全な利用例を追加する | `[x]` |
| Concurrency test / wiring test の追加 — 新しい lifecycle 方針が Spring 環境で破綻しないことを検証する | `[x]` |

---

### Phase 4 — Plugin & Hooks & Interrupts `[x]`

**Goal**: Spring Bean として hook を登録し、エージェントのライフサイクルに AOP 的に介入できる。
Interrupts で Human-in-the-loop ワークフローを実現できる。

| Task | Status |
|---|---|
| `HookRegistry` + `HookProvider` — 可変イベントによる制御フロー介入 | `[x]` |
| 全 hook イベント型: `BeforeInvocationEvent`, `AfterInvocationEvent`, `BeforeToolCallEvent`, `AfterToolCallEvent`, `BeforeModelCallEvent`, `AfterModelCallEvent`, `MessageAddedEvent` | `[x]` |
| `Plugin` インターフェース — hook + tool を一体でバンドルする単位 | `[x]` |
| `@ArachneHook` アノテーション — Spring Bean として自動検出・登録 | `[x]` |
| Spring `ApplicationEvent` ブリッジ — 観測専用の通知を Spring イベントとして publish | `[x]` |
| **Interrupts** — `BeforeToolCallEvent.interrupt()` で実行を一時停止し人間の承認を待つ | `[x]` |
| `AgentResult.interrupts` — 発生した interrupt の一覧と resume API | `[x]` |

---

### Phase 5 — Skills `[x]`

**Goal**: SKILL.md ファイルで定義した命令セットをエージェントに動的に注入できる。

Skills は [AgentSkills.io](https://agentskills.io) 仕様に準拠する。
Hook（Phase 4）の上に乗る機能のため、Phase 4 完了後に着手する。

| Task | Status |
|---|---|
| `Skill` データモデル — SKILL.md（YAMLフロントマター + Markdown本文）のパース | `[x]` |
| `AgentSkillsPlugin` — skill catalog / activation 経路を hook/plugin 境界の上に載せる | `[x]` |
| Spring classpath スキャン — `resources/skills/` 配下の SKILL.md を自動検出 | `[x]` |
| `AgentFactory.builder().skills(...)` API | `[x]` |
| Dedicated skill activation tool — available skill catalog を見せ、必要な skill 本文だけを遅延ロードする | `[x]` |
| 会話中にロード済み skill の重複注入抑止と context management 方針を定める | `[x]` |

---

### Phase 6 — Streaming & Steering `[ ]`

**Goal**: 既存の同期 agent runtime を維持したまま、ストリーミング出力と context-aware な実行制御を追加する。

| Task | Status |
|---|---|
| ストリーミングレスポンス（`Flux<AgentEvent>`） | `[ ]` |
| Steering Handler — plugin / hook の上で context-aware guidance を提供 | `[ ]` |
| Tool steering — `BeforeToolCallEvent` で proceed / guide / interrupt を制御 | `[ ]` |
| Model steering — `AfterModelCallEvent` で guidance を伴う retry を制御 | `[ ]` |
| Spring integration — `AgentFactory` から streaming / steering を opt-in で利用可能にする | `[ ]` |
| Test coverage — streaming event ordering と steering action の回帰を追加 | `[ ]` |

---

## Out of Scope (for now)

- OpenTelemetry / tracing（Hooks 経由で後付けは可能）
- Bidirectional streaming (audio/realtime)
- Agent Config (experimental)
- Evals SDK
- OpenAIModel / AnthropicModel
- MCP tool support
- マルチエージェント Swarm
- マルチエージェント Graph
- A2A protocol support
- Guardrails（Bedrock Guardrails 統合）
- Additional model providers / external protocol integrations beyond Bedrock MVP
