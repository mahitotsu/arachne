-- Demo seed data for delivery_orders.
-- customer_accounts are seeded by customer-service ApplicationRunner at startup (BCrypt-hashed).
-- All inserts use ON CONFLICT (order_id) DO NOTHING so this script is safe to re-run.
--
-- Demo personas referenced here:
--   cust-demo-001  login: demo      Aoi Sato
--   cust-demo-002  login: family    Family Account
--   cust-solo-001  login: solo      Hina Nakamura
--   cust-corp-001  login: corporate Corp Account
--
-- Prices are computed directly from the menu-service catalog.
-- Delivery fee 300 yen is added to each order total.

INSERT INTO delivery_orders (order_id, customer_id, item_summary, subtotal, total, eta_label, payment_status, created_at)
VALUES
    ('ord-seed-0001',
     'cust-demo-001',
     '2x Crispy Chicken Box, 1x Curly Fries, 2x Lemon Soda',
     2770.00, 3070.00, '18 min via In-house Express', 'CHARGED',
     NOW() - INTERVAL '3 days'),

    ('ord-seed-0002',
     'cust-demo-001',
     '1x Teriyaki Chicken Box, 1x Iced Latte',
     1240.00, 1540.00, '22 min via Partner Standard', 'CHARGED',
     NOW() - INTERVAL '1 day'),

    ('ord-seed-0003',
     'cust-demo-002',
     '3x Kids Cheeseburger Set, 1x Nugget Share Box, 2x Matcha Soft Serve',
     3380.00, 3680.00, '30 min via Partner Standard', 'CHARGED',
     NOW() - INTERVAL '2 days'),

    ('ord-seed-0004',
     'cust-solo-001',
     '1x Salmon Rice Bowl, 1x Hot Matcha Latte',
     1240.00, 1540.00, '20 min via In-house Express', 'CHARGED',
     NOW() - INTERVAL '6 hours')

ON CONFLICT (order_id) DO NOTHING;
