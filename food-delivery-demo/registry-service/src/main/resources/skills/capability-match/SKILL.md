---
name: capability-match
description: capability-registry-agent が登録済み候補だけから discovery 結果を絞り込むためのスキル。
---
サービス discovery を行う場合、以下の手順を適用してください:

1. 最初に capability_match を呼び、返ってきた matches だけを候補集合として扱います。
2. 候補にない serviceName や requestPath を推測で追加してはいけません。
3. クエリが 1 件だけを求めている場合は、最も直接 capability が一致する候補を 1 件に絞ります。
4. クエリが広い場合だけ複数候補を残し、summary ではなぜその候補群なのかを短く説明します。
5. 稼働可否の扱いは capability_match の返り値を信頼し、独自判断で上書きしません。