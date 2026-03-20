# 0002. Session Manager And Explicit Session IDs

## Status

Accepted (retrospective)

## Context

Phase 3 で Arachne は multi-turn 会話を process 外へ退避・復元するために `SessionManager` を導入した。core 側には `SessionManager`、`InMemorySessionManager`、`FileSessionManager` があり、Spring integration では Spring Session repository を利用する `SpringSessionManager` を追加した。

この設計では、Arachne の会話履歴、`AgentState`、conversation manager state を 1 つの agent session としてまとめて保存する。session persistence の目的は、単に Web session に便乗することではなく、新しい agent runtime instance へ会話状態を restore できるようにすることである。

Spring Session backend を使う場合、Redis や JDBC の repository は通常 backend 側で session id を生成する前提を持つ。しかし Arachne は user-facing な conversation identity を agent invocation 側で明示的に扱いたい。sample でも Redis/JDBC の両方で explicit `sessionId` を設定し、再起動後に同じ session を restore する使い方を採用している。

このため、どこまでを core の契約とし、どこからを Spring integration adapter の責務とするかを retrospective ADR として固定しておく必要がある。

## Decision

Arachne は session persistence の core 境界として `SessionManager` を採用し、backend の種類にかかわらず explicit `sessionId` を Arachne 側の契約として維持する。

標準方針は次のとおりとする。

- core は `SessionManager` を通じて agent session を load/save し、backend 固有 API へ直接依存しない。
- session の論理単位は `messages`、`AgentState`、conversation manager state を束ねた `AgentSession` である。
- file、in-memory、Redis、JDBC のいずれでも、Arachne 利用者が指定する explicit `sessionId` を conversation identity として扱う。
- Spring Session integration は storage adapter であり、backend ごとの session object 生成や attribute serialization の差異は adapter 側で吸収する。
- Spring Boot auto-configuration は、file directory 指定があれば `FileSessionManager` を選び、そうでなければ利用可能な Spring Session repository を `SpringSessionManager` へ橋渡しする標準入口を維持する。

## Consequences

- agent runtime を短命 instance に寄せても、同じ `sessionId` を指定すれば会話状態を restore できる。
- session persistence の public contract は backend 非依存に保たれ、Phase 4 以降の hook / interrupt 設計でも session identity を一貫して扱える。
- Redis/JDBC adapter は backend 内部型の差異を吸収する実装責務を負うため、Spring integration 側に一定の複雑さが残る。
- Web session や HTTP session と agent session は同一視しない。必要なら同じ ID を使えるが、Arachne の契約はあくまで explicit agent session id である。
- 将来ほかの storage backend を追加する場合も、まず `SessionManager` 契約に従うかどうかで判断できる。

## Alternatives Considered

### 1. backend 生成の session id に全面的に委ねる

採用しない。agent session identity が backend の都合に引きずられ、Redis/JDBC/file 間で利用パターンが揃わない。新しい agent instance への restore を利用者が明示的に制御しにくくなる。

### 2. Spring Web session をそのまま Arachne session とみなす

採用しない。Arachne は CLI、batch、tool 内 nested agent invocation でも動作するため、Web stack に session 概念を固定すると core 契約として狭すぎる。

### 3. file persistence のみを標準にし、Redis/JDBC は利用者責務とする

採用しない。Phase 3 の goal には Spring Session backend での restore 検証が含まれており、Spring Boot integration を提供するライブラリとして標準 adapter を持つ価値が高い。

### 4. session persistence を `Agent` 実装の内部詳細として隠し、明示的な境界を作らない

採用しない。storage backend の差し替え、テスト、Phase 3.5 以降の lifecycle 整理に不利であり、Arachne が何を固定契約としているかが不明瞭になる。
