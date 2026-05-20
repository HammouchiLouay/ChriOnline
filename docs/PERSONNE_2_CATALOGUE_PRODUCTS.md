# Personne 2 - Catalogue, produits, stock et moderation

## Mission

Personne 2 gere tout ce qui concerne les produits :

- affichage du catalogue
- categories
- details produit
- stock
- fiches vendeur
- moderation admin
- creation directe de produits par l'admin

Le serveur lit la table `products`. Le client recoit seulement les donnees via socket.

## Parcours general

1. Le client demande `PRODUCT_LIST`, `PRODUCT_CATEGORIES` ou `PRODUCT_DETAILS`.
2. `RequestRouter` appelle `ProductService`.
3. `ProductService` appelle `ProductRepository`.
4. `ProductRepository` utilise `ProductCatalogDAO`.
5. Le resultat est renvoye au client.

Pour vendeur/admin :

1. Le vendeur soumet une fiche.
2. L'admin la voit dans la moderation.
3. L'admin approuve ou refuse.
4. Les produits approuves deviennent visibles dans le catalogue.

## Classes du package `product`

### `Product`

Role : modele d'un produit visible dans le catalogue.

Donnees :

- `id` : identifiant produit sous forme de texte.
- `name` : nom du produit.
- `description` : description.
- `price` : prix.
- `stock` : quantite disponible.
- `imageUrl` : URL de l'image principale.
- `category` : categorie metier.
- `brand` : marque.
- `rating` : note.

Methodes importantes :

- `Product()` : constructeur vide.
- `Product(id, name, description, price, stock)` : constructeur historique.
- `Product(id, name, description, price, stock, imageUrl, category, brand, rating)` : constructeur complet.
- `getId()`, `getName()`, `getDescription()` : lecture pour l'affichage.
- `getPrice()`, `getStock()` : prix et disponibilite.
- `setStock(int stock)` : mise a jour locale.
- `getImageUrl()`, `setImageUrl(...)` : image produit.
- `getCategory()`, `setCategory(...)` : categorie.
- `getBrand()`, `setBrand(...)` : marque.
- `getRating()`, `setRating(...)` : note.

Bibliotheques :

- `java.io.Serializable` : permet l'envoi binaire de listes de produits.

### `ProductCatalogDAO`

Role : classe SQL principale pour la table `products`.

Responsabilites :

- lire les produits publics
- filtrer par categorie
- inserer les fiches vendeur
- approuver/refuser les fiches
- creer des produits admin
- mettre a jour le stock
- supprimer un produit
- detecter les colonnes disponibles dans une base migree partiellement

Methodes de lecture catalogue :

- `loadAll()` : charge tous les produits approuves.
- `loadByCategory(String category)` : charge les produits d'une categorie.
- `loadPage(int offset, int limit)` : pagination sans categorie.
- `loadPageByCategory(String category, int offset, int limit)` : pagination par categorie.
- `findByProductId(int productId)` : detail d'un seul produit.
- `distinctCategoriesApproved()` : categories des produits visibles.

Methodes vendeur/moderation :

- `insertPendingListing(...)` : insere une fiche `PENDING`.
- `listPending()` : liste les fiches en attente.
- `listBySellerId(int sellerId)` : liste les fiches d'un vendeur.
- `approve(int productId, int adminUserId)` : passe une fiche en `APPROVED`.
- `reject(int productId, int adminUserId, String reason)` : passe une fiche en `REJECTED`.

Methodes admin catalogue :

- `updateStock(int productId, int newStock)` : modifie le stock et la disponibilite.
- `insertAdminDirectApproved(...)` : cree un produit directement publie.
- `deleteProductById(int productId)` : supprime une ligne produit.
- `listAllForAdmin()` : liste tous les produits.
- `listAllForAdmin(String search)` : recherche par ID, nom, SKU, marque ou categorie.
- `distinctCategoriesForAdmin()` : categories proposees a l'admin.
- `defaultAdminCategories()` : categories par defaut.
- `isKnownAdminCategory(String category)` : valide une categorie controlee.

Methodes internes importantes :

- `loadShape(...)` : inspecte les colonnes existantes.
- `mapRow(...)` : transforme une ligne SQL en `Product`.
- `mapListingInfo(...)` : transforme une ligne SQL en fiche de moderation.
- `normalizeAdminCategory(...)` : nettoie une categorie admin.

Bibliotheques :

- JDBC : `Connection`, `PreparedStatement`, `ResultSet`, `SQLException`.
- `BigDecimal` : precision des prix SQL.
- `LinkedHashSet` : categories sans doublons en gardant l'ordre.

