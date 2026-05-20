-- =============================================================================
-- ChriOnline — création de toutes les tables (schéma complet pour l’app Java).
-- Exécuter dans MySQL / MariaDB (phpMyAdmin : onglet SQL).
-- Encodage : utf8mb4 (accents, emojis dans les textes si besoin).
--
-- Ensuite, données catalogue : sql/chrionline_products_data.sql
--
-- Panier : uniquement en mémoire côté client JavaFX — pas de table cart.
-- Ancienne base avec cart : DROP TABLE IF EXISTS cart;
-- =============================================================================

CREATE DATABASE IF NOT EXISTS chrionline CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE chrionline;

SET NAMES utf8mb4;

-- `user` est un mot réservé MySQL → backticks obligatoires
CREATE TABLE IF NOT EXISTS `user` (
    id_user INT NOT NULL PRIMARY KEY,
    username VARCHAR(120) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone_number INT NOT NULL,
    hash_password VARCHAR(255) NOT NULL,
    date_creation DATE NOT NULL,
    role VARCHAR(50) NOT NULL,
    email_verified TINYINT(1) NOT NULL DEFAULT 0,
    admin_public_key_pem TEXT NULL COMMENT 'PEM X509 public key for ADMIN RSA challenge-response',
    UNIQUE KEY uk_user_email (email),
    UNIQUE KEY uk_user_phone (phone_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS products (
    product_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    sku VARCHAR(40) NOT NULL,
    nom_produit VARCHAR(255) NOT NULL,
    marque VARCHAR(120) NULL,
    categorie_source VARCHAR(100) NOT NULL,
    categorie_metier VARCHAR(120) NOT NULL,
    prix_usd DECIMAL(14, 2) NOT NULL,
    remise_pct DECIMAL(10, 5) NOT NULL DEFAULT 0,
    prix_net_usd DECIMAL(14, 2) NOT NULL,
    rating DECIMAL(4, 2) NOT NULL DEFAULT 0,
    stock INT NOT NULL DEFAULT 0,
    disponibilite VARCHAR(40) NULL,
    description TEXT NULL,
    image_principale VARCHAR(768) NULL,
    nb_images INT NULL DEFAULT 0,
    source_catalogue VARCHAR(512) NULL,
    listing_status VARCHAR(20) NOT NULL DEFAULT 'APPROVED'
        COMMENT 'PENDING / APPROVED / REJECTED',
    seller_id INT NULL,
    submitted_at DATETIME NULL,
    reviewed_at DATETIME NULL,
    reviewed_by_user_id INT NULL,
    rejection_reason VARCHAR(512) NULL,
    CONSTRAINT fk_products_seller FOREIGN KEY (seller_id) REFERENCES `user` (id_user),
    CONSTRAINT fk_products_reviewer FOREIGN KEY (reviewed_by_user_id) REFERENCES `user` (id_user)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_products_listing_status ON products (listing_status);
CREATE INDEX idx_products_seller_id ON products (seller_id);

CREATE TABLE IF NOT EXISTS orders (
    order_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_usd DECIMAL(14, 2) NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES `user` (id_user)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS order_lines (
    line_id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id INT NOT NULL,
    product_id INT NOT NULL,
    quantity INT NOT NULL,
    unit_price_usd DECIMAL(14, 2) NOT NULL,
    CONSTRAINT fk_ol_order FOREIGN KEY (order_id) REFERENCES orders (order_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS historique_paiement (
    id_historique INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    commande_id INT NOT NULL COMMENT 'orders.order_id',
    user_id INT NOT NULL,
    type_paiement VARCHAR(64) NOT NULL,
    id_paiement_simule VARCHAR(80) NOT NULL,
    statut VARCHAR(32) NOT NULL,
    montant_final DECIMAL(14, 2) NOT NULL,
    message_resume VARCHAR(512) NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_hist_orders FOREIGN KEY (commande_id) REFERENCES orders (order_id),
    CONSTRAINT fk_hist_user FOREIGN KEY (user_id) REFERENCES `user` (id_user)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS methode_paiement_enregistree (
    id_methode INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    type_paiement VARCHAR(64) NOT NULL,
    display_label VARCHAR(255) NOT NULL,
    template_json TEXT NOT NULL,
    created_at DATETIME NOT NULL,
    CONSTRAINT fk_methode_user FOREIGN KEY (user_id) REFERENCES `user` (id_user) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
