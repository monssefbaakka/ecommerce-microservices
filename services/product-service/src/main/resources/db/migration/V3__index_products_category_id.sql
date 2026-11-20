-- PostgreSQL indexes primary keys and unique columns, not foreign keys.
-- Products of a category (GET /api/products?categoryId=), and the foreign key check
-- PostgreSQL runs on products when a category is deleted (409 if it still has some)
CREATE INDEX idx_products_category_id ON products (category_id);