### `ProductRepository`

Role : facade entre les services et le DAO.

Pourquoi cette classe existe :

- Elle cache les details SQL.
- Elle fournit un fallback en memoire si MySQL est indisponible.
- Elle donne une API simple au reste du serveur.

Methodes importantes :

- `getAll()` : retourne tous les produits.
- `getByCategory(String category)` : filtre par categorie.
- `getPage(String category, int offset, int limit)` : pagination.
- `findById(String id)` : cherche un produit par ID.
- `distinctCategories()` : retourne les categories avec `Tous`.
- `updateStock(String id, int newStock)` : modifie le stock.

Bibliotheques :

- `Optional` : resultat qui peut etre absent.
- `List`, `Set`, `ArrayList` : collections produit.

### `ProductListingInfo`

Role : record qui represente une fiche vendeur ou une ligne de moderation.

Champs :

- `productId`
- `nomProduit`
- `sku`
- `sellerId`
- `listingStatus`
- `submittedAtMs`
- `rejectionReason`

Utilisation :

- affiche la file d'attente admin
- affiche les fiches d'un vendeur
- transporte les informations sans creer une classe mutable

Bibliotheques :

- Java `record` : DTO immutable.

### `AdminProductRow`

Role : record pour la console catalogue admin.

Champs typiques :

- ID produit
- SKU
- nom
- marque
- categorie
- prix
- stock
- statut
- vendeur
- date de soumission
- raison de refus

Utilisation :

- alimente le tableau admin.
- permet la recherche, suppression et modification du stock.

## Services

### `ProductService`

Role : service socket du catalogue public.

Methodes importantes :

- `list(Message request)` : traite `PRODUCT_LIST`.
- `details(Message request)` : traite `PRODUCT_DETAILS`.
- `updateStock(Message request)` : traite une mise a jour de stock.
- `categories(Message request)` : traite `PRODUCT_CATEGORIES`.

Classe interne :

- `UpdateStockRequest` : objet serialise contenant ID produit et nouveau stock.

Dependances :

- `ProductRepository`
- `JsonUtil`
- `Message`
- `Base64`

### `ProductListingService`

Role : service vendeur/admin pour fiches en attente.

Methodes importantes :

- `submit(Message request)` : vendeur soumet un produit.
- `listPending(Message request)` : admin liste les fiches en attente.
- `listMine(Message request)` : vendeur liste ses propres fiches.
- `approve(Message request)` : admin approuve.
- `reject(Message request)` : admin refuse.

Controles importants :

- verifie le `sessionToken`.
- verifie le role `SELLER` pour soumettre.
- verifie le role `ADMIN` pour moderer.

### `AdminProductService`

Role : service de console catalogue admin.

Methodes importantes :

- `list(Message request)` : liste et recherche les produits.
- `categories(Message request)` : renvoie la liste controlee des categories.
- `create(Message request)` : valide et cree un produit.
- `updateStock(Message request)` : met a jour le stock.
- `delete(Message request)` : supprime un produit.

Validations :

- prix > 0.
- stock >= 0.
- SKU optionnel mais valide si fourni.
- categorie obligatoire et connue.
- image URL vide ou `http://` / `https://`.

## Classes communes et UI liees

### `TextUiNormalizer`

Role : normalise les libelles francais, surtout les categories.

Methodes importantes :

- `normalizeFrenchUi(String s)` : transforme les anciennes variantes en affichage correct.
- `categoryMatchVariants(String category)` : cree plusieurs variantes pour les recherches SQL.

### `ProductImageLoader`

Role : charge les images produits dans JavaFX sans bloquer l'interface.

Methode importante :

- `loadAsync(...)` : telecharge/decode l'image puis met a jour l'UI.

Bibliotheques :

- JavaFX `Image`.
- ImageIO.
- TwelveMonkeys WebP : decode les images `.webp`.

## Bibliotheques globales

- JDBC : communication avec MySQL.
- MySQL Connector/J : pilote SQL.
- Java Serialization : transfert binaire des listes de `Product`.
- Base64 : transforme le binaire en texte compatible JSON.
- JavaFX : affichage catalogue cote client.
- TwelveMonkeys ImageIO WebP : support WebP.

## Checklist de test

1. Lancer le serveur.
2. Charger le catalogue.
3. Filtrer par categorie.
4. Ouvrir un detail produit.
5. Soumettre une fiche vendeur.
6. L'approuver/refuser en admin.
7. Creer un produit admin.
8. Chercher, modifier le stock, supprimer.
