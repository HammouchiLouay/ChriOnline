# ChriOnline — Résumé Personne 1 : classes et rôle de chaque méthode

**Périmètre** : gestion des utilisateurs, authentification (socket + MySQL), e-mails (vérification / OTP / réinitialisation), suppression de compte, et support côté client JavaFX (session locale, préférences). Le serveur ouvre JDBC ; les clients passent par TCP (`ServerMain` / `ClientHandler`) et des messages JSON (`common.Message`).

**Impact base** : voir `Personne1-database-impact.txt`.

**Note** : les mots de passe sont gérés via **BCrypt** (`common.PasswordHasher` et `org.mindrot.jbcrypt.BCrypt`). Les anciennes lignes encore en clair peuvent être migrées automatiquement après une connexion réussie (`Authentification.loginByEmailOrPhone` + `needsRehash`).

---

## 1. Modèle et accès données — `chrionline.*`

### `chrionline.User`

Bean aligné sur la table MySQL `user`.

| Méthode | Rôle |
|--------|------|
| `User()` | Constructeur vide pour JDBC / formulaires. |
| `User(id_user, username, hash_password, email, phone_number, date_creation, role, emailVerified)` | Constructeur complet. |
| `get_id_user()` | Identifiant utilisateur. |
| `get_username()` | Nom d’affichage. |
| `get_hash_password()` | Secret stocké (BCrypt ou legacy). |
| `get_email()` | E-mail. |
| `get_phone_number()` | Téléphone (entier, schéma INT). |
| `get_date_creation()` | Date SQL de création. |
| `get_role()` | Rôle (ex. `CLIENT`). |
| `isEmailVerified()` | E-mail confirmé ou non. |
| `set_id_user` … `set_role` | Mutateurs ; lèvent `IllegalArgumentException` si valeur interdite (`null` où applicable). |
| `setEmailVerified(boolean)` | Met à jour le drapeau de vérification e-mail. |

### `chrionline.UserDAO`

Requêtes préparées sur `user`. Une instance = une `Connection`.

| Méthode | Rôle |
|--------|------|
| `UserDAO(Connection)` | Stocke la connexion pour les opérations suivantes. |
| `getNextUserId()` | `MAX(id_user)+1` (schéma sans AUTO_INCREMENT sur `id_user`). |
| `createUser(User)` | `INSERT` complet (dont `email_verified`). |
| `findById(int)` | Utilisateur par id ou `null`. |
| `findByEmail(String)` | Par e-mail. |
| `findByPhoneNumber(Integer)` | Par téléphone. |
| `emailExists(String)` | `true` si l’e-mail existe. |
| `phoneNumberExists(Integer)` | `true` si le téléphone existe. |
| `updateUsername` / `updatePassword` / `updateEmail` / `updatePhoneNumber` | Mises à jour ciblées. |
| `updateEmailVerified(int, boolean)` | Met à jour la colonne de vérification. |
| `deleteUser(Integer)` | Supprime la ligne utilisateur. |
| `mapUser(ResultSet)` *(private)* | Construit un `User` depuis une ligne SQL. |
| `readEmailVerified(ResultSet)` *(private)* | Lit `email_verified` ; défaut `false` si colonne absente. |

### `chrionline.Authentification`

Règles métier inscription / connexion (sans protocole socket).

| Méthode | Rôle |
|--------|------|
| `Authentification(Connection)` | Crée un `UserDAO` sur la même connexion. |
| `register(User)` | Refuse si e-mail ou téléphone déjà pris ; sinon `createUser` → `true`. |
| `loginByEmailOrPhone(String, String)` | Connexion par e-mail (`@`) ou par téléphone normalisé ; vérifie le mot de passe avec `PasswordHasher` ; si besoin, réécrit un hash BCrypt (`needsRehash`). Retourne `User` ou `null`. |
| `findUserForLogin(String)` *(private)* | Résout l’utilisateur à partir d’un identifiant e-mail ou chiffres téléphone. |
| `buildSampleUser(...)` *(static)* | Fabrique un `User` de démo avec hash BCrypt. |

