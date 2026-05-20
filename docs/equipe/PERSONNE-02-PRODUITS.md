# Personne 2 — Gestion produits
## Rôles et responsabilités (cahier des charges)
| Thème | Détail |
|-------|--------|
| Consultation liste | Catalogue complet ou paginé, avec filtre par catégorie métier. |
| Recherche / filtre | Filtre catégorie côté serveur ; recherche texte (nom) principalement côté client JavaFX sur la liste déjà chargée. |
| Ajout / modification / suppression | Catalogue : les données viennent de MySQL (`products`) via le script SQL ; pas d'API CRUD produit dans `RequestRouter` sauf mise à jour du stock (`STOCK_UPDATE`). |
| Stock | `ProductRepository.updateStock` → `ProductCatalogDAO.updateStock` (`UPDATE products SET stock = ?`). |
## Correspondance cahier des charges → code
| Spécification | Implémentation |
|---------------|----------------|
| Produit | `product.Product` |
| ProduitService | `services.ProductService` |
### Couche données et normalisation
| Composant | Rôle |
|-----------|------|
| `product.ProductCatalogDAO` | Requêtes SQL sur la table `products` (lecture, filtre, pagination, détail, stock). |
| `product.ProductRepository` | Façade : JDBC si possible, sinon liste fallback en mémoire pour démo hors base. |
| `common.TextUiNormalizer` | Normalise certains libellés (ex. catégories) pour un affichage ASCII fiable dans l'UI. |
### Base de données
Table `products` (voir `sql/chrionline_schema_all.sql` + `sql/chrionline_products_data.sql`) : entre autres `product_id`, `sku`, `nom_produit`, `marque`, `categorie_source`, `categorie_metier`, `prix_usd`, `remise_pct`, `prix_net_usd`, `stock`, `description`, `image_principale`, `rating`.
Le DAO mappe vers `Product` avec prix net effectif :  
`COALESCE(prix_net_usd, prix_usd * (1 - remise))`.
### Messages réseau (`RequestRouter`)
| Type | Méthode | Rôle |
|------|---------|------|
| `PRODUCT_LIST` | `ProductService.list` | Liste (option : `category`, `limit`, `offset`). |
| `PRODUCT_CATEGORIES` | `ProductService.categories` | Tableau JSON de catégories (`Tous` en tête). |
| `PRODUCT_DETAILS` | `ProductService.details` | Un produit par id (texte dans le payload). |
| `STOCK_UPDATE` | `ProductService.updateStock` | Mise à jour stock (payload binaire Base64). |
## `product.Product`
Classe `Serializable` pour sérialisation binaire (liste / détail) puis encodage Base64 dans le `Message`.
### Constructeurs
| Méthode | Explication |
|---------|-------------|
| `Product()` | Vide. |
| `Product(id, name, description, price, stock)` | Ancien constructeur minimal. |
| `Product(id, name, description, price, stock, imageUrl, category, brand, rating)` | Complet ; normalise chaînes nulles en `""` pour URL, catégorie, marque. |
### Méthodes
| Méthode | Explication |
|---------|-------------|
| `getId()` | Identifiant texte (souvent `String.valueOf(product_id)`). |
| `getName()` | Nom affiché (`nom_produit` normalisé). |
| `getDescription()` | Description longue. |
| `getPrice()` | Prix unitaire affiché / ligne de commande (net). |
| `getStock()` | Quantité disponible. |
| `setStock(int)` | Met à jour le stock (fallback mémoire ou après sync). |
| `getImageUrl()` / `setImageUrl(String)` | URL image principale. |
| `getCategory()` / `setCategory(String)` | Catégorie métier. |
| `getBrand()` / `setBrand(String)` | Marque. |
| `getRating()` / `setRating(double)` | Note. |
*Remarque :* pas de setters pour `id`, `name`, `description`, `price` dans la version actuelle — le catalogue est rempli depuis la base via le DAO.
## `product.ProductCatalogDAO`
Classe utilitaire `final` (que des méthodes `static`). Colonnes SQL aliasées pour compatibilité d'anciens schémas.
| Méthode | Explication |
|---------|-------------|
| `loadAll()` | Tous les produits, tri `product_id`. |
| `loadByCategory(String category)` | Filtre `categorie_metier = ?` ; vide/null → `loadAll()`. |
| `loadPage(int offset, int limit)` | Pagination globale `LIMIT/OFFSET` ; `limit <= 0` → liste vide. |
| `loadPageByCategory(String category, int offset, int limit)` | Pagination par catégorie. |
| `findByProductId(int productId)` | Un produit ou `null`. |
| `updateStock(int productId, int newStock)` | `UPDATE products` ; retourne `true` si une ligne modifiée. |
Méthodes privées : `mapRows`, `mapRow` — appliquent `TextUiNormalizer.normalizeFrenchUi` sur nom, description, catégorie, marque.
## `product.ProductRepository`
| Méthode | Explication |
|---------|-------------|
| `getAll()` | Copie immuable de tous les produits JDBC, ou fallback si exception. |
| `getByCategory(String category)` | Filtre ; `null`, vide ou `Tous` (insensible à la casse) → tout le catalogue. |
| `getPage(String category, int offset, int limit)` | Page ; en échec JDBC, découpe en mémoire sur `getByCategory`. |
| `findById(String id)` | Parse en `int` pour la base ; sinon cherche dans le fallback. Retourne `Optional<Product>`. |
| `distinctCategories()` | `LinkedHashSet` avec `Tous` puis catégories distinctes des produits chargés. |
| `updateStock(String id, int newStock)` | Parse `id` en entier pour JDBC ; sinon met à jour une entrée du fallback si l'id correspond. |
## `services.ProductService`
| Méthode | Explication |
|---------|-------------|
| `list(Message request)` | Payload JSON optionnel. Vide → `getAll()`. Sinon map : si clé `limit` → `getPage(category, offset, limit)` avec plafond 500 ; sans `limit` → `getByCategory`. Sérialise `List<Product>` en binaire puis Base64 dans le payload. Erreur : `SERIALIZATION_ERROR`. |
| `details(Message request)` | Payload = id produit en texte brut. Retourne un `Product` en Base64 ou `NOT_FOUND`. |
| `updateStock(Message request)` | Décode Base64 vers classe interne `UpdateStockRequest` (`id` String, `int stock`) puis `ProductRepository.updateStock`. |
| `categories(Message request)` | JSON tableau trié avec `Tous` en première position. |
Classe interne `UpdateStockRequest` : champs `id`, `stock` pour désérialisation Java.
Méthode privée : `esc(String)` pour échapper les chaînes dans le JSON des catégories.
## Bibliothèques et dépendances utiles
| Dépendance | Rôle pour cette personne |
|------------|---------------------------|
| MySQL Connector/J | JDBC : `ProductCatalogDAO` lit la table `products`. |
| Java (sérialisation) | `Product` est `Serializable` ; `ProductService` envoie des listes/objets en binaire puis Base64 dans le `Message`. |
| `common.TextUiNormalizer` | Normalisation d'affichage et variantes de catégorie pour les requêtes SQL (`IN (...)`). |
Côté client JavaFX, le chargement d'images distantes (souvent `.webp`) utilise TwelveMonkeys ImageIO WebP dans `ui.ProductImageLoader` (voir `pom.xml`).
## Points d'attention
- Le client doit décoder le Base64 puis désérialiser les `byte[]` en `ArrayList<Product>` / `Product` (voir `JsonUtil` côté client).
- L'administration complète des fiches produit (CRUD hors stock) se fait aujourd'hui par SQL ou outil DB, pas par l'API socket.
- Les montants des commandes sont recalculés côté serveur à partir du catalogue au moment de `CREATE_COMMANDE` — le prix affiché client doit rester cohérent avec la base.
