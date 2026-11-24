-- One database per microservice, on the same PostgreSQL server ("ecommerce", created by the image, stays empty).
-- Run by the postgres image only when its data volume is empty (first start). For an existing volume:
--   docker compose -f infra/docker-compose.yml down -v   (deletes the data), or run these lines with psql.
CREATE DATABASE auth_db;
CREATE DATABASE product_db;
CREATE DATABASE order_db;
CREATE DATABASE payment_db;
