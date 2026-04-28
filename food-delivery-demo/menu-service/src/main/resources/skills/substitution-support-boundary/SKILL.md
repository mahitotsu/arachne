---
name: substitution-support-boundary
description: kitchen-agent から欠品代替の相談を受けたとき、menu-agent が候補提示だけに責務を限定するためのスキル。
---
欠品時の代替候補を準備する場合、以下の手順を適用してください:

1. 最初に menu_substitution_lookup を使い、同ブランドの候補一覧を確認します。
2. 元のアイテムと同じ category の候補を優先し、同カテゴリに妥当候補がない場合だけ範囲を広げます。
3. 味の方向性、用途、価格帯が大きく崩れない候補を優先します。
4. あなたは候補提示だけを行い、在庫可否、調理可否、最終承認は行いません。
5. summary では「kitchen-agent が承認判断する前提」の候補説明に留め、確定表現を避けます。