---
name: substitution-approval
description: 欠品時に menu-agent から候補を受け取り、kitchen-agent が承認可能な候補だけを残すためのスキル。
---
欠品時の代替承認を行う場合、以下の手順を適用してください:

1. menu_substitution_lookup は kitchen_inventory_lookup が unavailableItemIds を返したときだけ使います。
2. 候補を受け取ったら、自分のキッチンで今提供できるかを確認できるものだけ残します。
3. 候補が魅力的でも、実際に作れないものは承認しません。
4. 承認結果は unavailableItemId ごとに 1 件までに絞り、選ばなかった理由は summary で短く説明します。
5. 承認できる候補がない場合は、代替なしを明示します。