### `chrionline.BaseDonnees`

Point d’entrée JDBC **côté serveur uniquement**.

| Méthode | Rôle |
|--------|------|
| `getConnection()` | Ouvre une connexion MySQL (URL / user / mot de passe via défaut ou variables `CHRIONLINE_*`). |
| `verifyConnection()` | `SELECT 1` pour santé BDD (ex. `PING`, démarrage serveur). |
| `jdbcUrl` / `dbUser` / `dbPassword` *(private)* | Lecture des paramètres d’environnement ou défauts. |

### `chrionline.PhoneNumberLookup`

Alignement saisie utilisateur ↔ colonne `phone_number` (INT).

| Méthode | Rôle |
|--------|------|
| `digitsOnly(String)` | Ne garde que les chiffres ; `null` → `""`. |
| `parseStoredPhoneInt(String)` | Interprète les chiffres comme entier stockable (gère dépassement `int`, +33 / 0033, repli sur 9 derniers chiffres). |

---

## 2. Utilitaires communs — `common.*`

### `common.PasswordHasher`

| Méthode | Rôle |
|--------|------|
| `hash(String)` | Produit un hash BCrypt (coût 12 rounds). |
| `verify(String, String)` | Vérifie le mot de passe (BCrypt ou égalité clair en migration). |
| `needsRehash(String)` | Indique si la valeur en base n’est pas encore un hash BCrypt. |
| `isBcryptHash(String)` *(private)* | Détecte le format `$2a$` / `$2b$` / `$2y$`. |

### `common.JsonUtil`

| Méthode | Rôle |
|--------|------|
| `toJson(Message)` | Sérialise un message protocole en JSON. |
| `fromJson(String)` | Parse une ligne JSON → `Message`. |
| `toMap(String)` | Parse un objet JSON **plat** (clés/valeurs chaînes) pour les payloads LOGIN, REGISTER, etc. |
| `splitTopLevel` *(private)* | Découpe par virgules hors accolades imbriquées. |
| `toBinary` / `fromBinary` | Sérialisation Java (hors périmètre strict auth ; utilisé ailleurs pour le catalogue). |

### `common.Message`

Enveloppe du protocole socket : `type`, `requestId`, `status`, `payload`, `errorCode`.

| Méthode | Rôle |
|--------|------|
| Constructeurs / getters / setters | Accès aux champs. |
| `request(type, requestId, payload)` *(static)* | Construit une requête sans statut d’erreur. |

### `common.MaskingUtil`

| Méthode | Rôle |
|--------|------|
| `maskEmail(String)` | Masque un e-mail pour affichage (mot de passe oublié). |
| `maskPhoneDigits(String)` | Masque un numéro en gardant des indices visuels. |

### `common.ClientPrefs`

Préférences locales (`java.util.prefs`) : session, dernier hôte, etc. Côté client, le nœud de session (ex. `ChriOnlineClientApp`) stocke notamment `sessionToken`, `userId`, `role`, `username`, `email`, `phone`, `emailVerified`.

| Méthode | Rôle |
|--------|------|
| `runQuietly(Runnable)` | Exécute sans faire échouer l’UI si prefs interdites. |
| `getString` / `getInt` / `getBoolean` + `put*` + `remove` | Lecture / écriture par nœud et clé. |

### `common.ClientConfigLoader`

| Méthode | Rôle |
|--------|------|
| `userClientConfigPath()` | Chemin `~/.chrionline/chrionline-client.properties`. |
| `load()` | Fusion classpath + fichier utilisateur. |
| `isAllowLoopback(Properties)` | Indique si connexion à `127.0.0.1` / `localhost` est autorisée. |

### `org.mindrot.jbcrypt.BCrypt` (vendu dans le projet)

