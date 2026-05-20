# Personne 3 — Gestion panier
## Rôles et responsabilités (cahier des charges)
| Thème | Détail |
|-------|--------|
| Ajout au panier | Associer un produit (id catalogue) à une quantité. |
| Suppression / retrait | Diminuer quantité ou retirer une ligne (selon ce que l'UI expose). |
| Total du panier | Somme prix Ã— quantité pour toutes les lignes. |
| Lignes du panier | Une entrée = id produit + quantité ; nom et prix viennent du catalogue chargé. |
## Correspondance cahier des charges → code ChriOnline
Le cahier des charges nomme Panier, LignePanier, PanierService. Dans ce dépôt, il n'y a pas de classes Java serveur avec ces noms : le panier est entièrement côté client dans l'application JavaFX.
| Concept pédagogique | Implémentation réelle |
|---------------------|------------------------|
| Panier | `Map<String, Integer> cart` dans `ui.ChriOnlineClientApp` — clé = `id` produit (`String`), valeur = quantité (`Integer`). |
| LignePanier | Chaque couple `(productId → qty)` ; pas de classe dédiée : les détails affichés résolvent `Product` via `findProduct(id)`. |
| PanierService | Logique répartie dans des méthodes privées de `ChriOnlineClientApp` (voir tableau ci-dessous) — équivalent fonctionnel d'un service applicatif. |
### Persistance et schéma SQL
- Aucune table « panier » : le schéma (`sql/chrionline_schema_all.sql`) ne persiste pas le panier ; tout est mémoire vive dans le client jusqu'à création de commande.
- Fermeture de l'application → panier perdu (sauf évolution future : fichier local, serveur, etc.).
## « Panier » — champ et comportement (`ChriOnlineClientApp`)
| Élément | Explication |
|---------|-------------|
| `cart` | `LinkedHashMap` : ordre d'insertion conservé pour l'affichage du résumé. |
## Méthodes client équivalentes à un PanierService
Toutes dans `ui.ChriOnlineClientApp` (signatures simplifiées pour la doc).
| Méthode | Explication |
|---------|-------------|
| `addProductDetailToCart()` | Lit la quantité sur la fiche produit courante ; incrémente `cart` pour `currentDetailProduct.getId()` ; appelle `updateCartSummary()`. |
| `updateCartSummary()` | Si panier vide → libellé « Vide ». Sinon parcourt `cart`, résout chaque id avec `findProduct`, cumule articles et total USD (`price * qty`), met à jour `cartSummaryLabel` et le tableau de bord (`updateDashboardStats()`). |
| `findProduct(String id)` | Cherche d'abord dans `productById`, puis dans la liste `catalog` ; `null` si inconnu. |
| `buildProduitsPayload()` | Construit la chaîne `id:quantite;id:quantite;...` pour le serveur. Vide si panier vide. |
| `createCommandeFromCart()` | Vérifie le panier, envoie `CREATE_COMMANDE` avec JSON `userId` + `produits` (chaîne ci-dessus). En succès : vide le panier, rafraîchit l'UI. |
### Ajout depuis la grille produits
Dans la création de carte produit (`createProductCard`), le bouton d'ajout incrémente typiquement :
- `cart.put(p.getId(), cart.getOrDefault(p.getId(), 0) + 1)`  
puis `updateCartSummary()` et rafraîchissement de la grille.
### Suppression / vidage
- Après commande réussie : `cart.clear()` dans `createCommandeFromCart()`.
- Après suppression de compte (côté client) : `cart.clear()` dans le flux de reset session.
- Il peut n'y avoir pas de bouton « retirer une unité » sur toutes les vues : le modèle de données permet `put(id, q)` avec `q == 0` ou `remove(id)` si vous étendez l'UI.
## Contrat réseau vers les commandes
Le serveur ne reçoit pas un objet « Panier », mais :
1. `CREATE_COMMANDE` avec payload JSON, par exemple :  
   `{"userId":"1","produits":"83:2;84:1"}`  
2. `RequestRouter` délègue à `CommandeService.createCommandeAvecProduits`, qui parse la chaîne et enrichit chaque ligne avec nom et prix depuis `ProductRepository`.
Le total affiché dans le panier client est une vue ; les montants définitifs des lignes de commande sont figés côté serveur à partir du catalogue au moment de la création.
## Évolution possible (alignement pédagogique)
Pour se rapprocher de Panier / LignePanier / PanierService sans changer le protocole :
- Extraire une classe `Panier` avec `Map<String,Integer>` ou `List<LignePanier>` et méthodes `add`, `removeLine`, `totalPrice(Function<String,Product>)`.
- Déplacer la logique de `ChriOnlineClientApp` dans un `PanierService` injecté ou statique.
- Conserver `buildProduitsPayload()` ou équivalent pour produire la chaîne `id:qty;…`.
## Bibliothèques et dépendances utiles
| Dépendance | Rôle pour cette personne |
|------------|---------------------------|
| JavaFX | UI `ChriOnlineClientApp` : `Map` pour le panier, bindings, listes produits. |
| `common.JsonUtil` / `common.Message` | Envoi de `CREATE_COMMANDE` via `ui.SocketApiClient`. |
| Aucune table SQL | Le panier n'est pas persisté en base dans le schéma actuel. |
## Points d'attention
- Stock : le panier client ne vérifie pas toujours le stock serveur avant affichage ; la cohérence peut être renforcée lors du `CREATE_COMMANDE` ou par un message dédié.
- Recherche : filtrer les produits affichés n'altère pas le contenu du panier (ids restent valides si le produit existe encore en base).
