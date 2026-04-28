---
name: delivery-routing
description: 自社配送と外部 ETA 候補を同じ基準で比較し、配送推奨を決めるためのスキル。
---
配送候補を比較する場合、以下の手順を適用してください:

1. まず check_courier_availability で自社レーンを確認し、次に get_traffic_weather で遅延要因を確認します。
2. その後に discover_eta_services を呼び、返ってきた AVAILABLE な候補だけを対象に call_eta_service を実行します。
3. 返ってきていない外部サービスや、NOT_AVAILABLE の候補を推測で比較対象に加えてはいけません。
4. 「急ぎ」は最短 ETA、「安く」は最安料金、それ以外は ETA と料金のバランスを優先します。
5. recommendationReason では、選ばれた候補が他候補より何で優れているかを現在の文脈に即して説明します。
6. 配送の確約や手配完了は宣言せず、比較結果と推奨に責務を限定します。