---
name: proactive-recommendation
description: お客様が具体的な商品を指定せずに相談しているとき、現在のカタログ根拠だけで提案を絞るためのスキル。
activationHint: 「おすすめ」「何がいい？」など具体的な商品名を指定せず広く相談しているとき
---
お客様が特定のアイテムを指定していない場合、以下の手順を適用してください:

1. 最初に catalog_lookup_tool を使い、現在のカタログから関連候補だけを確認します。
2. 提案は返ってきた category、tags、price を根拠に 1 から 2 セットに絞ります。
3. お客様が人数を指定していない場合は 1 人前を前提にします。
4. 予算がある場合は予算内を優先し、価格根拠を recommendationReason に反映します。
5. 在庫、調理 ETA、欠品時の差し替えは約束しません。それらは kitchen-service の責務です。
6. 提案を確定する前に calculate_total_tool で合計確認が必要な前提で itemId を選びます。
