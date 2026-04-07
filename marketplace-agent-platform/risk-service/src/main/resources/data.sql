insert into risk_review_templates (order_id, case_type, operator_role, indicator_summary, manual_review_required, policy_flags, summary, updated_at)
values
    ('ord-demo-001', 'ITEM_NOT_RECEIVED', 'CASE_OPERATOR', 'No elevated fraud signal detected for the current order.', true, 'FINANCE_CONTROL_REVIEW_REQUIRED', 'Risk review found no elevated fraud signal but requires finance control confirmation for settlement-changing actions.', '2026-04-08T00:00:00Z'),
    ('ord-demo-002', 'ITEM_NOT_RECEIVED', 'CASE_OPERATOR', 'No elevated fraud signal detected for the current order.', true, 'FINANCE_CONTROL_REVIEW_REQUIRED', 'Risk review found no elevated fraud signal but requires finance control confirmation for settlement-changing actions.', '2026-04-08T00:00:00Z'),
    ('order-dmg-1', 'DELIVERED_BUT_DAMAGED', 'CASE_OPERATOR', 'No elevated fraud signal detected, but the damage dispute needs seller and inspection evidence.', false, 'DAMAGE_EVIDENCE_REQUIRED', 'Risk review found no fraud escalation, but the damage claim still needs corroborating evidence before settlement changes.', '2026-04-08T00:00:00Z'),
    ('order-risk-1', 'HIGH_RISK_SETTLEMENT_HOLD', 'CASE_OPERATOR', 'Elevated fraud and account-takeover indicators are present for the current order.', true, 'HIGH_RISK_SETTLEMENT_HOLD,ACCOUNT_TAKEOVER_REVIEW,FINANCE_CONTROL_REVIEW_REQUIRED', 'Risk review identified elevated fraud indicators and requires a settlement hold until controls are cleared.', '2026-04-08T00:00:00Z')
on conflict (order_id) do update set
    case_type = excluded.case_type,
    operator_role = excluded.operator_role,
    indicator_summary = excluded.indicator_summary,
    manual_review_required = excluded.manual_review_required,
    policy_flags = excluded.policy_flags,
    summary = excluded.summary,
    updated_at = excluded.updated_at;
