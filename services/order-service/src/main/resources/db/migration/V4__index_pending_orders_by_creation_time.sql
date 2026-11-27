-- Unpaid orders past their deadline, looked up every minute by PendingOrderExpiry
-- (status = 'PENDING' AND created_at < deadline, oldest first). Partial: only the PENDING rows,
-- a small share of the table once the orders get paid or cancelled
CREATE INDEX idx_orders_pending_created_at ON orders (created_at) WHERE status = 'PENDING';
