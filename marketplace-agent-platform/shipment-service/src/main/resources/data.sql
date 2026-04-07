insert into shipment_order_templates (order_id, case_type, tracking_number, milestone_summary, delivery_confidence, shipping_exception_summary, updated_at)
values
    ('ord-demo-001', 'ITEM_NOT_RECEIVED', 'TRACK-ord-demo-001', 'Carrier tracking shows label creation and in-transit milestones but no final delivery scan.', 'LOW', 'Shipment remains in a not-delivered state for the current case.', '2026-04-08T00:00:00Z'),
    ('ord-demo-002', 'ITEM_NOT_RECEIVED', 'TRACK-ord-demo-002', 'Carrier tracking shows label creation and in-transit milestones but no final delivery scan.', 'LOW', 'Shipment remains in a not-delivered state for the current case.', '2026-04-08T00:00:00Z'),
    ('order-dmg-1', 'DELIVERED_BUT_DAMAGED', 'TRACK-order-dmg-1', 'Carrier tracking shows final delivery on the doorstep with photo proof captured the same day.', 'HIGH', 'Shipment was delivered, but the package exterior shows impact damage and moisture exposure.', '2026-04-08T00:00:00Z'),
    ('order-risk-1', 'HIGH_RISK_SETTLEMENT_HOLD', 'TRACK-order-risk-1', 'Carrier tracking shows delivery completion, but the handoff location and recipient pattern diverge from the account''s normal history.', 'MEDIUM', 'Shipment completed, but delivery evidence is unusual enough to support a settlement hold pending risk review.', '2026-04-08T00:00:00Z')
on conflict (order_id) do update set
    case_type = excluded.case_type,
    tracking_number = excluded.tracking_number,
    milestone_summary = excluded.milestone_summary,
    delivery_confidence = excluded.delivery_confidence,
    shipping_exception_summary = excluded.shipping_exception_summary,
    updated_at = excluded.updated_at;
