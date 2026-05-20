-- =============================================================================
-- Exemple : activer un compte ADMIN + enregistrer sa clé publique RSA (PEM).
-- Pré-requis : migration_admin_rsa_public_key.sql exécutée.
-- =============================================================================

USE chrionline;

-- 1) Mettre le rôle ADMIN (adapter l'id_user)
-- UPDATE `user` SET role='ADMIN' WHERE id_user=1;

-- 2) Enregistrer la clé publique (PEM X509). Remplacez <BASE64...> par le contenu généré.
-- UPDATE `user`
-- SET admin_public_key_pem = '-----BEGIN PUBLIC KEY-----\n<BASE64...>\n-----END PUBLIC KEY-----'
-- WHERE id_user=1;

-- Vérification :
-- SELECT id_user, email, role, (admin_public_key_pem IS NOT NULL) AS has_key FROM `user`;

