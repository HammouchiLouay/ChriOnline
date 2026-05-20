# ChriOnline — répartition par personne (1 à 5)

Synthèse des classes et rôles. Détail : `equipe/PERSONNE-0X-*.md` et `chrionline-server/src/main/java`.

## Architecture commune

| Élément | Rôle |
|--------|------|
| Transport | TCP, une ligne JSON par requête/réponse (`ClientHandler` → `RequestRouter.route`). |
| Enveloppe | `common.Message` : type, requestId, status, payload, errorCode. |
| JSON | `JsonUtil` : Message, toMap, binaire catalogue. |
| Base | MySQL JDBC, `chrionline.BaseDonnees`. |
| Client | JavaFX 21, `ui.ChriOnlineClientApp`. |
| Mots de passe | `PasswordHasher`, BCrypt vendored. |
| E-mail P1 | Angus Mail, `MailConfigLoader`, `MailService`. |
| Images UI | WebP, `ProductImageLoader`. |

## Personne 1 — Authentification

Rôle métier : inscription, connexion, vérif e-mail, reset MDP, profil, OTP, suppression compte.

| Classe | Rôle |
|--------|------|
| chrionline.User / UserDAO / BaseDonnees / Authentification / PhoneNumberLookup / TestConnexion | Domaine user, JDBC, connexion, auth, téléphone, test. |
| services.AuthService, MailConfigLoader, MailService, EmailVerificationService, PasswordResetService, ProfileSecurityService, ProfileService, AccountDeletionService | Messages réseau et mail. |
| common.PasswordHasher, MaskingUtil, ClientPrefs, ClientConfigLoader | Hash, masquage, prefs client. |

Fichier : equipe/PERSONNE-01-AUTH.md

## Personne 2 — Produits

Rôle métier : catalogue, catégories, détail, stock.

| Classe | Rôle |
|--------|------|
| product.Product, ProductCatalogDAO, ProductRepository | DTO, SQL, façade. |
| services.ProductService | PRODUCT_LIST, CATEGORIES, DETAILS, STOCK_UPDATE. |
| common.TextUiNormalizer | Normalisation catégorie / UI. |

Fichier : equipe/PERSONNE-02-PRODUITS.md

## Personne 3 — Panier

Rôle métier : panier côté client uniquement ; pas de table serveur panier.

| Concept | Code |
|--------|------|
| Panier | `Map<String,Integer> cart` dans `ChriOnlineClientApp`. |
| Logique | Méthodes privées : updateCartSummary, findProduct, buildProduitsPayload, createCommandeFromCart, … |

Fichier : equipe/PERSONNE-03-PANIER.md

## Personne 4 — Commandes

Rôle métier : création, validation, liste, annulation ; MySQL.

| Classe | Rôle |
|--------|------|
| models.Commande, LigneCommande | Agrégat, lignes. |
| services.CommandeService | Création, validation, annulation, lecture. |
| persistence.CommandeDAO | SQL orders / order_lines. |

Messages : CREATE_COMMANDE, VALIDER_COMMANDE, GET_COMMANDES, ANNULER_COMMANDE.

Fichier : equipe/PERSONNE-04-COMMANDES.md

## Personne 5 — Paiement

Rôle métier : simulation `ecommerce.personne5`, pont socket, historique SQL, moyens enregistrés.

| Classe | Rôle |
|--------|------|
| SocketPaymentService, SavedPaymentService | SIMULATE_PAYMENT, listes moyens. |
| PaymentHistoryDAO, SavedPaymentMethodDAO | SQL historique / moyens. |

Module personne5 : packages service, model, utils, config, main (`PaiementApp`), dashboard (`AdminDashboard`). Exemples : PaiementService, PromotionService, StatutPaiement, TypePaiement, etc.

Attention : `ecommerce.personne5.model.Commande` est le modèle de simulation ; la commande métier ChriOnline est `models.Commande` (table `orders`).

Fichier : equipe/PERSONNE-05-PAIEMENT.md

## Réseau transversal

| Classe | Rôle |
|--------|------|
| ServerMain | Socket, MySQL, mail, LAN. |
| RequestRouter, ClientHandler | Dispatch, boucle client. |
| NetworkInfo, PublicIpHint, LanDiscoveryProtocol, LanDiscoveryClient | Connexion réseau. |
| SocketApiClient, UiMessages | Client TCP, textes erreurs. |

## Schéma SQL

`sql/chrionline_schema_all.sql`, `sql/chrionline_products_data.sql`, `sql/DATABASE_TABLES.md`.

## Index autres fichiers projet

Plannings Excel P2–P5 à la racine ; `PersonneX-*.txt`, `Resume-PersonneX-*.md` ; `tools/build_personnes_2_5_workbook.py` ; equipe/Resume-Personnes-2-a-5-classes-et-methodes.md ; EQUIPE-CHRIONLINE.md.
