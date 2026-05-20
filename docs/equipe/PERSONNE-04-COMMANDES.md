# Personne 4 — Gestion commandes
## Rôles et responsabilités (cahier des charges)
| Thème | Détail |
|-------|--------|
| Création | Transformer le contenu du panier (côté client) en commande avec lignes détaillées. |
| Validation | Passer une commande de `EN_ATTENTE` à `VALIDE` (règles SQL). |
| Historique | Lister les commandes d'un utilisateur avec leurs lignes. |
| Annulation | Annuler si le statut le permet (pas déjà `VALIDE` ou `PAYEE`). |
## Correspondance cahier des charges → code
| Spécification | Implémentation |
|---------------|----------------|
| Commande | `models.Commande` |
| LigneCommande | `models.LigneCommande` |
| CommandeService | `services.CommandeService` |
### Persistance (important)
Les commandes sont enregistrées en MySQL via `persistence.CommandeDAO` :
- Table `orders` : `order_id`, `user_id`, `status`, `total_usd`, `created_at`.
- Table `order_lines` : `line_id`, `order_id`, `product_id`, `quantity`, `unit_price_usd`.
Ce n'est plus un stockage uniquement en mémoire : l'historique survit au redémarrage du serveur tant que la base est conservée.
### Messages réseau (`RequestRouter`)
| Type | Rôle |
|------|------|
| `CREATE_COMMANDE` | Crée une commande (avec lignes produits ou total global simplifié). |
| `VALIDER_COMMANDE` | Passe `EN_ATTENTE` → `VALIDE`. |
| `GET_COMMANDES` | Liste JSON des commandes : payload JSON `{"sessionToken":"…"}` → utilisateur issu du jeton. |
| `ANNULER_COMMANDE` | Payload JSON `{"sessionToken":"…","commandeId":"…"}` : la commande doit appartenir à l'utilisateur ; met `ANNULEE` si le statut n'est ni `VALIDE` ni `PAYEE`. |
## `models.LigneCommande`
`Serializable` pour usage éventuel sur le fil réseau ; les lignes sont surtout construites côté serveur et sérialisées en JSON via `Commande.toJson()`.
### Constructeurs
| Méthode | Explication |
|---------|-------------|
| `LigneCommande()` | Vide (beans). |
| `LigneCommande(produitId, nom, quantite, prixUnitaire)` | Ligne complète figée au moment de la commande. |
### Méthodes
| Méthode | Explication |
|---------|-------------|
| `getProduitId()` | Identifiant produit catalogue (`product_id`). |
| `getNom()` | Libellé affiché (nom produit au moment de la commande). |
| `getQuantite()` | Quantité commandée. |
| `getPrixUnitaire()` | Prix unitaire figé (USD). |
| `calculerSousTotal()` | `quantite * prixUnitaire`. |
| `toJson()` | Fragment JSON pour une ligne (échappement des guillemets dans `nom`). |
| `toString()` | Debug. |
*Pas de setters publics* dans la version actuelle : immutabilité logique après construction.
## `models.Commande`
Agrégat : identifiant, utilisateur, lignes, statut, horodatage.
### Constructeurs
| Méthode | Explication |
|---------|-------------|
| `Commande()` | Id 0, liste vide, `dateCommandeMs` = maintenant. |
| `Commande(int id, int userId)` | Fixe `id` et `userId`, statut initial `EN_ATTENTE`, timestamp courant. |
### Méthodes
| Méthode | Explication |
|---------|-------------|
| `getId()` / `setId(int)` | Identifiant commande (assigné après insert SQL). |
| `getUserId()` | Propriétaire de la commande (pas de setter : défini au constructeur). |
| `getLignes()` | Liste mutable des `LigneCommande`. |
| `getStatus()` / `setStatus(String)` | Ex. `EN_ATTENTE`, `VALIDE`, `ANNULEE`, `PAYEE`. |
| `getDateCommandeMs()` / `setDateCommandeMs(long)` | Millisecondes depuis epoch (affichage historique). |
| `ajouterLigne(LigneCommande)` | Ajoute une ligne. |
| `supprimerLigne(int produitId)` | Retire la ligne dont `produitId` correspond. |
| `calculerTotal()` | Somme des `calculerSousTotal()` de toutes les lignes. |
| `toJson()` | JSON : `id`, `userId`, `status`, `total`, `dateCommande`, tableau `lignes`. |
| `toString()` | Debug. |
## `services.CommandeService`
Façade métier + JDBC. Toutes les méthodes statiques peuvent propager `SQLException`.
| Méthode | Explication |
|---------|-------------|
| `createCommande(int userId, double total)` | Cas simplifié : une seule ligne synthétique « Commande globale », quantité 1, `prixUnitaire = total`. Insère via `CommandeDAO.insert`, retourne la commande avec `id` généré. |
| `createCommandeAvecProduits(int userId, String produitsData)` | Parse `produitsData` au format `id:qty;id:qty`. Pour chaque id, `ProductRepository.findById` fournit nom et prix ; sinon valeurs par défaut. Puis `CommandeDAO.insert`. |
| `validerCommande(int id)` | Délègue à `CommandeDAO.valider` (`EN_ATTENTE` → `VALIDE`). |
| `annulerCommande(int id)` | `CommandeDAO.annuler` (refus si déjà `VALIDE` ou `PAYEE`). |
| `getCommandesByUser(int userId)` | `CommandeDAO.findByUserId` avec lignes rechargées (jointure optionnelle sur `products` pour le nom). |
| `getCommandeById(int id)` | `CommandeDAO.findById` (utilisé par le paiement). |
| `updateCommandeStatus(int id, String status)` | `CommandeDAO.updateStatus` (ex. `PAYEE`, `ANNULEE` après simulation de paiement). |
## `persistence.CommandeDAO`
| Méthode | Explication |
|---------|-------------|
| `insert(Connection conn, Commande cmd)` | Transaction : insert dans `orders` (total = `cmd.calculerTotal()`, timestamp depuis `dateCommandeMs`), récupère `order_id` auto, batch insert des lignes dans `order_lines`, `commit`. |
| `findById(int id)` | Ouvre une connexion, charge l'en-tête + lignes (`loadLignes` avec `LEFT JOIN products` pour le nom). |
| `findById(Connection conn, int id)` | Même logique sur une connexion fournie. |
| `findByUserId(int userId)` | Toutes les commandes de l'utilisateur, tri décroissant par `order_id`, avec lignes. |
| `updateStatus(int orderId, String newStatus)` | `UPDATE orders SET status`. |
| `valider(int orderId)` | `UPDATE` seulement si `status = 'EN_ATTENTE'`. |
| `annuler(int orderId)` | `UPDATE` vers `ANNULEE` si statut non dans (`VALIDE`, `PAYEE`). |
Méthodes privées : `insertLignes`, `mapCommande`, `loadLignes`.
## Payload `CREATE_COMMANDE` (rappel)
- Avec détail panier :  
  `{"userId":"<id>","produits":"83:2;84:1"}`  
- Simplifié :  
  `{"userId":"<id>","total":"<montant>"}`  
Le routeur valide les champs et parse les entiers / décimaux ; erreurs possibles : `EMPTY_PAYLOAD`, `INVALID_JSON`, `MISSING_USERID`, `MISSING_FIELDS`, `DB_ERROR`.
## Bibliothèques et dépendances utiles
| Dépendance | Rôle pour cette personne |
|------------|---------------------------|
| MySQL Connector/J | JDBC : `CommandeDAO` écrit dans `orders` et `order_lines`. |
| `java.sql` / JDBC | `PreparedStatement`, `ResultSet`, transactions dans `insert`. |
| `models.Commande` / `LigneCommande` | Sérialisation JSON pour `GET_COMMANDES` (pas de Hibernate : SQL direct). |
## Points d'attention
- Le paiement (`SocketPaymentService`) met à jour le statut vers `PAYEE` ou `ANNULEE` : l'UI doit rester alignée sur ces libellés.
- Le total persisté dans `orders.total_usd` correspond à la somme des lignes au moment de l'insertion.
- Les clés étrangères : `orders.user_id` → `` `user` `` ; `order_lines.product_id` référence logiquement `products` (selon votre schéma, la contrainte peut être ajoutée ou absente).
