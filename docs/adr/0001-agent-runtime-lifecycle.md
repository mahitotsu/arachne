# 0001. Agent Runtime Lifecycle

## Status

Accepted (retrospective)

## Context

Arachne の `DefaultAgent` は、単なる definition ではなく実行時状態を抱えた runtime object である。`messages` をインスタンス内で保持し、`run(...)` のたびに会話履歴を更新する。session が有効な場合は constructor 時に復元し、各 invocation の後に保存も行う。

Phase 2 と Phase 3 では、Spring から `Agent` を `@Bean` として公開し、そのまま他の singleton bean に注入する sample を用意してきた。これは簡潔ではあるが、Web や multi-thread 環境では 1 つの runtime instance に複数リクエストが流れ込みやすく、会話履歴や `AgentState` が意図せず共有される。

Phase 3 で `SessionManager` と restore/save が導入されたことで、この性質はさらに明確になった。Arachne では session backend に履歴を退避できるため、agent を長寿命 singleton として共有しなくても multi-turn を成立させられる。Phase 4 では hook、plugin、interrupt が runtime state と制御フローへ介入するため、runtime の共有前提を残したまま進むと誤用しやすさが増える。

このため、Spring integration の標準利用パターンとして何を shared bean にしてよく、何を per-use runtime にすべきかを明文化する必要がある。

## Decision

Arachne は `Agent` を stateful な runtime object として扱い、shared singleton Spring bean として共有する利用を標準パターンにしない。

標準方針は次のとおりとする。

- shared してよい標準入口は `AgentFactory` とその builder defaults である。
- 各 invocation、または各 session / conversation 単位で短命の `Agent` runtime instance を構築する利用を標準化する。
- Spring integration では `AgentFactory`、`ObjectProvider`、またはそれに準ずる provider 経由で runtime を取得するパターンを優先する。
- `DefaultAgent` は definition と execution state を同時に持つ現行 runtime として扱い続ける。definition/runtime を別型に分けるかは後続 ADR で改めて判断する。
- 既存の sample や guide にある `Agent` の直接注入例は、標準推奨ではなく簡易サンプルまたは今後見直す対象として扱う。

## Consequences

- Spring Boot auto-configuration と `AgentFactory` は、runtime instance を毎回安全に組み立てる標準統合入口として位置づけられる。
- Web や multi-thread 環境では、conversation state の accidental sharing を避けやすくなる。
- sample、user guide、wiring test は、`Agent` bean の直接共有ではなく factory/provider ベースの利用例へ段階的に寄せる必要がある。
- `DefaultAgent` の mutable state を前提とした現在の実装は維持できるため、Phase 3.5 では大きな抽象追加を強制しない。
- runtime と definition の分離要否、scope 制御、resume API の表現は後続判断として残る。

## Alternatives Considered

### 1. `Agent` を shared singleton bean のまま標準化する

採用しない。現在の `DefaultAgent` は会話履歴と state をインスタンス内に保持するため、同期化や利用者側 discipline に依存しやすい。Spring では「inject できるなら共有してよい」と誤解されやすく、標準 API としては危険である。

### 2. 共有 singleton のまま内部同期で安全化する

採用しない。排他制御を入れても conversation 単位の分離は表現できず、性能劣化と API の分かりにくさを増やす。session restore/save の意味論も曖昧になる。

### 3. Spring の prototype scope に委ねる

採用しない。prototype scope は一部の誤用を減らすが、Arachne 側の標準利用パターンを十分には表現しない。session 単位での生成、tool からの nested invocation、non-Spring 環境での意味論も説明しにくい。

### 4. 先に definition/runtime を別型へ全面分離する

今回は採用しない。方向性としては有力だが、Phase 3.5 の目的はまず誤用しにくい標準方針を固めることであり、直ちに新しい public abstraction を追加する段階ではない。
