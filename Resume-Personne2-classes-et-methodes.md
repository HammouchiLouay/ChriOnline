# ChriOnline — Résumé Personne 2 : classes et rôle de chaque méthode

**Périmètre** : catalogue MySQL (`products`), catégories, détail produit, **stock** ; soumission vendeur / modération admin (`listing_status`) ; normalisation d’affichage ; images côté client.

**Titre du poste** : responsable catalogue, fiches produits et stock (Personne 2 — ChriOnline).

**Impact base** : voir `Personne2-database-impact.txt`.

---

## 1. Modèle et accès données — `product.*`

### `product.Product`

DTO `Serializable` pour transfert réseau (liste / détail en Base64).

| Méthode | Rôle |
|--------|------|
| Constructeurs | Vide, minimal, ou complet (image, catégorie, marque, note). |
| Accesseurs `get*` / `setStock` | Prix net, stock, métadonnées affichage. |

### `product.ProductListingInfo` (record)

| Champ | Rôle |
|--------|------|
| `productId` … `rejectionReason` | Fiche modération / espace vendeur (hors `Product` catalogue public). |

### `product.ProductCatalogDAO`

SQL statique sur `products` ; catalogue public filtré **APPROVED** (ou `NULL`).

| Méthode | Rôle |
|--------|------|
| `loadAll` / `loadByCategory` / `loadPage*` | Liste et pagination. |
| `findByProductId` | Détail par id. |
| `updateStock` | `UPDATE` colonne `stock`. |
| `insertPendingListing` | INSERT fiche **PENDING** (vendeur). |
| `listPending` / `listBySellerId` | Files admin / vendeur. |
| `approve` / `reject` | Modération **APPROVED** / **REJECTED**. |

### `product.ProductRepository`

| Méthode | Rôle |
|--------|------|
| `getAll`, `getByCategory`, `getPage` | Façade JDBC + **fallback mémoire** si échec. |
| `findById` | `Optional<Product>`. |
| `distinctCategories` | Jeu de catégories pour l’UI. |
| `updateStock` | Délègue au DAO ou au fallback. |

---

## 2. Services socket — `services.*`

### `services.ProductService`

| Méthode | Rôle |
|--------|------|
| `list` | `PRODUCT_LIST` — JSON optionnel `category`, `limit`, `offset`. |
| `details` | `PRODUCT_DETAILS` — id produit en texte. |
| `updateStock` | `STOCK_UPDATE` — charge binaire Base64 → `UpdateStockRequest`. |
| `categories` | `PRODUCT_CATEGORIES` — tableau JSON avec **Tous** en tête. |

### `services.ProductListingService`

| Méthode | Rôle |
|--------|------|
| `submit` | `SUBMIT_PRODUCT_LISTING` — vendeur + `insertPendingListing`. |
| `listPending` | `LIST_PENDING_PRODUCTS` — admin. |
| `listMine` | `LIST_MY_PRODUCT_LISTINGS` — vendeur. |
| `approve` / `reject` | `APPROVE_PRODUCT_LISTING` / `REJECT_PRODUCT_LISTING`. |

---

## 3. Client et normalisation

### `common.TextUiNormalizer`

| Méthode | Rôle |
|--------|------|
| `normalizeFrenchUi` | Libellés affichables. |
| `categoryMatchVariants` | Variantes SQL pour filtre catégorie. |

### `ui.ProductImageLoader`

Chargement d’images (ex. **WebP**) pour la grille catalogue.

---

## 4. Messages réseau (Personne 2)

| Type | Rôle |
|------|------|
| `PRODUCT_LIST` | Liste (pagination / catégorie). |
| `PRODUCT_CATEGORIES` | Catégories. |
| `PRODUCT_DETAILS` | Un produit. |
| `STOCK_UPDATE` | Stock. |
| `SUBMIT_PRODUCT_LISTING` | Fiche vendeur. |
| `LIST_PENDING_PRODUCTS` | File admin. |
| `LIST_MY_PRODUCT_LISTINGS` | Fiches vendeur. |
| `APPROVE_PRODUCT_LISTING` / `REJECT_PRODUCT_LISTING` | Modération. |

---

*Détail : `docs/equipe/PERSONNE-02-PRODUITS.md`, `Personne2-classes-et-methodes.txt`.*
