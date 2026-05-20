# Gestion catalogue administrateur

Interface et protocole socket permettant aux comptes **`role = ADMIN`** de **créer** des produits publiés immédiatement (`APPROVED`) et de **supprimer** des fiches, avec une **console UI distincte** de la page « Modération catalogue » (files vendeur).

## Messages socket

| Type | Payload | Réponse SUCCESS |
|------|---------|-----------------|
| `ADMIN_PRODUCT_LIST` | `{"sessionToken":"..."}` | Tableau JSON `[{productId,nomProduit,sku,stock,listingStatus,prixUsd},…]` |
| `ADMIN_PRODUCT_CREATE` | `sessionToken`, `nomProduit`, `prixUsd`, `stock`, `categorieMetier`, optionnel `sku`, `marque`, `description`, `imageUrl` | `{"productId":…,"status":"APPROVED"}` |
| `ADMIN_PRODUCT_DELETE` | `sessionToken`, `productId` | `OK` |

Erreurs possibles : `AUTH_REQUIRED`, `NOT_ADMIN`, `NOT_FOUND`, `MISSING_FIELDS`, `INVALID_PRICE`, `INVALID_STOCK`, `DB_ERROR`, etc.

## Classes ajoutées

- **`product.AdminProductRow`** — enregistrement ligne liste admin (DAO → JSON).
- **`services.AdminProductService`** — vérifie session + rôle ADMIN, appelle le DAO, sérialise les réponses.

## Classes modifiées

- **`product.ProductCatalogDAO`** — `insertAdminDirectApproved`, `deleteProductById`, `listAllForAdmin`.
- **`server.RequestRouter`** — dispatch des trois types ci-dessus.
- **`ui.SocketApiClient`** — `AdminCatalogRow`, `parseAdminCatalogRows` pour le client JavaFX.
- **`ui.ChriOnlineClientApp`** — écran **Console catalogue (admin)** (chrome ambré), lien sidebar doré, badge **rôle** sur la page Compte, mention « Administrateur » dans la bannière session ; méthodes `showAdminCatalogPage`, `refreshAdminCatalogList`, `submitAdminCatalogCreate`, `confirmDeleteAdminProduct`.

## Utilisation (profil admin)

1. Se connecter avec un utilisateur MySQL `role = 'ADMIN'`.
2. Dans **COMPTE**, ouvrir **Console catalogue (admin)** (lien distinct de « Modération catalogue »).
3. Remplir le formulaire **Nouveau produit** puis **Créer et publier** ; le produit est visible tout de suite dans le catalogue public.
4. **Actualiser la liste**, puis **Supprimer** sur une carte pour retirer une fiche.

Les comptes **CLIENT** voient le même bandeau « Client » discret sur la page Compte ; seuls les ADMIN ont le badge ambré et les liens console/modération.
