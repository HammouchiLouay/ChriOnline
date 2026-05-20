# ChriOnline — Résumé Personne 3 : classes et rôle de chaque méthode

**Périmètre** : panier d’achat **uniquement côté client JavaFX**. Aucune table SQL « panier » : état en mémoire dans `ui.ChriOnlineClientApp`.

**Titre du poste** : responsable du panier d’achat et de l’expérience d’achat (client JavaFX) — Personne 3.

**Impact base** : voir `Personne3-database-impact.txt` (aucune table modifiée par le panier).

---

## 1. État — `ui.ChriOnlineClientApp`

| Élément | Rôle |
|---------|------|
| `cart` | `Map<String, Integer>` — id produit → quantité (`LinkedHashMap`, ordre d’insertion). |

---

## 2. Méthodes équivalent « PanierService »

Toutes dans `ChriOnlineClientApp` (signatures simplifiées).

| Méthode | Rôle |
|--------|------|
| `addProductDetailToCart` | Ajout depuis la fiche produit ; `updateCartSummary`. |
| `updateCartSummary` | Total USD, libellé, lien tableau de bord. |
| `findProduct` | Résout un `Product` depuis les listes chargées. |
| `buildProduitsPayload` | Chaîne `id:qty;id:qty` pour `CREATE_COMMANDE`. |
| `createCommandeFromCart` | Envoie la commande puis vide `cart` si succès. |

La grille catalogue incrémente aussi `cart` puis appelle `updateCartSummary`.

---

## 3. Réseau et dépendances

| Composant | Rôle |
|-----------|------|
| `common.Message` / `JsonUtil` | `CREATE_COMMANDE` avec `userId` + `produits`. |
| `ui.SocketApiClient` | Envoi TCP. |

Le serveur ne reçoit pas un objet « Panier » : uniquement la chaîne produits + utilisateur. Les montants définitifs sont figés **côté serveur** (Personne 4).

---

## 4. Messages réseau (liés au panier)

| Type | Rôle |
|------|------|
| `CREATE_COMMANDE` | Conversion panier → commande persistée (côté Personne 4). |

---

*Détail : `docs/equipe/PERSONNE-03-PANIER.md`, `Personne3-classes-et-methodes.txt`.*
