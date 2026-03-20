# 0007. Phase 2 Tool Contracts

## Status

Accepted (retrospective)

## Context

Phase 2 で Arachne は `@StrandsTool` / `@ToolParam` による annotation-driven tools、qualifier ベースの discovered tool scope、`Agent.run(String, Class<T>)` による structured output を public API として導入した。これにより、利用者は JSON schema や tool spec を手書きせず、Spring bean のメソッドと Java 型をそのまま agent runtime の入出力契約として使えるようになった。

現行実装では `AnnotationToolScanner` が Spring context から `@StrandsTool` メソッドを発見し、`JsonSchemaGenerator` と `MethodTool` を使って tool spec と invocation binding を組み立てる。さらに `AgentFactory.Builder` は discovered tools を既定で runtime に取り込みつつ、`toolQualifiers(...)` と `useDiscoveredTools(false)` で agent ごとに公開する tool surface を制御できる。

structured output では `DefaultAgent` が呼び出しごとに final structured-output tool を追加し、`EventLoop` は model がその tool を使わなかった場合に強制リトライして型付き出力を回収する。この方式は Bedrock 固有 API ではなく、既存の tool loop の上に public contract を載せる実装である。

Phase 3 と Phase 3.5 では named agent defaults、binding/validation 境界、Spring `ObjectMapper` 再利用、pluggable tool executor などの整理が入ったが、Phase 2 の利用者向け契約そのものは継続している。closeout にあたって、この契約を retrospective ADR として固定しておく必要がある。

## Decision

Arachne は Phase 2 の public contract として、annotation-driven tool discovery、qualifier ベースの tool scope、structured output via final tool を採用し、今後も後方互換の基準として扱う。

標準方針は次のとおりとする。

- `@StrandsTool` を付けた Spring bean メソッドは、Arachne が自動検出する標準 tool 定義方法として扱う。
- `@ToolParam` は parameter 名、description、required などの schema-facing metadata を与える軽量な public annotation として維持する。
- discovered tools は既定で `AgentFactory.Builder` から見えるが、agent ごとの tool surface は `toolQualifiers(...)` と `useDiscoveredTools(false)` で絞り込めるようにする。
- Spring `@Qualifier` は Arachne の qualifier set に bridge され、`@StrandsTool(qualifiers = ...)` と同じスコープ制御に参加できるようにする。
- structured output は `Agent.run(String, Class<T>)` の public API で要求し、内部的には final structured-output tool を使って型付き結果を回収する方式を標準とする。
- model が structured-output tool を自発的に呼ばない場合、event loop が最終リトライでその tool の使用を強制できることを contract の一部として扱う。
- Bean Validation は Phase 2 の公開挙動に含めるが、制約を generated JSON schema に投影することまでは Phase 2 の契約に含めない。

## Consequences

- 利用者は tool contract と structured output contract を Java の annotation と型で記述でき、JSON schema の手書きや provider 固有 API への依存を避けられる。
- Spring integration では、tool discovery と agent-scoped tool selection の標準説明を `AgentFactory` 起点で統一できる。
- structured output は既存の tool loop の上に載るため、provider 非依存の core flow を維持しやすい。
- named agent、binding/validation 整理、executor backend 差し替えなどの後続変更は、この Phase 2 public contract を壊さない前提で進める必要がある。
- 一方で、generated schema は intentionally narrow なままであり、複雑な object graph、validation-to-schema 投影、provider native structured output などは後続フェーズの検討余地として残る。

## Alternatives Considered

### 1. annotation-driven tools を convenience API とみなし、手書き `Tool` 実装を唯一の標準契約にする

採用しない。Phase 2 の価値は Spring / Java 利用者が annotation と型で tool surface を定義できる点にあり、convenience 扱いにすると sample、docs、wiring test の前提とずれる。

### 2. discovered tools を application-global に固定し、agent ごとの scope 制御を持たない

採用しない。複数 agent が共存する Spring application では、tool surface を runtime ごとに絞れないと意図しない tool exposure を避けにくい。

### 3. structured output を provider native API にのみ依存させる

採用しない。Phase 2 の時点では Bedrock 以外も見据えて provider 非依存の contract が必要であり、tool loop ベースの実装の方が core flow と整合する。

### 4. Bean Validation 制約を generated schema にも必須で投影する

採用しない。制約投影は有益だが、当時の Phase 2 では runtime validation を先に public behavior として成立させることを優先し、schema projection までを完了条件には含めなかった。