-- PostgreSQL indexes primary keys and unique columns, not foreign keys.
-- These are the ones the application searches on, or that a delete has to scan.

-- Order history of a user (GET /api/orders)
CREATE INDEX idx_orders_user_id ON orders (user_id);

-- Lines of an order, loaded with each order
CREATE INDEX idx_order_items_order_id ON order_items (order_id);

-- Deleting a product sets product_id to NULL on its order lines: without the index, a full scan
CREATE INDEX idx_order_items_product_id ON order_items (product_id);

-- Deleting a product removes it from the carts (ON DELETE CASCADE). cart_id already leads
-- the unique (cart_id, product_id) index, product_id alone does not
CREATE INDEX idx_cart_items_product_id ON cart_items (product_id);

-- Products of a category (GET /api/products?categoryId=)
CREATE INDEX idx_products_category_id ON products (category_id);
