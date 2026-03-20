# 0005. Binding And Validation Boundaries

## Status

Accepted

## Context

Phase 2 で tool input validation と structured output validation が導入され、現在の実装では tool invocation と structured output capture の両方で、まず `ObjectMapper` による値変換を行い、その後に Bean Validation を実行している。`MethodTool` は model から渡された input map をメソッド引数型へ変換し、その後に `validator.forExecutables().validateParameters(...)` を呼ぶ。`StructuredOutputTool` も同様に output 型へ変換した後で `validator.validate(...)` を行う。

この流れ自体は妥当だが、Phase 3.5 の観点では 2 つの問題が残っている。1 つ目は、binding failure と validation failure が同じ概念として語られやすく、責務境界が曖昧なこと。2 つ目は、Spring integration では `Validator` は auto-configuration で共有 bean として扱っている一方、`ObjectMapper` は各所で `new ObjectMapper()` されており、どこまで Spring 標準 bean を再利用すべきかが固定されていないことである。

また、Spring には `ConversionService` という標準変換基盤もあるが、現時点の Arachne の tool binding は JSON-like な object graph を Java 型へ変換する問題として実装されており、`ConversionService` を必須基盤として組み込む段階にはない。Phase 3.5 では、今後の実装変更に先立って、binding と validation の意味論、および Spring bean 再利用の優先範囲を明文化しておく必要がある。

## Decision

Arachne は tool input と structured output の処理を、binding と validation の 2 段階に分けて扱う。また Spring integration では、Bean Validation の入口として `Validator` を標準再利用対象とし、object binding に使う `ObjectMapper` は Spring が提供する共有 bean を優先して再利用できるよう整理する。`ConversionService` は現時点では必須契約にしない。

標準方針は次のとおりとする。

- binding は、model から受け取った JSON-like input を Java の tool argument または structured output 型へ変換する段階である。
- validation は、binding 後の Java object に対して Bean Validation 制約を適用する段階である。
- binding failure と validation failure は別カテゴリのエラーとして扱い、原因と責務を混同しない。
- Spring integration では `Validator` を標準の validation hook として再利用する。
- Spring integration で application-level data binding を行う箇所では、Spring が管理する `ObjectMapper` を優先的に再利用できるようにする。
- `ConversionService` は、明確な要求が出るまでは Arachne の標準 binding contract に含めない。必要になった場合に限定的に導入を再検討する。
- protocol 固有や provider 固有の内部 serialization は、必ずしも Spring の共有 mapper に統一しなくてよい。再利用対象はまず tool binding、structured output、session payload のような application-facing boundary から整理する。

## Consequences

- tool input error と Bean Validation error を別々に説明できるため、利用者にとって失敗原因が分かりやすくなる。
- Spring Boot integration では、`Validator` に加えて `ObjectMapper` の再利用方針も定まり、application-level customization が Arachne の binding に反映されやすくなる。
- 一方で、`ObjectMapper` の共有利用を広げると、現行の `new ObjectMapper()` 前提コードを段階的に見直す必要がある。
- `ConversionService` を今すぐ標準契約に入れないことで、実際のユースケースがない段階での過剰な抽象追加を避けられる。
- 次の実装・整理タスクでは、`AnnotationToolScanner`、`MethodTool`、`StructuredOutputTool`、session manager 周辺の mapper/validator 注入経路を揃えることが中心になる。

## Alternatives Considered

### 1. binding と validation をひとまとめの「入力検証」として扱い続ける

採用しない。型変換失敗と Bean Validation 制約違反では原因も修正方法も異なり、Phase 3.5 で責務境界を明文化する目的に反する。

### 2. `Validator` だけでなく `ObjectMapper` と `ConversionService` もただちに必須の Spring 契約にする

採用しない。`ObjectMapper` の再利用は有益だが、`ConversionService` まで一気に必須化すると現行の binding 問題に対して過剰である。

### 3. Spring bean は使わず、Arachne が独自に mapper と validator を毎回生成する

採用しない。Spring Boot integration の一貫性を損ない、利用者の customization も反映されにくい。

### 4. `ObjectMapper` だけを再利用し、validation は Arachne 独自実装へ寄せる

採用しない。Jakarta Bean Validation はすでに Phase 2 の公開的な挙動の一部であり、Spring 環境でも `Validator` の再利用を優先した方が自然である。
