# Personne 3 - Panier d'achat client

## Mission

Personne 3 gere le panier d'achat cote client JavaFX.

Important : il n'existe pas de table SQL `panier` et il n'existe pas de classe serveur separee nommee `PanierService`. Le panier est un etat temporaire dans `ui.ChriOnlineClientApp`. Quand l'utilisateur valide, le panier est transforme en payload pour `CREATE_COMMANDE`.

## Parcours general

1. Le client charge les produits depuis le serveur.
2. L'utilisateur ajoute un produit au panier.
3. `ChriOnlineClientApp` stocke l'ID produit et la quantite.
4. L'UI recalcule le total.
5. Au moment de commander, le panier devient une chaine `id:quantite;id:quantite`.
6. Le serveur cree une vraie commande avec `CommandeService`.

## Classes directement impliquees

### `ui.ChriOnlineClientApp`

Role : classe principale JavaFX. Pour Personne 3, elle contient l'etat panier et les methodes d'interaction.

Etat panier :

- `cart` : map `productId -> quantite`.
- `productById` : cache des produits charges.
- `catalogProducts` : liste visible du catalogue.
- `currentDetailProduct` : produit ouvert dans la fiche detail.
- `cartSummaryLabel` : label qui affiche le resume du panier.

Methodes panier importantes :

- `addProductDetailToCart()` : ajoute au panier le produit actuellement ouvert dans la page detail. Elle lit la quantite choisie, incremente la quantite existante et rafraichit le resume.
- `updateCartSummary()` : recalcule le nombre d'articles, le total et le texte affiche. Si le panier est vide, l'interface affiche un etat vide.
- `findProduct(String id)` : retrouve un `Product` depuis le cache ou la liste catalogue.
- `buildProduitsPayload()` : transforme le panier en format texte pour le serveur, par exemple `83:2;84:1`.
- `createCommandeFromCart()` : verifie que l'utilisateur est connecte, construit le payload JSON et envoie `CREATE_COMMANDE`.
- `currentCartTotal()` : calcule le total affiche dans l'interface.
- `createProductCard(...)` : cree une carte produit dans la grille. Le bouton d'ajout modifie le panier.
- `openProductDetail(...)` : affiche une fiche detail avec choix de quantite.

Comportement important :

- Le panier ne fixe pas definitivement le prix. Le serveur recalcule les lignes de commande depuis le catalogue au moment de `CREATE_COMMANDE`.
- Le panier est vide apres creation reussie d'une commande.
- Le panier est aussi nettoye quand la session utilisateur est supprimee/deconnectee.

Bibliotheques :

- JavaFX : `Button`, `Label`, `VBox`, `HBox`, `Spinner`, etc.
- Java Collections : `LinkedHashMap` pour garder l'ordre d'ajout.

### `product.Product`

Role : modele produit utilise par le panier pour afficher le nom, le prix et le stock.

Methodes importantes pour le panier :

- `getId()` : cle du panier.
- `getName()` : nom affiche.
- `getPrice()` : calcul du total.
- `getStock()` : verification de quantite.
- `getImageUrl()` : image dans la carte produit.
- `getCategory()` et `getBrand()` : informations affichees.

Pourquoi elle est importante :

- Le panier ne stocke pas tout le produit, seulement l'ID et la quantite.
- Pour afficher le panier, l'application retrouve le `Product` avec `findProduct`.

### `common.Message`

Role : enveloppe de la requete `CREATE_COMMANDE`.

Champs utilises :

- `type = CREATE_COMMANDE`
- `requestId` : identifiant de requete.
- `payload` : JSON contenant `userId`, `sessionToken` et `produits`.

Methode importante :

- `Message.request(...)` : construit le message envoye au serveur.

### `common.JsonUtil`

Role : aide a encoder/decoder les messages socket.

Utilisation pour Personne 3 :

- le payload panier est du JSON texte.
- la reponse serveur est lue comme un `Message`.

### `ui.SocketApiClient`

Role : envoie la commande creee par l'interface.

Methodes importantes :

- `send(Message request)` : envoie `CREATE_COMMANDE`.
- `parseCommandeId(String commandeJson)` : recupere l'ID de commande creee si necessaire.
- `parseCommandesFull(...)` et `parseCommandeSummaries(...)` : utiles apres creation pour afficher l'historique.

Bibliotheques :

- Java Socket : communication TCP.
- Java IO : lecture/ecriture.
- AES/RSA classes communes si le mode chiffre est active.

## Classes serveur touchees indirectement

### `server.RequestRouter`

Role : recoit `CREATE_COMMANDE` et appelle le service de commandes.

Pourquoi elle concerne Personne 3 :

- Le panier devient une commande uniquement quand le routeur transmet la requete au serveur.

### `services.CommandeService`

Role : transforme le payload panier en vraie commande SQL.

Methode importante :

- `createCommandeAvecProduits(int userId, String produitsData)` : lit `83:2;84:1`, retrouve les produits, cree les lignes et sauvegarde.

### `product.ProductRepository`

Role : retrouve les produits au moment de la creation de commande.

Methode importante :

- `findById(String id)` : permet au serveur de fixer le nom et le prix de la ligne.

## Format du payload panier

Exemple :

```json
{
  "userId": "3",
  "sessionToken": "token...",
  "produits": "83:2;84:1"
}
```

Signification :

- produit `83`, quantite `2`
- produit `84`, quantite `1`

## Bibliotheques globales

- JavaFX : interface panier.
- Java Collections : stockage temporaire.
- Java Socket : envoi au serveur.
- JSON interne : payload de creation commande.

## Checklist de test

1. Charger le catalogue.
2. Ajouter un produit depuis une carte.
3. Ouvrir une fiche detail et ajouter une quantite.
4. Verifier le total panier.
5. Creer la commande.
6. Verifier que le panier se vide apres succes.
7. Verifier que la commande apparait dans l'historique.
