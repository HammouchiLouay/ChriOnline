# Personne 4 - Commandes, lignes de commande et statuts

## Mission

Personne 4 gere le cycle de vie des commandes :

- creation de commande
- lignes de commande
- calcul total
- sauvegarde MySQL
- historique par utilisateur
- validation
- annulation
- statut paye apres paiement

Cette partie commence quand le panier est envoye au serveur.

## Parcours general

1. Le client envoie `CREATE_COMMANDE`.
2. `RequestRouter` appelle `CommandeService`.
3. `CommandeService` construit un objet `Commande`.
4. Pour chaque produit, une `LigneCommande` est ajoutee.
5. `CommandeDAO.insert` sauvegarde `orders` et `order_lines`.
6. Le serveur renvoie la commande en JSON.

## Classes du package `models`

### `Commande`

Role : modele serveur d'une commande ChriOnline.

Donnees :

- `id` : identifiant commande.
- `userId` : proprietaire de la commande.
- `lignes` : liste de `LigneCommande`.
- `status` : etat de la commande.
- `dateCommandeMs` : date en millisecondes.

Constructeurs :

- `Commande()` : cree une commande vide.
- `Commande(int id, int userId)` : cree une commande pour un utilisateur avec statut initial.

Methodes importantes :

- `getId()`, `setId(int id)` : ID de commande.
- `getUserId()` : proprietaire.
- `getLignes()` : liste des lignes.
- `getStatus()`, `setStatus(String status)` : etat actuel.
- `getDateCommandeMs()`, `setDateCommandeMs(...)` : date.
- `ajouterLigne(LigneCommande ligne)` : ajoute un produit.
- `supprimerLigne(int produitId)` : retire un produit.
- `calculerTotal()` : somme les sous-totaux.
- `toJson()` : transforme la commande pour le client.
- `toString()` : affichage debug.

Statuts courants :

- `EN_ATTENTE`
- `VALIDE`
- `PAYEE`
- `ANNULEE`

Bibliotheques :

- `ArrayList` : stockage des lignes.
- `List` : abstraction de liste.

### `LigneCommande`

Role : represente un produit dans une commande.

Donnees :

- `produitId` : ID produit.
- `nom` : nom du produit au moment de la commande.
- `quantite` : quantite achetee.
- `prixUnitaire` : prix fixe au moment de la commande.

Constructeurs :

- `LigneCommande()` : constructeur vide.
- `LigneCommande(int produitId, String nom, int quantite, double prixUnitaire)` : ligne complete.

Methodes importantes :

- `getProduitId()` : ID produit.
- `getNom()` : nom fige.
- `getQuantite()` : quantite.
- `getPrixUnitaire()` : prix unitaire.
- `calculerSousTotal()` : `quantite * prixUnitaire`.
- `toJson()` : fragment JSON de ligne.
- `toString()` : affichage debug.

Bibliotheques :

- `Serializable` : permet certains transferts ou stockages objet.

## Classes de persistance

### `persistence.CommandeDAO`

Role : acces SQL aux commandes.

Tables concernees :

- `orders`
- `order_lines`

Methodes importantes :

- `insert(Connection conn, Commande cmd)` : insere la commande et ses lignes dans une transaction.
- `findById(int id)` : charge une commande par ID.
- `findById(Connection conn, int id)` : variante avec connexion existante.
- `findByUserId(int userId)` : liste toutes les commandes d'un utilisateur.
- `updateStatus(int orderId, String newStatus)` : change le statut.
- `valider(int orderId)` : valide une commande si elle est en attente.
- `annuler(int orderId)` : annule seulement si le statut le permet.

Methodes internes :

- `insertLignes(...)` : sauvegarde les lignes.
- `mapCommande(...)` : transforme une ligne SQL en `Commande`.
- `loadLignes(...)` : charge les lignes d'une commande.

Transaction :

- `insert` doit sauvegarder l'en-tete et les lignes ensemble.
- Si une ligne echoue, la commande ne doit pas rester incomplete.

Bibliotheques :

- JDBC : `Connection`, `PreparedStatement`, `ResultSet`.
- SQL transactions : `commit`, `rollback`.
- `ArrayList` : construction des listes.

## Service metier

### `services.CommandeService`

Role : logique metier des commandes.

Methodes importantes :

- `createCommande(int userId, double total)` : cree une commande simplifiee.
- `createCommandeAvecProduits(int userId, String produitsData)` : cree une commande depuis le panier.
- `validerCommande(int id)` : valide une commande.
- `annulerCommande(int id)` : annule une commande.
- `getCommandesByUser(int userId)` : historique utilisateur.
- `getCommandeById(int id)` : detail d'une commande.
- `updateCommandeStatus(int id, String status)` : utilise par le paiement pour passer en `PAYEE` ou `ANNULEE`.

Details de `createCommandeAvecProduits` :

1. Lit le format `id:qty;id:qty`.
2. Ignore les morceaux invalides.
3. Cherche chaque produit avec `ProductRepository.findById`.
4. Cree une `LigneCommande` avec le prix actuel du produit.
5. Calcule le total via `Commande.calculerTotal`.
6. Sauvegarde avec `CommandeDAO.insert`.

Dependances :

- `ProductRepository`
- `Product`
- `CommandeDAO`
- `BaseDonnees`

## Routage socket

### `server.RequestRouter`

Role : relie les messages socket aux methodes de commande.

Commandes concernees :

- `CREATE_COMMANDE` : creation.
- `GET_COMMANDES` : historique utilisateur.
- `VALIDER_COMMANDE` : validation.
- `ANNULER_COMMANDE` : annulation.

### `server.SessionRegistry`

Role : verifie que l'utilisateur connecte peut consulter ses commandes.

Methodes utiles :

- `resolveUser(String token)` : retrouve l'utilisateur depuis le token.

## Classes client impliquees

### `ui.SocketApiClient`

Role : lit les reponses de commandes dans JavaFX.

Records importants :

- `CommandeSummary` : resume pour liste simple.
- `OrderLineSnapshot` : ligne de commande cote UI.
- `CommandeFull` : commande complete avec lignes.

Methodes importantes :

- `parseCommandeSummaries(String jsonArray)` : lit une liste de commandes.
- `parseCommandesFull(String jsonArray)` : lit commandes + lignes.
- `summarizeCommandesPayload(String jsonArray)` : cree des lignes affichables.
- `parseCommandeId(String commandeJson)` : extrait l'ID apres creation.

## Bibliotheques globales

- JDBC : persistance commandes.
- MySQL Connector/J : pilote.
- Java Collections : lignes de commande.
- Java Socket : messages client/serveur.
- JSON interne : reponses vers JavaFX.

## Checklist de test

1. Ajouter des produits au panier.
2. Creer une commande.
3. Verifier `orders`.
4. Verifier `order_lines`.
5. Afficher l'historique.
6. Annuler une commande en attente.
7. Valider une commande.
8. Payer une commande et verifier le statut `PAYEE`.
