# Personne 1 - Utilisateurs, authentification et securite de compte

## Mission

Personne 1 gere l'identite dans ChriOnline. Cette partie permet de creer un compte, se connecter, verifier l'e-mail, modifier le profil, reinitialiser le mot de passe et supprimer un compte.

Le client JavaFX ne contacte jamais MySQL directement. Il envoie des messages socket au serveur. Le serveur utilise ensuite les classes `chrionline`, `services`, `persistence`, `common` et `server`.

## Parcours general

1. L'utilisateur remplit un formulaire dans JavaFX.
2. Le client envoie un `Message` socket : `LOGIN`, `REGISTER`, `PROFILE_UPDATE`, etc.
3. `server.RequestRouter` choisit le service.
4. Le service ouvre une connexion avec `BaseDonnees.getConnection()`.
5. `UserDAO` lit ou modifie la table `user`.
6. Le serveur renvoie un `Message` avec `SUCCESS` ou `ERROR`.

## Classes du package `chrionline`

### `User`

Role : modele Java d'un compte utilisateur. Une instance represente une ligne de la table `user`.

Donnees principales :

- `id_user` : identifiant unique.
- `username` : nom affiche dans l'application.
- `hash_password` : mot de passe hashe avec BCrypt ou ancien mot de passe legacy.
- `email` : adresse e-mail du compte.
- `phone_number` : numero de telephone stocke en entier.
- `date_creation` : date de creation.
- `role` : `CLIENT`, `SELLER` ou `ADMIN`.
- `emailVerified` : indique si l'e-mail est confirme.
- `adminPublicKeyPem` : cle publique RSA utilisee pour la connexion admin RSA.

Methodes importantes :

- `User()` : constructeur vide pour creer l'objet puis remplir ses champs.
- `User(...)` : constructeur complet utilise par le DAO.
- `get_id_user()`, `get_username()`, `get_email()`, `get_phone_number()` : donnent les informations de compte.
- `get_hash_password()` : retourne le hash stocke. Il ne faut pas l'afficher dans l'UI.
- `get_role()` : permet au serveur de savoir si l'utilisateur est client, vendeur ou admin.
- `isEmailVerified()` : controle les actions sensibles.
- `getAdminPublicKeyPem()` : recupere la cle RSA admin publique.
- `set_*` : modifient les champs et refusent les valeurs nulles pour les champs obligatoires.

Bibliotheques :

- `java.sql.Date` : stocke la date compatible SQL.

### `UserDAO`

Role : acces SQL a la table `user`. C'est la couche qui execute les `SELECT`, `INSERT`, `UPDATE` et `DELETE`.

Etat interne :

- `Connection connexion` : connexion JDBC fournie par le service.

Methodes importantes :

- `UserDAO(Connection connexion)` : construit le DAO avec une connexion existante.
- `getNextUserId()` : calcule `MAX(id_user) + 1` si la table n'utilise pas `AUTO_INCREMENT`.
- `createUser(User user)` : insere un nouveau compte.
- `findById(int id_user)` : trouve un compte par ID.
- `findByEmail(String email)` : trouve un compte par e-mail.
- `emailExists(String email)` : verifie si l'e-mail est deja utilise.
- `findByPhoneNumber(Integer phone_number)` : trouve un compte par telephone.
- `phoneNumberExists(Integer phone_number)` : verifie si le telephone existe deja.
- `updateUsername(...)` : modifie le nom affiche.
- `updatePassword(...)` : remplace le hash du mot de passe.
- `updateEmail(...)` : remplace l'adresse e-mail.
- `updatePhoneNumber(...)` : remplace le telephone.
- `deleteUser(...)` : supprime la ligne utilisateur.
- `updateEmailVerified(...)` : marque l'e-mail comme verifie ou non.
- `mapUser(...)` : transforme un `ResultSet` SQL en objet `User`.
- `readEmailVerified(...)` : lit `email_verified` sans casser les anciennes bases.

Bibliotheques :

- `java.sql.Connection` : connexion MySQL.
- `java.sql.PreparedStatement` : requetes SQL parametrees.
- `java.sql.ResultSet` : lecture des resultats.
- `java.sql.SQLException` : erreurs SQL.

### `Authentification`

Role : logique metier pour inscription et connexion.

Etat interne :

- `UserDAO userDAO` : DAO utilise pour lire/ecrire les comptes.

Methodes importantes :