API bcrypt bas niveau : `hashpw`, `gensalt`, `checkpw`. **Usage applicatif préféré** : `PasswordHasher` uniquement.

---

## 3. Services socket — `services.*`

### `services.AuthService`

| Méthode | Rôle |
|--------|------|
| `login(Message)` | Payload JSON : `email` (e-mail ou téléphone) + `password` → `Authentification.loginByEmailOrPhone` ; émission d’un **jeton de session** via `SessionRegistry.issueToken` ; réponse JSON avec `userId`, `username`, `email`, `phone`, `emailVerified`, **`role`**, **`sessionToken`**. |
| `register(Message)` | `username`, `email`, `password`, `phone` ; rôle optionnel (`CLIENT` par défaut) ; planifie le premier e-mail de vérification ; **jeton de session** + **`role`** dans la réponse SUCCESS (comme pour `LOGIN`). |
| `esc(String)` *(private)* | Échappement pour littéraux JSON. |
| `err(Message, code)` *(private)* | Construit une réponse `ERROR` avec code. |

### `services.MailConfigLoader`

| Méthode | Rôle |
|--------|------|
| `userConfigFilePath()` | Chemin du fichier e-mail utilisateur. |
| `load()` | Fusion : classpath → `~/.chrionline/email-config.properties` → variables `CHRIONLINE_*`. |
| `loadClasspath` / `loadUserHome` / `applyEnvironment` / `putEnv` *(private)* | Couches de fusion. |

### `services.MailService`

| Méthode | Rôle |
|--------|------|
| `reload()` | Recharge la configuration. |
| `isMailConfigured()` | Indique si Resend ou SMTP est utilisable. |
| `logMailDiagnostics()` | Journalise la config SMTP (sans mot de passe). |
| `sendPlain(to, subject, body)` | Envoie un message texte (ou copie console). |
| `sendEmailVerification` / `sendPasswordResetCode` / `sendProfileSecurityCode` | Modèles d’e-mails métier. |
| Méthodes privées | Resend HTTP, SMTP Jakarta, helpers `smtpUser`, `effectiveMailFrom`, etc. |

### `services.EmailVerificationService`

Codes 6 chiffres, TTL ~15 min, `ConcurrentHashMap` par `userId`.

| Méthode | Rôle |
|--------|------|
| `scheduleInitialVerificationEmail(int)` | Thread daemon après inscription. |
| `send(Message)` | `EMAIL_VERIFY_SEND` : renvoie un code ou `alreadyVerified`. |
| `confirm(Message)` | `EMAIL_VERIFY_CONFIRM` : valide le code et met à jour la base. |
| `clearPendingForUser(int)` | Nettoie les codes en mémoire (ex. suppression de compte). |
| `sendCodeForUser` / `issueCode` *(private)* | Génération et envoi du code. |

### `services.PasswordResetService`

| Méthode | Rôle |
|--------|------|
| `forgotPassword(Message)` | Par e-mail (compte vérifié) ou téléphone (code en console) ; indices masqués. |
| `resetPassword(Message)` | `code` + `newPassword` + identifiant e-mail ou téléphone. |
| `clearPendingForUser(int)` | Supprime les demandes liées à l’utilisateur. |
| `keyEmail` / `keyPhoneDigits` / `buildForgotPayload` / `esc` *(private)* | Clés de stockage et JSON de réponse. |

### `services.ProfileSecurityService`

| Méthode | Rôle |
|--------|------|
| `sendOtp(Message)` | `PROFILE_OTP_SEND` : envoie un OTP à l’e-mail vérifié. |
| `verifyAndConsumeProfileOtp(int, String)` | Vérifie et consomme un OTP (usage unique). |
| `clearPendingOtpForUser(int)` | Retire l’OTP en mémoire. |

### `services.ProfileService`

