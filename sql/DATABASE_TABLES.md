# Base `chrionline` — rôle de chaque table

**Création des tables (schéma à jour, utf8mb4) :** exécuter [`chrionline_schema_all.sql`](chrionline_schema_all.sql), puis les données produits si besoin : [`chrionline_products_data.sql`](chrionline_products_data.sql).

---

### Où sont stockées les données de carte (base MySQL) ?

- **Aucun numéro de carte complet ni CVV** n’est enregistré en base. La saisie dans l’UI sert à la **simulation** côté serveur (`PaiementService` / `SocketPaymentService`).
- **`historique_paiement`** : une ligne par tentative de paiement simulé — type de paiement, statut, montant, id de paiement simulé, message, **pas** de numéro de carte (`PaymentHistoryDAO`).
- **`methode_paiement_enregistree`** : uniquement si l’utilisateur coche « mémoriser » — colonne **`template_json`** avec un **modèle masqué** (ex. titulaire, **4 derniers chiffres**, marque, expiration ; PayPal = **code** tronqué ; wallet = alias). Inséré par `SavedPaymentMethodDAO`.

**Panier :** le client JavaFX garde le panier en **mémoire** (`Map` dans `ChriOnlineClientApp`). Il n’y a **pas** de table `cart` dans le schéma livré avec l’app.

---

Référence rapide : **quand** le code touche la table et **à quoi** elle sert.

| Table | Rôle | Utilisation dans l’application |
|-------|------|--------------------------------|
| **`user`** | Compte client (identité, mot de passe, e-mail, téléphone, rôle, e-mail vérifié). | Inscription / connexion (`AuthService`, `UserDAO`), mise à jour profil (`ProfileService`), clé étrangère pour commandes et paiements. |
| **`products`** | Catalogue (prix, stock, catégorie, image, etc.). | Liste / détail / catégories / mise à jour stock (`ProductCatalogDAO`, `ProductRepository`, `ProductService`). |
| **`orders`** | Commande : utilisateur, statut, total USD, date. | Création et lecture des commandes (`CommandeDAO`, `CommandeService`, paiement simulé). |
| **`order_lines`** | Lignes d’une commande (produit, quantité, prix unitaire). | Insérées avec la commande ; lues avec jointure sur `products` pour le nom (`CommandeDAO`). |
| **`historique_paiement`** | Trace des paiements simulés (liée à une commande et un utilisateur). | Écriture après `SIMULATE_PAYMENT` (`PaymentHistoryDAO`). Supprimée par utilisateur à la suppression de compte. |
| **`methode_paiement_enregistree`** | Modèles de paiement enregistrés (libellé + JSON masqué, pas de vrai PAN). | Liste / suppression / enregistrement optionnel (`SavedPaymentMethodDAO`, `SavedPaymentService`). Supprimée entièrement pour l’utilisateur à la suppression de compte. |

**Hors base de données :** codes OTP (profil, vérification e-mail, mot de passe oublié) sont stockés **en mémoire** sur le serveur (`ProfileSecurityService`, `EmailVerificationService`, `PasswordResetService`) — redémarrage du serveur les efface ; à la suppression de compte, les entrées pour cet utilisateur sont aussi retirées de ces maps.

**Suppression de compte (`DELETE_ACCOUNT`) :** supprime dans l’ordre les lignes liées (historique de paiement, lignes de commande, commandes, moyens enregistrés), puis la ligne `user`.
