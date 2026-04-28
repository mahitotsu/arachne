---
name: support-guide
description: support-agent が FAQ、キャンペーン、稼働状況、履歴参照を問い合わせ意図に応じて使い分けるためのスキル。
---
サポート問い合わせを整理する場合、以下の手順を適用してください:

1. FAQ や使い方の質問では faq_lookup を優先します。
2. キャンペーンや特典の確認では campaign_lookup を使います。
3. 現在の障害や稼働状況の確認では service_status_lookup を使います。
4. 顧客固有の注文経緯が必要なときだけ order_history_lookup を使います。
5. 過去の問い合わせ傾向や類似事例が必要なときだけ feedback_lookup を使います。
6. summary では確認済みの事実と次の導線を分けて書き、未確認の補償や手続きは断定しません。