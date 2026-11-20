-- PostgreSQL indexes primary keys and unique columns, not foreign keys or plain columns.
-- carts.user_email is unique, so already indexed; cart_items.cart_id leads the unique
-- (cart_id, product_id) index.

-- Order history of a user (GET /api/orders), found by the email of the JWT
CREATE INDEX idx_orders_user_email ON orders (user_email);

-- Lines of an order, loaded with each order
CREATE INDEX idx_order_items_order_id ON order_items (order_id);
