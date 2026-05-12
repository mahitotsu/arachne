現在の変更を検証してコミット・プッシュまで実行する。準備が整っていない場合はコミットをブロックして理由を報告する。

$ARGUMENTS

## 手順

1. `git status --short` と `git diff --stat HEAD` で変更範囲を確認する。
2. `memories/repo/status.md` を読み、作業意図を確認する。

### 完了チェック（NOT_READY なら以降を実行せず報告して終了）

3. 変更が1つの責任境界に収まっているか確認する。複数エリアにまたがる場合はブロック。
4. shipped surface（公開API・Spring統合・セッション永続化・ツール境界・ドキュメント）に触れている場合、関連する truth surface が同期されているか確認する。最低限: `arachne/docs/project-status.md`、影響READMEとADR、`memories/repo/status.md`。
5. 以下のいずれかが true ならブロックして理由を報告する:
   - スコープ混在（無関係な変更が混入している）
   - ドキュメント不整合（shipped surface の変更がドキュメントに反映されていない）
   - 未解決の follow-up がある（このスライスに属する作業が残っている）

### 検証・コミット・プッシュ

6. 対象エリアに応じた最小限の検証を実行する。`.claude/repository-ops/repository-reading-guide.md` で検証コマンドを確認すること:
   - ライブラリ・shipped surface: `mvn test`
   - サンプル: `mvn -pl arachne -am install -DskipTests` → `mvn -f samples/pom.xml test`
   - food-delivery Java: `mvn -f food-delivery-demo/pom.xml test`
   - customer UI: `npm ci` → `npm run build`（`food-delivery-demo/customer-ui` 内）
   - ドキュメント・ワークフローのみ: パス・リンク・同期の目視確認
7. 検証失敗時はスコープ内の最小修正のみ適用する。新たな機能実装に踏み込まない。
8. ユーザー指定がない場合は実際のdiffからコミットメッセージを選ぶ。
9. `git add` → `git commit` を実行する。ユーザーがpushスキップを指示していない限り `git push` まで実行する。
10. push失敗（権限・保護ブランチ・non-fast-forward等）の場合は事実を報告して停止する。回避策を取らない。
11. 修正によって新たな変更が生じた場合、ユーザーが明示的に要求しない限りamendしない。追加コミットを使う。

## 出力フォーマット

```
State: SHIPPED | BLOCKED
Scope: <primary area>
Blockers:
- <ブロッカーまたはnone>
Verification:
- <コマンド/結果>
Commit:
- Status: completed | blocked | skipped
- Message: <コミットメッセージ>
Push:
- Status: completed | blocked | skipped
```
