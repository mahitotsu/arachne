---
name: order-handoff-boundary
description: support-agent が order-service へ引き継ぐべき相談だけを見極めるためのスキル。
---
order-service へのハンドオフを判断する場合、以下の手順を適用してください:

1. 再注文、注文内容変更、支払いのやり直し、注文フロー継続の依頼だけを handoffTarget=order の対象にします。
2. FAQ、キャンペーン、稼働状況だけで完結する相談は handoff しません。
3. handoffMessage には、やりたいことと必要最小限の注文文脈だけを短くまとめます。
4. support-agent 自身が注文変更や返金を実行するとは言わず、案内と引き継ぎに責務を限定します。