- `Authentification(Connection connexion)` : prepare le DAO avec la connexion SQL.
- `register(User user)` : refuse l'inscription si l'e-mail ou le telephone existe deja, sinon cree le compte.
- `loginByEmailOrPhone(String emailOrPhone, String plainPassword)` : accepte soit un e-mail, soit un numero de telephone.
- `findUserForLogin(...)` : choisit `findByEmail` ou `findByPhoneNumber`.
- `buildSampleUser(...)` : cree un compte de test avec mot de passe hashe.

Detail important :

- Si un ancien mot de passe en clair existe encore en base, `PasswordHasher.needsRehash` permet de le migrer vers BCrypt apres une connexion reussie.

Bibliotheques :

- `java.sql.Connection` : connexion a MySQL.
- `java.sql.SQLException` : gestion des erreurs SQL.

### `BaseDonnees`

Role : point central de connexion MySQL.

Methodes importantes :

- `jdbcUrl()` : retourne l'URL JDBC active.
- `dbUser()` : retourne l'utilisateur MySQL.
- `getConnection()` : ouvre une connexion vers la base.
- `verifyConnection()` : teste MySQL avec une requete simple.
- `currentDatabaseName()` : affiche la base active, utile pour verifier phpMyAdmin.
- `describeProductsTable()` : diagnostic de la table `products`.

Configuration :

- `CHRIONLINE_JDBC_URL` peut remplacer l'URL par defaut.
- `CHRIONLINE_DB_USER` peut remplacer `root`.
- `CHRIONLINE_DB_PASSWORD` peut definir le mot de passe.

Bibliotheques :

- JDBC (`java.sql`) : API standard Java pour MySQL.
- MySQL Connector/J : pilote Maven qui rend JDBC compatible MySQL.

### `PhoneNumberLookup`

Role : normalise les numeros de telephone pour une colonne SQL de type `INT`.

Methodes importantes :

- `digitsOnly(String s)` : retire tout sauf les chiffres.
- `parseStoredPhoneInt(String digitsOnly)` : convertit le numero en `Integer`.

Pourquoi c'est utile :

- Un numero comme `+33 6...` ne rentre pas toujours directement dans un `INT`.
- La classe nettoie et garde une forme compatible avec la base existante.

Bibliotheques :

- `java.lang.Long`, `Integer` : parsing numerique.

### `TestConnexion`

Role : petite classe de test pour verifier que la connexion MySQL fonctionne.

Methode importante :

- `main(String[] args)` : lance le test en console.

## Classes du package `common`

### `Message`

Role : enveloppe standard du protocole socket.

Champs :

- `type` : nom de commande, par exemple `LOGIN`.
- `requestId` : identifiant de requete.
- `status` : `SUCCESS` ou `ERROR`.
- `payload` : contenu JSON ou texte.
- `errorCode` : code machine si erreur.

Methodes importantes :

- Constructeur vide : necessaire pour certains usages de serialisation.
- Constructeur complet : cree un message complet.
- Getters/setters : lisent et modifient les champs.
- `request(String type, String requestId, String payload)` : fabrique une requete client.

### `JsonUtil`

Role : convertit les messages et petits payloads JSON.

Methodes importantes :

- `toJson(Message m)` : transforme un `Message` en texte JSON.
- `fromJson(String json)` : reconstruit un `Message`.
- `toMap(String json)` : lit un objet JSON simple sous forme de `Map`.
- `toBinary(Object obj)` : serialise un objet Java.
- `fromBinary(byte[] data, Class<T> clazz)` : restaure un objet Java.

Limite :

- Ce n'est pas une bibliotheque JSON complete. Elle est adaptee aux payloads simples du projet.

### `PasswordHasher`

Role : securise les mots de passe.

Methodes importantes :

- `hash(String plainPassword)` : cree un hash BCrypt.
- `verify(String plainPassword, String stored)` : verifie un mot de passe.
- `needsRehash(String stored)` : indique si l'ancien stockage doit etre migre.

Bibliotheques :

- `org.mindrot.jbcrypt.BCrypt` : algorithme de hash de mot de passe avec sel et cout.

### `MaskingUtil`

Role : masque les informations sensibles avant affichage.

Methodes importantes :

- `maskEmail(String email)` : masque une adresse e-mail.
- `maskPhoneDigits(String digits)` : masque un numero de telephone.

### `ClientPrefs`

Role : stocke des preferences locales cote client.

Methodes importantes :

- `getString(...)`, `getInt(...)`, `getBoolean(...)` : lisent une preference.
- `putString(...)`, `putInt(...)`, `putBoolean(...)` : sauvegardent une preference.
- `remove(...)` : supprime une preference.
- `runQuietly(...)` : evite de casser l'UI si les preferences sont bloquees.