| Méthode | Rôle |
|--------|------|
| `updateProfile(Message)` | `userId` + `currentPassword` ; champs optionnels `newPassword`, `newEmail`, `newPhone`, `securityOtp` ; changements sensibles = e-mail vérifié + OTP valide. |

### `services.AccountDeletionService`

| Méthode | Rôle |
|--------|------|
| `deleteAccount(Message)` | Vérifie mot de passe (et OTP si e-mail vérifié) ; `AccountDeletionDAO.deleteAllForUser` en transaction ; nettoie les maps OTP. |

---

## 4. Persistance — `persistence.*`

### `persistence.AccountDeletionDAO`

| Méthode | Rôle |
|--------|------|
| `deleteAllForUser(Connection, int)` | Supprime dans l’ordre : historique paiement (optionnel), lignes de commande, commandes, méthodes enregistrées (optionnel), puis `user`. |
| `deleteOrderLinesForUser` *(private)* | DELETE joint sur `order_lines` / `orders`. |
| `deleteOptionalTable` *(private)* | Ignore l’erreur MySQL « table inexistante » (1146). |

---

## 5. Jetons de session — `server.SessionRegistry`

Registre en mémoire (processus serveur) : associe un **UUID** opaque à un `userId` après `LOGIN` / `REGISTER`. Utilisé pour invalider la session au `LOGOUT` et pour résoudre l’utilisateur à partir du jeton (ex. opérations protégées).

| Méthode | Rôle |
|--------|------|
| `issueToken(int userId)` | Crée un jeton, l’enregistre, le retourne dans le JSON de réponse auth. |
| `resolveUser(String token)` | Retourne l’`userId` associé au jeton ou `null` si inconnu / expiré côté logique métier. |
| `revoke(String token)` | Supprime le jeton (déconnexion). |

---

## 6. Routage serveur — `server`

### `server.RequestRouter`

| Méthode | Rôle |
|--------|------|
| `route(Message)` | Dispatche selon `type` (majuscules). **Personne 1 (auth / profil)** : `PING` → `BaseDonnees.verifyConnection` ; `LOGIN` / `REGISTER` ; `LOGOUT` → `SessionRegistry.revoke` ; `FORGOT_PASSWORD` / `RESET_PASSWORD` ; `EMAIL_VERIFY_*` ; `PROFILE_OTP_SEND` ; `UPDATE_PROFILE` ; `DELETE_ACCOUNT`. **Session** : `GET_COMMANDES` exige un `sessionToken` valide (`SessionRegistry.resolveUser`) — lien direct avec la sécurisation des commandes par utilisateur. *(Autres types : catalogue, produits, paiement, etc.)* |
| `error` / `success` *(private)* | Helpers de réponse. |

---

## 7. Client JavaFX et UI — `ui.*` (partie Personne 1)

### `ui.SocketApiClient`

Client TCP : envoi des `Message` JSON et lecture de la réponse ; parseurs légers sur les payloads texte.

| Méthode | Rôle |
|--------|------|
| `send(Message)` | Envoie une requête et lit une ligne JSON réponse (IOException si réseau). |
| `parseAuthUserId` / `parseAuthUsername` / `parseAuthEmail` / `parseAuthPhone` / `parseAuthEmailVerified` | Extraction des champs JSON après `LOGIN` / `REGISTER` / profil. |
| `parseAuthSessionToken(String)` | Lit le **jeton de session** renvoyé par le serveur. |
| `parseAuthRole(String)` | Lit le **rôle** (`CLIENT`, vendeur, admin, etc.) pour l’UI et la navigation. |
| `extractJsonStringValue` | Lit une valeur chaîne dans un petit objet JSON. |

### `ui.UiMessages`

| Méthode | Rôle |
|--------|------|
| `errorCode(String)` | Traduit un code serveur en message français (`BAD_CREDENTIALS`, `AUTH_REQUIRED`, `SESSION_INVALID`, `EMAIL_NOT_VERIFIED`, etc.). |
| `genericFailure()` / `networkFailure()` | Messages génériques d’échec ou de panne réseau. |

