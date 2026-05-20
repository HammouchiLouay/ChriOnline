# Index documentation équipe ChriOnline

Cinq rôles : responsabilités, lien cahier des charges / code, classes dans `chrionline-server`.

Vue consolidée : `DOCUMENTATION-COMPLETE-PAR-PERSONNE.md`

Par personne :

| Personne | Rôle | Fichier |
|----------|------|---------|
| 1 | Auth / utilisateurs | equipe/PERSONNE-01-AUTH.md |
| 2 | Produits | equipe/PERSONNE-02-PRODUITS.md |
| 3 | Panier (client) | equipe/PERSONNE-03-PANIER.md |
| 4 | Commandes | equipe/PERSONNE-04-COMMANDES.md |
| 5 | Paiement | equipe/PERSONNE-05-PAIEMENT.md |

Architecture commune

- Transport : TCP, une ligne JSON par requête/réponse. `server.ClientHandler` lit une ligne, désérialise `common.Message`, appelle `server.RequestRouter.route`, renvoie la réponse.
- `common.Message` : champs type, requestId, status (SUCCESS/ERROR), payload, errorCode.
- `RequestRouter` : dispatch sur type vers AuthService, ProductService, CommandeService, SocketPaymentService, etc.

Bibliothèques (pom.xml)

- Java 17, MySQL Connector/J, JavaFX 21 (client), Angus Mail, TwelveMonkeys WebP, BCrypt vendored. Pas de Spring.

Messages réseau (extraits)

| Type | Service principal |
|------|-------------------|
| PING | BaseDonnees.verifyConnection |
| LOGIN / REGISTER | AuthService |
| FORGOT_PASSWORD / RESET_PASSWORD | PasswordResetService |
| EMAIL_VERIFY_* | EmailVerificationService |
| PROFILE_OTP_SEND | ProfileSecurityService |
| UPDATE_PROFILE | ProfileService |
| DELETE_ACCOUNT | AccountDeletionService |
| PRODUCT_* / STOCK_UPDATE | ProductService |
| CREATE_COMMANDE | CommandeService |
| VALIDER_COMMANDE / GET_COMMANDES / ANNULER_COMMANDE | CommandeService |
| SIMULATE_PAYMENT | SocketPaymentService |
| LIST_SAVED_PAYMENT_METHODS / DELETE_SAVED_PAYMENT_METHOD | SavedPaymentService |

Coordination : mots de passe BCrypt (PasswordHasher) ; commandes MySQL orders / order_lines ; panier uniquement client ; paiement simulé met à jour les statuts commande côté serveur.
