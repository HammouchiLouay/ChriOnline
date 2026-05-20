# ChriOnline — Résumé Personnes 2 à 5 : classes et rôle des méthodes
Ce document complète Personne 1 (authentification). Il couvre Produits (P2), Panier (P3), Commandes (P4) et Paiement (P5). Le détail pédagogique reste dans `PERSONNE-0X-*.md` et dans le code sous `chrionline-server/src/main/java`.
Fichiers par personne (à la racine du projet, comme pour Personne 1) : `PersonneX-classes-et-methodes.txt`, `PersonneX-summary-english.txt`, `Resume-PersonneX-classes-et-methodes.md`, `PersonneX-database-impact.txt` (X = 2…5).
Les classeurs de planification associés (un fichier par personne, avec titre de poste en première ligne fusionnée) sont générés par `tools/build_personnes_2_5_workbook.py` :
| Fichier | Rôle (titre du poste dans le classeur) |
|---------|----------------------------------------|
| `ChriOnline-Planning-Personne-2-Catalogue-et-stock.xlsx` | Responsable catalogue, fiches produits et stock |
| `ChriOnline-Planning-Personne-3-Panier-client.xlsx` | Responsable du panier d'achat (client JavaFX) |
| `ChriOnline-Planning-Personne-4-Commandes.xlsx` | Responsable des commandes et persistance |
| `ChriOnline-Planning-Personne-5-Paiement-et-integration.xlsx` | Responsable paiement simulé, historique et moyens enregistrés |
## Personne 2 — Produits (catalogue, stock)
Périmètre : consultation du catalogue MySQL (`products`), catégories, détail produit, mise à jour du stock via le réseau ; normalisation d'affichage ; images côté client.
| Classe / composant | Rôle principal |
|--------------------|----------------|
| `product.Product` | DTO sérialisable (prix net, stock, image, catégorie, marque, note). |
| `product.ProductCatalogDAO` | SQL : `loadAll`, `loadByCategory`, pagination, `findByProductId`, `updateStock`. |
| `product.ProductRepository` | Façade JDBC + fallback mémoire si la base échoue. |
| `services.ProductService` | `PRODUCT_LIST`, `PRODUCT_CATEGORIES`, `PRODUCT_DETAILS`, `STOCK_UPDATE` (payload binaire Base64 pour le stock). |
| `common.TextUiNormalizer` | `normalizeFrenchUi`, `categoryMatchVariants` pour filtres / affichage. |
| `ui.ProductImageLoader` | Chargement d'images (ex. WebP) pour la grille catalogue. |
Messages : `PRODUCT_LIST`, `PRODUCT_CATEGORIES`, `PRODUCT_DETAILS`, `STOCK_UPDATE`.
Fichier détaillé : `docs/equipe/PERSONNE-02-PRODUITS.md`
## Personne 3 — Panier (client JavaFX uniquement)
Périmètre : il n'existe pas de table SQL « panier » ; l'état est `Map<String, Integer> cart` dans `ui.ChriOnlineClientApp` (id produit → quantité).
| Méthodes (indicatif) | Rôle |
|----------------------|------|
| `addProductDetailToCart` | Ajout depuis la fiche produit ; `updateCartSummary`. |
| `updateCartSummary` | Total USD, libellé, lien avec le tableau de bord. |
| `findProduct` | Résout un `Product` depuis les listes chargées. |
| `buildProduitsPayload` | Chaîne `id:qty;id:qty` pour `CREATE_COMMANDE`. |
| `createCommandeFromCart` | Envoie la commande puis vide `cart`. |
Fichier détaillé : `docs/equipe/PERSONNE-03-PANIER.md`
## Personne 4 — Commandes (persistance MySQL)
Périmètre : création de commande avec lignes, validation, annulation, historique ; tables `orders` et `order_lines`.
| Classe | Rôle principal |
|--------|----------------|
| `models.LigneCommande` | Ligne figée (produit, nom, quantité, prix unitaire). |
| `models.Commande` | Agrégat avec statut (`EN_ATTENTE`, `VALIDE`, `PAYEE`, `ANNULEE`, …), `toJson()`. |
| `services.CommandeService` | `createCommande`, `createCommandeAvecProduits`, `validerCommande`, `annulerCommande`, `getCommandesByUser`, `getCommandeById`, `updateCommandeStatus`. |
| `persistence.CommandeDAO` | `insert`, `findById`, `findByUserId`, `valider`, `annuler`, `updateStatus`. |
Messages : `CREATE_COMMANDE`, `VALIDER_COMMANDE`, `ANNULER_COMMANDE`, `GET_COMMANDES` (avec session côté routeur : jeton → utilisateur).
Fichier détaillé : `docs/equipe/PERSONNE-04-COMMANDES.md`
## Personne 5 — Paiement et intégration
Périmètre : simulation métier dans `ecommerce.personne5` (coupons, types de paiement, fraude) ; pont `SocketPaymentService` (`SIMULATE_PAYMENT`) ; historique SQL ; moyens enregistrés masqués.
| Classe | Rôle principal |
|--------|----------------|
| `ecommerce.personne5.service.PaiementService` | `simulerPaiement`, `confirmerPaiement`, coupons, frais, score fraude. |
| `services.SocketPaymentService` | Entrée socket : charge `models.Commande`, appelle le module eco, met à jour `PAYEE` / `ANNULEE`, historique. |
| `services.SavedPaymentService` | `LIST_SAVED_PAYMENT_METHODS`, `DELETE_SAVED_PAYMENT_METHOD`. |
| `persistence.PaymentHistoryDAO` | Insertion dans `historique_paiement`. |
| `persistence.SavedPaymentMethodDAO` | CRUD sur `methode_paiement_enregistree`. |
Messages : `SIMULATE_PAYMENT`, `LIST_SAVED_PAYMENT_METHODS`, `DELETE_SAVED_PAYMENT_METHOD` (et codes d'erreur associés).
Fichier détaillé : `docs/equipe/PERSONNE-05-PAIEMENT.md`
## Synthèse transverse
| Personne | Couche dominante |
|----------|------------------|
| 2 | Serveur + DAO + `ProductService` + client images |
| 3 | Client uniquement (`ChriOnlineClientApp`, panier) |
| 4 | Serveur + DAO commandes + `RequestRouter` |
| 5 | Module eco + services ChriOnline + DAO paiement |
*Pour la Personne 1 (utilisateurs / session), voir `Resume-Personne1-classes-et-methodes.md` et `Gestion des utilisateurs.xlsx` (généré par `tools/renew_gestion_utilisateurs_xlsx.py`).*
