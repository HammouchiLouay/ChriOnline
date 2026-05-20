# Personne 1 — Gestion utilisateurs et authentification
## Rôles et responsabilités (cahier des charges)
| Thème | Détail |
|-------|--------|
| Inscription | Création de compte avec contrôle d'unicité (e-mail, téléphone). |
| Connexion / déconnexion | Connexion par e-mail + mot de passe via le serveur ; la déconnexion est côté client (effacement de la session UI, pas de message réseau dédié). |
| Rôles utilisateur | Champ `role` en base (ex. `CLIENT` par défaut à l'inscription, surcharge possible via JSON `role`). |
| Sécurité des mots de passe | Colonne `hash_password` : hachage BCrypt via `common.PasswordHasher` ; les anciennes valeurs en clair peuvent être vérifiées puis remplacées par un hash au prochain login (`needsRehash`). |
## Correspondance cahier des charges → code
| Spécification | Implémentation |
|---------------|----------------|
| Utilisateur | `chrionline.User` |
| AuthService | `services.AuthService` |
### Classes et services associés (à connaître)
| Composant | Rôle |
|-----------|------|
| `chrionline.UserDAO` | Accès JDBC à la table MySQL `` `user` ``. |
| `chrionline.Authentification` | Règles métier inscription / login au-dessus de `UserDAO` (BCrypt, `loginByEmailOrPhone`). |
| `chrionline.PhoneNumberLookup` | Normalisation téléphone : `digitsOnly`, `parseStoredPhoneInt`. |
| `chrionline.BaseDonnees` | Pool / connexion JDBC (`getConnection()`, `verifyConnection()` pour `PING`). |
| `services.EmailVerificationService` | Codes e-mail 6 chiffres (mémoire serveur, TTL ~15 min). |
| `services.PasswordResetService` | Réinitialisation par e-mail (compte vérifié) ou démo par téléphone (code en console). |
| `services.ProfileService` | Mise à jour profil après mot de passe actuel + OTP si changement sensible. |
| `services.ProfileSecurityService` | Envoi / validation OTP pour modifications sensibles du profil. |
| `services.AccountDeletionService` | Suppression de compte + données liées (`AccountDeletionDAO`). |
| `services.MailService` | Envoi des e-mails (config `email-config.properties`). |
### Base de données
- Table `` `user` `` : `id_user`, `username`, `email`, `phone_number`, `hash_password`, `date_creation`, `role`, `email_verified`.
- Les identifiants utilisateur sont générés par `UserDAO.getNextUserId()` (pas d'`AUTO_INCREMENT` sur `id_user` dans le schéma fourni).
- Schéma de référence : `sql/chrionline_schema_all.sql`.
### Messages réseau (`server.RequestRouter`)
| Type | Service | Rôle |
|------|---------|------|
| `LOGIN` | `AuthService.login` | Connexion. |
| `REGISTER` | `AuthService.register` | Inscription. |
| `FORGOT_PASSWORD` | `PasswordResetService.forgotPassword` | Demande de code (e-mail ou téléphone). |
| `RESET_PASSWORD` | `PasswordResetService.resetPassword` | Nouveau mot de passe + code. |
| `EMAIL_VERIFY_SEND` | `EmailVerificationService.send` | Renvoyer un code de vérification e-mail. |
| `EMAIL_VERIFY_CONFIRM` | `EmailVerificationService.confirm` | Valider le code → `email_verified = true`. |
| `PROFILE_OTP_SEND` | `ProfileSecurityService.sendOtp` | OTP pour changement MDP / e-mail / téléphone. |
| `UPDATE_PROFILE` | `ProfileService.updateProfile` | Mise à jour du profil. |
| `DELETE_ACCOUNT` | `AccountDeletionService.deleteAccount` | Suppression du compte. |
## `chrionline.User`
Modèle aligné sur une ligne de la table `` `user` ``.
### Constructeurs
| Méthode | Explication |
|---------|-------------|
| `User()` | Constructeur vide pour remplissage par setters / JDBC. |
| `User(id_user, username, hash_password, email, phone_number, date_creation, role, emailVerified)` | Tous les champs en une fois. |
### Accesseurs (getters)
| Méthode | Explication |
|---------|-------------|
| `get_id_user()` | Identifiant utilisateur. |
| `get_username()` | Nom d'affichage. |
| `get_hash_password()` | Valeur stockée (nom historique « hash » ; attendu BCrypt ou legacy en clair en migration). |
| `get_email()` | Adresse e-mail. |
| `get_phone_number()` | Numéro (stocké en `INT` dans le schéma). |
| `get_date_creation()` | Date SQL de création. |
| `get_role()` | Rôle (ex. `CLIENT`). |
| `isEmailVerified()` | Indique si l'e-mail a été confirmé. |
### Mutateurs (setters)
| Méthode | Explication |
|---------|-------------|
| `set_id_user(Integer)` | Définit l'id ; lève `IllegalArgumentException` si `null`. |
| `set_username(String)` | Idem si `null`. |
| `set_hash_password(String)` | Hash BCrypt (inscription / mise à jour) ; interdit `null`. |
| `set_email(String)` | Interdit `null`. |
| `set_phone_number(Integer)` | Interdit `null`. |
| `set_date_creation(Date)` | Interdit `null`. |
| `set_role(String)` | Interdit `null`. |
| `setEmailVerified(boolean)` | Met à jour le booléen de vérification (pas d'exception sur `null`). |
## `chrionline.UserDAO`
Couche JDBC sur `` `user` ``. Constructeur : `UserDAO(Connection connexion)`.
| Méthode | Explication |
|---------|-------------|
| `getNextUserId()` | `SELECT COALESCE(MAX(id_user),0)+1` — prochain id manuel. |
| `createUser(User)` | `INSERT` complet incluant `email_verified`. |
| `findById(int)` | Utilisateur par `id_user` ou `null`. |
| `findByEmail(String)` | Par e-mail. |
| `findByPhoneNumber(Integer)` | Par téléphone. |
| `emailExists(String)` | `true` si l'e-mail est déjà utilisé. |
| `phoneNumberExists(Integer)` | `true` si le téléphone existe. |
| `updateUsername(id_user, username)` | Met à jour le nom. |
| `updatePassword(id_user, hash_password)` | Met à jour le mot de passe (valeur telle quelle). |
| `updateEmail(id_user, email)` | Change l'e-mail. |
| `updatePhoneNumber(id_user, phone_number)` | Change le téléphone. |
| `updateEmailVerified(id_user, verified)` | Met `email_verified` à true/false. |
| `deleteUser(id_user)` | Supprime la ligne utilisateur. |
## `chrionline.Authentification`
Constructeur : `Authentification(Connection connexion)` — crée un `UserDAO` interne.
| Méthode | Explication |
|---------|-------------|
| `register(User user)` | Si e-mail ou téléphone existe → `false` ; sinon `createUser` → `true`. Le champ `hash_password` doit déjà contenir un hash BCrypt (fait par `AuthService.register` via `PasswordHasher.hash`). |
| `loginByEmailOrPhone(String emailOrPhone, String plainPassword)` | Si la chaîne contient `@` → recherche par e-mail ; sinon chiffres normalisés → `phone_number`. Vérifie avec `PasswordHasher.verify` ; si `needsRehash` (ancien stockage), met à jour le hash en base. Retourne `User` ou `null`. |
| `buildSampleUser(...)` | Fabrique statique d'un `User` pour tests / démo (hash BCrypt sur le mot de passe passé). |
## `chrionline.PhoneNumberLookup`
| Méthode | Explication |
|---------|-------------|
| `digitsOnly(String)` | Ne garde que les chiffres (saisie utilisateur variable). |
| `parseStoredPhoneInt(String)` | Interprète la chaîne comme entier stocké en colonne `phone_number`, ou `null` si invalide. |
## `common.PasswordHasher` (utilisé par l'auth)
| Méthode | Explication |
|---------|-------------|
| `hash(String plain)` | Produit une chaîne BCrypt pour `INSERT` / `UPDATE`. |
| `verify(String plain, String stored)` | Vérifie le mot de passe (BCrypt ou comparaison legacy si migration). |
| `needsRehash(String stored)` | Indique s'il faut remplacer le stockage par un hash BCrypt. |
## `services.AuthService`
Point d'entrée messages pour l'authentification de base. Utilise `JsonUtil.toMap` sur le payload.
| Méthode | Explication |
|---------|-------------|
| `login(Message request)` | Attend JSON avec `email`, `password`. Succès : payload JSON `userId`, `username`, `email`, `phone`, `emailVerified`. Erreurs : `EMPTY_PAYLOAD`, `MISSING_FIELDS`, `BAD_CREDENTIALS`, `DB_ERROR`. |
| `register(Message request)` | Attend `username`, `email`, `password`, `phone` ; `role` optionnel (défaut `CLIENT`). Crée l'utilisateur, planifie le premier e-mail de vérification. Erreurs : `MISSING_FIELDS`, `INVALID_PHONE`, `EMAIL_OR_PHONE_EXISTS`, `DB_ERROR`. |
Méthodes privées utiles à connaître : `esc(String)` (échappement JSON), `err(Message, code)`.
## `services.EmailVerificationService`
Codes stockés en mémoire (`ConcurrentHashMap` par `userId`), TTL ~15 minutes.
| Méthode | Explication |
|---------|-------------|
| `scheduleInitialVerificationEmail(int userId)` | Thread daemon : envoie le premier code après inscription si le compte n'est pas déjà vérifié. |
| `send(Message request)` | `EMAIL_VERIFY_SEND` : payload `{"userId":"…"}`. Envoie un code ou retourne `alreadyVerified`. |
| `confirm(Message request)` | `EMAIL_VERIFY_CONFIRM` : `userId` + `code` ; met à jour la base et retire le code pending. |
| `clearPendingForUser(int userId)` | Nettoie le code en mémoire (ex. après suppression de compte). |
## `services.PasswordResetService`
| Méthode | Explication |
|---------|-------------|
| `forgotPassword(Message request)` | E-mail ou téléphone. E-mail : exige `email_verified`, envoie code par `MailService`. Téléphone : affiche le code en console. Payload succès peut contenir `maskedEmail` ou `maskedPhone`. |
| `resetPassword(Message request)` | `code`, `newPassword` (min 4 car.), plus e-mail ou téléphone pour retrouver l'entrée pending ; met à jour `hash_password`. |
| `clearPendingForUser(int userId)` | Supprime les demandes de reset liées à cet utilisateur. |
## `services.ProfileSecurityService`
| Méthode | Explication |
|---------|-------------|
| `sendOtp(Message request)` | `PROFILE_OTP_SEND` avec `userId` ; exige e-mail vérifié ; envoie un code 6 chiffres. |
| `verifyAndConsumeProfileOtp(int userId, String code)` | Vérifie le code, le consomme (usage unique) ; utilisé par `ProfileService` / `AccountDeletionService`. |
| `clearPendingOtpForUser(int userId)` | Retire l'OTP en mémoire. |
## `services.ProfileService`
| Méthode | Explication |
|---------|-------------|
| `updateProfile(Message request)` | Champs : `userId`, `currentPassword` obligatoires ; optionnels : `newPassword`, `newEmail`, `newPhone`, `securityOtp`. Changements sensibles (MDP, e-mail, téléphone) exigent e-mail vérifié + OTP valide. Contrôles d'unicité e-mail / téléphone. Retourne un JSON profil mis à jour. |
## `services.AccountDeletionService`
| Méthode | Explication |
|---------|-------------|
| `deleteAccount(Message request)` | `userId`, `currentPassword` ; si e-mail vérifié → `securityOtp` obligatoire. Transaction JDBC via `AccountDeletionDAO`, puis nettoyage des maps OTP (reset, verify, profil). |
## Points d'attention
- Ne jamais journaliser les mots de passe en clair.
- Les codes (e-mail, reset, OTP profil) sont volatils : redémarrage serveur = perte des pending maps.
- Le client JavaFX (`ui.ChriOnlineClientApp`) conserve `userId` et champs affichés après `LOGIN` / `REGISTER` ; la déconnexion est locale.
