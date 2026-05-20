-- =============================================================================
-- Exemple : attribuer les rôles SELLER et ADMIN après création des comptes.
-- Adaptez id_user à votre table `user` (phpMyAdmin → parcourir `user`).
-- =============================================================================

USE chrionline;

-- Exemple : l’utilisateur 2 devient vendeur
-- UPDATE `user` SET role = 'SELLER' WHERE id_user = 2;

-- Exemple : l’utilisateur 1 devient administrateur (modération catalogue)
-- UPDATE `user` SET role = 'ADMIN' WHERE id_user = 1;

-- Vérification :
-- SELECT id_user, username, email, role FROM `user`;