### `ui.ChriOnlineClientApp` (gestion utilisateurs / session)

Application JavaFX principale : connexion au serveur, **connexion compte depuis la barre latérale** (champs e-mail / mot de passe), persistance de session, rôle, pages Compte / Profil.

| Méthode / zone | Rôle |
|----------------|------|
| `start(Stage)` | Scène, chargement `ClientConfigLoader`, restauration session via `restorePersistedSession`. |
| `rebuildApiFromFields()` | Reconstruit l’URL du serveur et l’instance `SocketApiClient` après modification hôte/port. |
| `refreshSessionBanner()` | Bannière d’état (serveur joignable, session affichée). |
| `submitSidebarLogin()` | Construit le message `LOGIN`, envoie via `SocketApiClient`, appelle `applySession` si succès. |
| `openRegisterDialog()` | Inscription modale (`REGISTER`), `initOwner` sur la fenêtre principale. |
| `openForgotPasswordDialog()` | Flux `FORGOT_PASSWORD` / `RESET_PASSWORD`. |
| `applySession(String jsonPayload)` | Parse `userId`, token, rôle, profil ; met à jour l’UI connectée ; `persistSession` ; `updateSessionRoleNav`. |
| `persistSession()` / `clearPersistedSession()` | Écriture / suppression des clés de session dans `ClientPrefs`. |
| `restorePersistedSession()` | Au démarrage : rechargement local du token et du profil sans mot de passe. |
| `logoutAccount()` | Message `LOGOUT` avec jeton, `SessionRegistry` côté serveur invalidé ; nettoyage local. |
| `setAccountLoggedInUi(boolean)` | Affiche le bloc « connecté » ou le formulaire de connexion dans la sidebar. |
| `updateSessionRoleNav()` | Affiche ou masque les entrées de navigation selon le rôle (client / vendeur / admin). |
| `sendAccountEmailVerification` / `confirmAccountEmailVerification` | Vérification e-mail depuis la page Compte. |
| `sendProfileSecurityOtp` / `submitProfileUpdateFromPage` (ou équivalent) | OTP et mise à jour profil (`UPDATE_PROFILE`). |
| `confirmAndDeleteAccount()` | Suppression de compte (`DELETE_ACCOUNT`). |
| `buildSidebar` / pages Compte–Profil | Structure UI liée au compte (navigation, formulaires). |

---

## 8. Récapitulatif des messages réseau (Personne 1)

| Type | Rôle principal |
|------|----------------|
| `PING` | Vérifie MySQL (`BaseDonnees.verifyConnection`) |
| `LOGIN` | `AuthService.login` (+ jeton + rôle) |
| `REGISTER` | `AuthService.register` (+ jeton + rôle) |
| `LOGOUT` | Révoque le jeton (`SessionRegistry.revoke`) |
| `GET_COMMANDES` | Liste des commandes **pour l’utilisateur du jeton** (`SessionRegistry` + `CommandeService`) |
| `FORGOT_PASSWORD` | `PasswordResetService.forgotPassword` |
| `RESET_PASSWORD` | `PasswordResetService.resetPassword` |
| `EMAIL_VERIFY_SEND` | `EmailVerificationService.send` |
| `EMAIL_VERIFY_CONFIRM` | `EmailVerificationService.confirm` |
| `PROFILE_OTP_SEND` | `ProfileSecurityService.sendOtp` |
| `UPDATE_PROFILE` | `ProfileService.updateProfile` |
| `DELETE_ACCOUNT` | `AccountDeletionService.deleteAccount` |

---

*Document aligné sur le code actuel du dépôt (session par jeton, UI sidebar, rôles). Pour le détail ligne à ligne, voir aussi `Personne1-classes-et-methodes.txt` et `docs/equipe/PERSONNE-01-AUTH.md`.*