Bibliotheques :

- `java.util.prefs.Preferences` : stockage local fourni par Java.

### `ClientConfigLoader`

Role : charge la configuration du client JavaFX.

Methodes importantes :

- `userClientConfigPath()` : retourne le chemin du fichier utilisateur.
- `load()` : fusionne les proprietes par defaut et utilisateur.
- `isAllowLoopback(...)` : autorise ou non `127.0.0.1`.

### `ChrionlineLog`

Role : journalisation simple dans la console et dans le dossier utilisateur.

Methodes importantes :

- `dotChrionlineDir()` : dossier `~/.chrionline`.
- `serverLogPath()` : chemin du journal serveur.
- `info(...)`, `warn(...)`, `err(...)` : niveaux de log.
- `err(String message, Throwable t)` : log avec exception.

## Services de compte

### `AuthService`

Role : service socket pour `LOGIN` et `REGISTER`.

Methodes importantes :

- `login(Message request)` : verifie identifiant + mot de passe, cree un token de session et renvoie les donnees utilisateur.
- `register(Message request)` : valide les champs, hashe le mot de passe et cree le compte.

Dependances :

- `BaseDonnees`
- `Authentification`
- `UserDAO`
- `PasswordHasher`
- `SessionRegistry`
- `JsonUtil`

### `EmailVerificationService`

Role : verification d'adresse e-mail par code.

Methodes importantes :

- `scheduleInitialVerificationEmail(int userId)` : planifie l'envoi apres inscription.
- `send(Message request)` : envoie un code.
- `confirm(Message request)` : verifie le code et marque l'e-mail comme verifie.
- `clearPendingForUser(int userId)` : supprime les codes en attente.

### `PasswordResetService`

Role : mot de passe oublie.

Methodes importantes :

- `forgotPassword(Message request)` : genere un code par e-mail ou telephone.
- `resetPassword(Message request)` : verifie le code et remplace le mot de passe.
- `clearPendingForUser(int userId)` : nettoie les codes en memoire.

### `ProfileSecurityService`

Role : OTP de securite pour modifier les champs sensibles du profil.

Methodes importantes :

- `sendOtp(Message request)` : envoie le code de securite.
- `verifyAndConsumeProfileOtp(int userId, String code)` : verifie puis supprime le code.
- `clearPendingOtpForUser(int userId)` : nettoie les codes.

### `ProfileService`

Role : modification du profil utilisateur.

Methode importante :

- `updateProfile(Message request)` : modifie nom, e-mail, telephone ou mot de passe selon le payload.

### `AccountDeletionService`

Role : suppression complete d'un compte.

Methode importante :

- `deleteAccount(Message request)` : verifie le compte, le mot de passe et l'OTP si necessaire, puis supprime les donnees.

## Persistance liee au compte

### `AccountDeletionDAO`

Role : supprime toutes les donnees liees a un utilisateur dans une transaction.

Methode importante :

- `deleteAllForUser(Connection conn, int userId)` : supprime commandes, lignes, historique paiement, moyens sauvegardes et compte.

## Session serveur

### `SessionRegistry`

Role : garde les sessions connectees en memoire.

Methodes importantes :

- `issueToken(int userId)` : genere un token.
- `resolveUser(String token)` : retrouve l'utilisateur.
- `revoke(String token)` : deconnecte la session.

Bibliotheques :

- `ConcurrentHashMap` : stockage thread-safe.
- `UUID` : generation de tokens.

## Routage

### `RequestRouter`

Role : dirige chaque `Message` vers le bon service.

Exemples pour Personne 1 :

- `LOGIN` vers `AuthService.login`.
- `REGISTER` vers `AuthService.register`.
- `EMAIL_VERIFY_SEND` vers `EmailVerificationService.send`.
- `EMAIL_VERIFY_CONFIRM` vers `EmailVerificationService.confirm`.
- `PROFILE_UPDATE` vers `ProfileService.updateProfile`.
- `DELETE_ACCOUNT` vers `AccountDeletionService.deleteAccount`.

## Bibliotheques globales

- JDBC : acces MySQL.
- MySQL Connector/J : pilote MySQL.
- BCrypt : hash des mots de passe.
- Angus Mail : envoi SMTP.
- Java Socket : transport via serveur.
- Java Collections : maps, listes, caches OTP.

## Checklist de test

1. Demarrer MySQL.
2. Lancer `server.ServerMain`.
3. Creer un compte.
4. Se connecter.
5. Demander un code e-mail.
6. Modifier le profil.
7. Tester mot de passe oublie.
8. Tester suppression de compte.
