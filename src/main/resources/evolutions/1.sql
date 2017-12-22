# --- !Ups

DROP TABLE IF EXISTS "product" CASCADE;
CREATE TABLE IF NOT EXISTS "product" (id INT IDENTITY, name VARCHAR(255), price FLOAT, weight INT);
DELETE FROM "product";
INSERT INTO "product" (name, price, weight) VALUES ('Egg Whisk', 3.99, 150), ('Tea Cosy', 5.99, 100), ('Spatula', 1.00, 80);

# --- !Downs

DROP TABLE IF EXISTS "product" CASCADE;
