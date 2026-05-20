# JavaFX dans ChriOnline

## Rôle dans le projet

JavaFX fournit l’interface graphique du client boutique : fenêtre unique, navigation par onglets (accueil, catalogue, commandes, compte, espaces vendeur/admin), panier, catalogue produits, dialogue de paiement simulé, connexion au serveur TCP. Le serveur socket (`server.ServerMain`) n’utilise pas JavaFX ; il tourne en processus séparé. Le même module Maven compile les deux ; seul le point d’entrée lancé change.

## Emplacement dans le dépôt

Racine du code UI : `chrionline-server/src/main/java/ui/`

- `ChriOnlineClientApp.java` — Point d’entrée JavaFX (`extends javafx.application.Application`). Construit la scène (barre titre custom, bandeau latéral, zone centrale, panneau droit), gère la navigation, le panier (`Map` produit → quantité), les appels réseau via `SocketApiClient`, les toasts d’erreur, la restauration de session locale (`ClientPrefs`), la découverte LAN (`LanDiscoveryClient`). Fichier central : assemblage UI en code Java (pas de FXML dans ce projet).
- `SocketApiClient.java` — Client TCP : envoie des `common.Message` en JSON, parse les réponses (catalogue sérialisé, commandes JSON, auth, paiement). Utilisé depuis `ChriOnlineClientApp`.
- `UiMessages.java` — Libellés français pour les codes d’erreur serveur affichés dans l’UI.
- `ProductImageLoader.java` — Charge les images produit en arrière-plan (`ImageView`) ; s’appuie sur ImageIO WebP (dépendance Maven) car `javafx.scene.image.Image` seul échoue souvent sur les URLs `.webp`.
- `BrandIconUtil.java` — Génération d’icônes / images pour la fenêtre et la barre titre (JavaFX `Image`, etc.).
- `StageResizeBehavior.java` — Comportement de redimensionnement / plein écran lié au `Stage`.
- `LanDiscoveryClient.java` — Multicast pour trouver hôte/port serveur ; résultat injecté dans les champs de connexion de l’app.

Ressources classpath : `chrionline-server/src/main/resources/` (l’UI peut charger des ressources ; `email-config.properties` sert surtout au serveur).

Déclaration Maven : `chrionline-server/pom.xml` — propriété `javafx.version` (21.0.2), artefacts `org.openjfx:javafx-controls`, `javafx-base`, `javafx-graphics`, plugin `org.openjfx:javafx-maven-plugin` avec `mainClass` = `ui.ChriOnlineClientApp`.

## Version et modules

La version est dans `pom.xml` (`javafx.version`). Les trois artefacts OpenJFX couvrent contrôles (`Button`, `Label`, `VBox`, …), graphiques 2D et base (`Application`, `Stage`, `Scene`). Pas de dépendance `javafx-fxml` : aucune vue FXML.

Le JAR shaded `jar-with-dependencies` exclut `org.openjfx:*` : ce JAR lance `ServerMain`, pas le client graphique.

## Concepts utilisés dans le code

- `Application` / `start(Stage)` : une `Scene` sur le `Stage` principal.
- Hiérarchie : `Parent` (`VBox`, `HBox`, `BorderPane`, `StackPane`, `ScrollPane`, `TilePane`) et `Node` enfants.
- `Platform.runLater` : retour sur le thread JavaFX après travail réseau sur un thread séparé.
- Styles : souvent `-fx-...` inline via `setStyle` sur les nœuds.

## Lancement

Depuis `chrionline-server` : Windows `mvnw.cmd javafx:run`, Unix `./mvnw javafx:run`. Exécute `ui.ChriOnlineClientApp` via `javafx-maven-plugin`. Alternative : lancer la classe depuis un IDE avec classpath Maven.

## Interaction avec le build

`mvnw compile` compile le package `ui`. `mvnw package` produit le JAR serveur shaded sans JavaFX. Pas de JAR client graphique autonome : utiliser `javafx:run` ou l’IDE.

## TwelveMonkeys WebP

`com.twelvemonkeys:imageio-webp` dans le `pom` alimente le décodage WebP pour `ProductImageLoader`, en complément de JavaFX.
