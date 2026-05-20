-- =============================================================================
-- ChriOnline - idempotent product listing moderation migration.
-- Safe to run multiple times on MySQL / MariaDB after chrionline_schema_all.sql.
-- =============================================================================

CREATE DATABASE IF NOT EXISTS chrionline CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE chrionline;

SET @db_name := DATABASE();

SET @sql := IF(
    EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'products' AND COLUMN_NAME = 'listing_status'),
    'SELECT ''listing_status already exists''',
    'ALTER TABLE products ADD COLUMN listing_status VARCHAR(20) NOT NULL DEFAULT ''APPROVED'' COMMENT ''PENDING = en attente, APPROVED = visible catalogue, REJECTED = refuse'' AFTER source_catalogue'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'products' AND COLUMN_NAME = 'seller_id'),
    'SELECT ''seller_id already exists''',
    'ALTER TABLE products ADD COLUMN seller_id INT NULL COMMENT ''id_user du vendeur (soumission)'' AFTER listing_status'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'products' AND COLUMN_NAME = 'submitted_at'),
    'SELECT ''submitted_at already exists''',
    'ALTER TABLE products ADD COLUMN submitted_at DATETIME NULL COMMENT ''Date de soumission vendeur'' AFTER seller_id'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'products' AND COLUMN_NAME = 'reviewed_at'),
    'SELECT ''reviewed_at already exists''',
    'ALTER TABLE products ADD COLUMN reviewed_at DATETIME NULL COMMENT ''Date de decision admin'' AFTER submitted_at'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'products' AND COLUMN_NAME = 'reviewed_by_user_id'),
    'SELECT ''reviewed_by_user_id already exists''',
    'ALTER TABLE products ADD COLUMN reviewed_by_user_id INT NULL COMMENT ''Administrateur ayant valide/refuse'' AFTER reviewed_at'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'products' AND COLUMN_NAME = 'rejection_reason'),
    'SELECT ''rejection_reason already exists''',
    'ALTER TABLE products ADD COLUMN rejection_reason VARCHAR(512) NULL AFTER reviewed_by_user_id'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE products SET listing_status = 'APPROVED'
WHERE listing_status IS NULL OR TRIM(listing_status) = '';

ALTER TABLE products MODIFY COLUMN product_id INT NOT NULL AUTO_INCREMENT;

SET @sql := IF(
    EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'products' AND INDEX_NAME = 'idx_products_listing_status'),
    'SELECT ''idx_products_listing_status already exists''',
    'CREATE INDEX idx_products_listing_status ON products (listing_status)'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.STATISTICS WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'products' AND INDEX_NAME = 'idx_products_seller_id'),
    'SELECT ''idx_products_seller_id already exists''',
    'CREATE INDEX idx_products_seller_id ON products (seller_id)'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'products' AND CONSTRAINT_NAME = 'fk_products_seller'),
    'SELECT ''fk_products_seller already exists''',
    'ALTER TABLE products ADD CONSTRAINT fk_products_seller FOREIGN KEY (seller_id) REFERENCES `user` (id_user)'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF(
    EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA = @db_name AND TABLE_NAME = 'products' AND CONSTRAINT_NAME = 'fk_products_reviewer'),
    'SELECT ''fk_products_reviewer already exists''',
    'ALTER TABLE products ADD CONSTRAINT fk_products_reviewer FOREIGN KEY (reviewed_by_user_id) REFERENCES `user` (id_user)'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Optional role examples:
-- UPDATE `user` SET role = 'SELLER' WHERE id_user = 2;
-- UPDATE `user` SET role = 'ADMIN' WHERE id_user = 1;
