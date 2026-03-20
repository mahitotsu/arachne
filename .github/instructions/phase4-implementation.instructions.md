---
description: "Arachne の Phase 4 実装時に使う。hook、plugin、interrupts、Spring bridge を扱うときの指針。"
applyTo: "src/main/java/**/*.java"
---
# Phase 4 実装ガイド

現在の重点は ROADMAP の Phase 4、すなわち hooks、plugins、interrupts である。

## 設計の優先順位
- public API を先に設計する。対象は hook event、HookRegistry / HookProvider、Plugin、interrupt result / resume API である。
- 観測用イベント通知、制御フロー介入、Spring bridge の責務を分離する。
- Bedrock 固有の事情が必須でない限り、Phase 4 のロジックは provider 非依存に保つ。
- 汎用的すぎる拡張フレームワークより、明示的な型と狭い interface を優先する。

## 守るべき境界
- hook event は lifecycle 上の出来事を表し、provider 固有の request/response 型を直接露出しない。
- HookRegistry / HookProvider は hook の解決と dispatch を担い、EventLoop 本体を hook 分岐だらけにしない。
- interrupt は Phase 4 で定義された境界、特に tool 呼び出し前後の制御に留め、model・tool・session の責務を混同しない。
- Spring `ApplicationEvent` ブリッジは観測専用に保ち、制御フローを書き換える入口にしない。

## 互換性ルール
- 既存の Phase 1 AgentFactory 経路を壊さない。
- 既存の Phase 2 annotation tool / structured output API を壊さない。
- 既存の Phase 3 retry / conversation / session 挙動を壊さない。
- Bedrock の request/response mapping を generic な core package に移さない。
- hook や plugin が未設定のとき、`agent.run("...")` の挙動は従来どおり維持する。

## 判断ルール
- クラスを増やすのは、そのクラスが Phase 4 の中で独立した責務を持つ場合だけにする。
- 将来の plugin ecosystem や provider 拡張のためだけに存在する変更は先送りする。
- middleware 風の抽象化は、ROADMAP 上の hook / plugin / interrupt 要件を満たす範囲に留める。
- public API を変える機能では、完了扱いにする前に必ずテストを更新する。
