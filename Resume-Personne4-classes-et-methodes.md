# ChriOnline — Résumé Personne 4 : classes et rôle de chaque méthode

**Périmètre** : commandes MySQL (`orders`, `order_lines`) ; création depuis le panier (chaîne `id:qty`) ou total simplifié ; validation, annulation, historique ; mise à jour de statut utilisée par le paiement.

**Titre du poste** : responsable des commandes, statuts et persistance (serveur & BDD) — Personne 4.

**Impact base** : voir `Personne4-database-impact.txt`.

---

## 1. Modèles — `models.*`

### `models.LigneCommande`

| Méthode | Rôle |
|--------|------|
| Constructeurs, getters | Ligne figée (id produit, nom, quantité, prix unitaire). |
| `calculerSousTotal` | `quantite * prixUnitaire`. |
| `toJson` | Fragment JSON. |

### `models.Commande`

| Méthode | Rôle |
|--------|------|
| Constructeurs | Id / utilisateur, statut initial `EN_ATTENTE`. |
| `getLignes` / `ajouterLigne` / `supprimerLigne` | Agrégat lignes. |
| `calculerTotal` | Somme des lignes. |
| `getStatus` / `setStatus` | Ex. `VALIDE`, `PAYEE`, `ANNULEE`. |
| `toJson` | JSON commande + lignes. |

---

## 2. Persistance — `persistence.CommandeDAO`

| Méthode | Rôle |
|--------|------|
| `insert` | Transaction `orders` + `order_lines`. |
| `findById` / `findByUserId` | Chargement avec lignes. |
| `updateStatus` | Mise à jour statut (ex. après paiement). |
| `valider` | `EN_ATTENTE` → `VALIDE` si autorisé. |
| `annuler` | Vers `ANNULEE` si statut ni `VALIDE` ni `PAYEE`. |

---

## 3. Métier — `services.CommandeService`

| Méthode | Rôle |
|--------|------|
| `createCommande` | Cas simplifié (une ligne « total »). |
| `createCommandeAvecProduits` | Parse `id:qty;…`, enrichit via `ProductRepository`. |
| `validerCommande` / `annulerCommande` | Délègue au DAO. |
| `getCommandesByUser` / `getCommandeById` | Lecture. |
| `updateCommandeStatus` | Pour intégration paiement. |

---

## 4. Messages réseau (Personne 4)

| Type | Rôle |
|------|------|
| `CREATE_COMMANDE` | Création. |
| `VALIDER_COMMANDE` | Validation. |
| `ANNULER_COMMANDE` | Annulation. |
| `GET_COMMANDES` | Liste (souvent liée à la **session** / jeton). |

---

*Détail : `docs/equipe/PERSONNE-04-COMMANDES.md`, `Personne4-classes-et-methodes.txt`.*
