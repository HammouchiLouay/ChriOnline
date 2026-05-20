# Personne 5 — Paiement et intégration
## Rôles et responsabilités (cahier des charges)
| Thème | Détail |
|-------|--------|
| Paiement simulé | Aucune passerelle bancaire réelle ; règles de démo (fraude, types de paiement, coupons). |
| Confirmation | Après simulation, mise à jour du statut de commande ChriOnline (`models.Commande` en base) et journalisation. |
| Intégration | Relier le module métier `ecommerce.personne5` au protocole socket + persistance SQL annexe. |
## Correspondance cahier des charges → code
| Spécification | Implémentation |
|---------------|----------------|
| Paiement | `ecommerce.personne5.model.Paiement` |
| PaiementService | `ecommerce.personne5.service.PaiementService` |
### Pont réseau et services ChriOnline
| Composant | Rôle |
|-----------|------|
| `services.SocketPaymentService` | Entrée `SIMULATE_PAYMENT` : lit `models.Commande`, appelle `PaiementService`, met à jour MySQL, écrit l'historique, optionnellement enregistre un moyen de paiement masqué. |
| `services.SavedPaymentService` | Messages `LIST_SAVED_PAYMENT_METHODS`, `DELETE_SAVED_PAYMENT_METHOD`. |
| `persistence.PaymentHistoryDAO` | Insertion dans `historique_paiement` après chaque tentative. |
| `persistence.SavedPaymentMethodDAO` | CRUD sur `methode_paiement_enregistree` (templates JSON masqués). |
### Deux modèles « Commande »
- `models.Commande` : commande ChriOnline (id entier, statuts `EN_ATTENTE`, `VALIDE`, `PAYEE`, `ANNULEE`, …) — source de vérité pour l'app et la base `orders`.
- `ecommerce.personne5.model.Commande` : modèle du module SSI (id `String`, `StatutCommande` enum) — utilisé uniquement pour la simulation ; le total est copié depuis `serverCmd.calculerTotal()`.
## `ecommerce.personne5.model.Paiement`
Représente le résultat d'une simulation : montants, statut, type, message, token, score fraude.
### Constructeur
| Méthode | Explication |
|---------|-------------|
| `Paiement(idPaiement, montantInitial, fraisLivraison, reductionCoupon, montantFinal, datePaiement, statut, typePaiement, idCommande, message, paiementSecurise, tokenPaiement, scoreFraude)` | Tous les champs initialisés d'un coup. |
### Méthodes
| Méthode | Explication |
|---------|-------------|
| `traiterPaiement()` | Démo : affiche un message console « Traitement en cours ». |
| `getIdPaiement()` | Identifiant unique simulé. |
| `getMontantInitial()` | Montant de départ (total commande côté eco). |
| `getFraisLivraison()` | Frais ajoutés selon `BusinessRules`. |
| `getReductionCoupon()` | Part déduite par coupon pourcentage. |
| `getMontantFinal()` | Montant TTC simulé (après promo, coupon, frais). |
| `getDatePaiement()` | Horodatage texte. |
| `getStatut()` | `StatutPaiement` (`ACCEPTE`, `REFUSE`, `EN_ATTENTE`, `REMBOURSE`, …). |
| `getTypePaiement()` | `TypePaiement` utilisé pour le switch métier. |
| `getIdCommande()` | Référence texte à la commande (id serveur en string). |
| `getMessage()` | Libellé explicatif (succès / refus). |
| `isPaiementSecurise()` | Résultat de la vérification simulée. |
| `getTokenPaiement()` | Jeton fictif. |
| `getScoreFraude()` | Score entier (comparé à des seuils dans `BusinessRules`). |
| `setStatut(StatutPaiement)` | Mutation (ex. remboursement). |
| `setMessage(String)` | Met à jour le message. |
| `setMontantFinal(double)` | Ajustement du montant final si besoin. |
| `toString()` | Debug. |
## `ecommerce.personne5.service.PaiementService`
Moteur métier hors socket. Constructeur : initialise `PromotionService`, `PaiementStats`, `AuditService`, `PaiementQueueService`, `FraudDetectionService`, `WalletService`, liste `historiquePaiements`.
| Méthode | Explication |
|---------|-------------|
| `getStats()` | Accès aux statistiques agrégées (`PaiementStats`). |
| `getAuditService()` | Journal d'audit du module. |
| `getQueueService()` | File d'attente des paiements simulés. |
| `verifierCoupon(String code)` | Retourne un `Coupon` pour `PROMO10`, `PROMO20`, `PROMO30` (majuscules), sinon `null`. |
| `calculerFraisLivraison(double montantApresReduction)` | 0 si montant â‰¥ seuil livraison gratuite, sinon frais standard (`BusinessRules`). |
| `calculerMontantParTranche(Paiement paiement, int n)` | `montantFinal / n` pour affichage 2Ã— / 3Ã— (garde-fous si `n <= 0`). |
| `simulerPaiement(Commande eco, TypePaiement type, Coupon coupon, Wallet wallet)` | Enchaîne : promotion globale, coupon %, frais, flag sécurité, token, score fraude ; `switch` sur le type (`WALLET`, `PAIEMENT_2X`, `PAIEMENT_3X`, `A_LA_LIVRAISON`, `CARTE_BANCAIRE`, `PAYPAL`, `STRIPE`, …). Construit un `Paiement`, met à jour stats, queue, audit. Retourne `null` si commande absente. |
| `afficherDetailsPaiement(Paiement)` | Affichage console détaillé (montants en MAD dans les `printf`). |
| `afficherRecu(Paiement)` | Utilise `RecuGenerator.genererRecu`. |
| `afficherHistorique()` | Liste console des paiements simulés en mémoire dans le service. |
| `afficherStats()` | Affiche `PaiementStats`. |
| `confirmerPaiement(Paiement, Commande eco)` | Si `ACCEPTE` : statut eco `PAYEE` ou `VALIDEE` (cas livraison) ; sinon `ANNULEE`. Notifications + audit. |
| `rembourserPaiement(Paiement, Commande eco)` | Si paiement accepté : passe à `REMBOURSE`, commande eco `REMBOURSEE`, stats, audit ; sinon `false`. |
Note : `SocketPaymentService` appelle `simulerPaiement` puis `confirmerPaiement` dans un bloc `synchronized` pour limiter les courses ; le wallet est passé `null` depuis le socket (le chemin `WALLET` refusera sans objet).
## `services.SocketPaymentService`
| Méthode | Explication |
|---------|-------------|
| `simulatePayment(Message request)` | Payload JSON : `commandeId`, `userId` (obligatoires), `typePaiement` (défaut `CARTE_BANCAIRE`), `coupon` optionnel, `saveTemplate` + champs template (`holderName`, `lastFour`, `brand`, `expMonth`, `expYear`, `paypalCode` / `paypalEmail`, `walletAlias`). Charge `CommandeService.getCommandeById` ; vérifie utilisateur et statut (`PAYEE` / `ANNULEE` interdits). Construit `ecommerce.personne5.model.Commande`, exécute paiement + confirmation, `PaymentHistoryDAO.insert`, met à jour statut commande `PAYEE` ou `ANNULEE`. Si succès + `saveTemplate`, appelle `maybeSavePaymentTemplate` → `SavedPaymentMethodDAO.insert`. Retour JSON : `idPaiement`, `idCommande`, `statut`, `montantFinal`, `scoreFraude`, `message`. |
Méthodes privées utiles : `parseType`, `parseBool`, `toJsonPayload`, `maybeSavePaymentTemplate`, `paypalCodeFrom`, `digitsOnly`, `escJson`, `err`.
### Codes d'erreur fréquents
`EMPTY_PAYLOAD`, `MISSING_COMMANDE_ID`, `MISSING_USERID`, `COMMANDE_NOT_FOUND`, `FORBIDDEN`, `COMMANDE_NOT_PAYABLE`, `PAYMENT_FAILED`, `INVALID_PAYLOAD`.
## `services.SavedPaymentService`
| Méthode | Explication |
|---------|-------------|
| `list(Message request)` | Payload texte = `userId` ; retourne un tableau JSON (via `SavedPaymentMethodDAO.toJsonArray`). |
| `delete(Message request)` | JSON `userId`, `idMethode` ; supprime la ligne si elle appartient à l'utilisateur. |
## `persistence.PaymentHistoryDAO`
| Méthode | Explication |
|---------|-------------|
| `insert(commandeId, userId, typePaiement, idPaiementSimule, statut, montantFinal, messageResume)` | `INSERT` dans `historique_paiement` avec `created_at` = maintenant. Tronque les chaînes trop longues. |
## `persistence.SavedPaymentMethodDAO`
| Type | Explication |
|------|-------------|
| `SavedRow` (record) | `id`, `typePaiement`, `displayLabel`, `createdAtIso`. |
| Méthode | Explication |
|---------|-------------|
| `insert(userId, typePaiement, displayLabel, templateJson)` | Enregistre un gabarit masqué (pas de PAN complet). |
| `listByUser(userId)` | Liste des méthodes pour l'utilisateur. |
| `deleteForUser(methodId, userId)` | Suppression sécurisée par couple id / user. |
| `toJsonArray(List<SavedRow>)` | Sérialisation pour le client. |
## Base de données (paiement)
- `historique_paiement` : trace chaque simulation (commande, user, type, id simulé, statut, montant, message, date).
- `methode_paiement_enregistree` : libellés + JSON template pour rappel UI (carte masquée, PayPal, wallet).
Schéma : `sql/chrionline_schema_all.sql`.
## Scénario d'intégration bout-en-bout
1. Authentification, chargement catalogue, remplissage panier client.
2. `CREATE_COMMANDE` → réponse JSON avec `id` de commande.
3. `SIMULATE_PAYMENT` avec `commandeId`, `userId`, `typePaiement` (+ coupon / sauvegarde optionnelle).
4. `GET_COMMANDES` ou onglet Commandes : vérifier `PAYEE` ou `ANNULEE`.
5. Optionnel : `LIST_SAVED_PAYMENT_METHODS` pour afficher les templates enregistrés.
## Bibliothèques et dépendances utiles
| Dépendance | Rôle pour cette personne |
|------------|---------------------------|
| MySQL Connector/J | `PaymentHistoryDAO`, `SavedPaymentMethodDAO`, mise à jour `orders` via `CommandeDAO`. |
| Module `ecommerce.personne5` | Logique métier simulation (promotions, file, fraude, wallet, audit) — sans framework injecté. |
| `java.util` / concurrence | `SocketPaymentService` synchronise un verrou `PAY_LOCK` autour de la simulation pour éviter les courses. |
Console autonome : `ecommerce.personne5.main.PaiementApp` (hors socket, démo du module).
## Points d'attention
- Les libellés MAD dans les affichages console du module personne5 sont cosmétiques ; les commandes ChriOnline restent en USD côté `orders.total_usd`.
- `TypePaiement` doit correspondre à un nom d'`enum` valide ; sinon le parseur socket retombe sur `CARTE_BANCAIRE`.
- Cohérence des statuts `models.Commande` avec l'interface JavaFX (couleurs, bouton « Payer », etc.).
