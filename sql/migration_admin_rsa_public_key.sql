-- =============================================================================
-- Migration: ajout de la clé publique RSA pour auth admin challenge-response.
-- =============================================================================

USE chrionline;

ALTER TABLE `user`
    ADD COLUMN IF NOT EXISTS admin_public_key_pem TEXT NULL
        COMMENT 'PEM X509 public key for ADMIN RSA challenge-response';

-- Exemple (à adapter) :
-- UPDATE `user`
-- SET role='ADMIN',
--     admin_public_key_pem='-----BEGIN PUBLIC KEY-----\n<BASE64>\n-----END PUBLIC KEY-----'
-- WHERE id_user=1;

