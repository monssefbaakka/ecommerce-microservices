-- Payment history of a user (GET /api/payments), found by the email of the JWT.
-- Only the primary key is indexed by PostgreSQL
CREATE INDEX idx_payments_user_email ON payments (user_email);
