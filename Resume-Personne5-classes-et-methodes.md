# ChriOnline — Résumé Personne 5 : classes et rôle de chaque méthode

**Périmètre** : simulation de paiement (`ecommerce.personne5`), pont socket `SIMULATE_PAYMENT`, historique SQL, moyens enregistrés masqués, mise à jour du statut de commande ChriOnline en base.

**Titre du poste** : responsable paiement simulé, historique et moyens enregistrés — Personne 5.

**Impact base** : voir `Personne5-database-impact.txt`.

---

## 1. Module `ecommerce.personne5` (extrait)

### `ecommerce.personne5.model.Paiement`

| Méthode | Rôle |
|--------|------|
| Constructeur | Montants, statut, type, message, token, score fraude, etc. |
| Getters / setters | Accès champs simulation. |
| `traiterPaiement` | Démo console. |

### `ecommerce.personne5.service.PaiementService`

| Méthode | Rôle |
|--------|------|
| `verifierCoupon` | Codes promo connus. |
| `calculerFraisLivraison` | Selon règles métier. |
| `simulerPaiement` | Cœur simulation (types de paiement, fraude, etc.). |
| `confirmerPaiement` / `rembourserPaiement` | Met à jour le modèle eco `Commande`. |

> La **commande** métier ChriOnline reste `models.Commande` + table `orders` ; le modèle eco sert à la simulation.

---

## 2. Services ChriOnline — `services.*`

### `services.SocketPaymentService`

| Méthode | Rôle |
|--------|------|
| `simulatePayment` | `SIMULATE_PAYMENT` : charge `Commande`, appelle `PaiementService`, `PaymentHistoryDAO.insert`, `CommandeDAO.updateStatus`, optionnel `SavedPaymentMethodDAO.insert`. |

### `services.SavedPaymentService`

| Méthode | Rôle |
|--------|------|
| `list` | `LIST_SAVED_PAYMENT_METHODS`. |
| `delete` | `DELETE_SAVED_PAYMENT_METHOD`. |

---

## 3. Persistance — `persistence.*`

### `persistence.PaymentHistoryDAO`

| Méthode | Rôle |
|--------|------|
| `insert` | Une ligne dans `historique_paiement` par tentative. |

### `persistence.SavedPaymentMethodDAO`

| Méthode | Rôle |
|--------|------|
| `insert` / `listByUser` / `deleteForUser` | Gabarits masqués dans `methode_paiement_enregistree`. |
| `toJsonArray` | Réponse JSON client. |

---

## 4. Messages réseau (Personne 5)

| Type | Rôle |
|------|------|
| `SIMULATE_PAYMENT` | Simulation + persistance. |
| `LIST_SAVED_PAYMENT_METHODS` | Liste des moyens enregistrés. |
| `DELETE_SAVED_PAYMENT_METHOD` | Suppression d’un gabarit. |

---

*Détail : `docs/equipe/PERSONNE-05-PAIEMENT.md`, `Personne5-classes-et-methodes.txt`.*
