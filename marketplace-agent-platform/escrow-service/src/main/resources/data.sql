insert into escrow_order_templates (order_id, case_type, hold_state, amount, currency, prior_settlement_status, updated_at)
values
    ('ord-demo-001', 'ITEM_NOT_RECEIVED', 'HELD', 199.99, 'USD', 'NO_PRIOR_REFUND', '2026-04-08T00:00:00Z'),
    ('ord-demo-002', 'ITEM_NOT_RECEIVED', 'HELD', 249.50, 'USD', 'NO_PRIOR_REFUND', '2026-04-08T00:00:00Z'),
    ('order-dmg-1', 'DELIVERED_BUT_DAMAGED', 'HELD', 89.90, 'USD', 'NO_PRIOR_REFUND', '2026-04-08T00:00:00Z'),
    ('order-risk-1', 'HIGH_RISK_SETTLEMENT_HOLD', 'HELD', 1499.00, 'USD', 'NO_PRIOR_REFUND', '2026-04-08T00:00:00Z')
on conflict (order_id) do update set
    case_type = excluded.case_type,
    hold_state = excluded.hold_state,
    amount = excluded.amount,
    currency = excluded.currency,
    prior_settlement_status = excluded.prior_settlement_status,
    updated_at = excluded.updated_at;
