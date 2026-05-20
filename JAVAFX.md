# JavaFX — documentation de référence (ChriOnline)

Guide court pour travailler sur l’interface **JavaFX** de ce dépôt. La version utilisée est définie dans `chrionline-server/pom.xml` (`javafx.version`, actuellement **21.0.2**).

## Ressources officielles

| Ressource | URL |
|-----------|-----|
| Projet **OpenJFX** (site, install, tutoriels) | [https://openjfx.io](https://openjfx.io) |
| **Getting Started** (Maven, Gradle, IDEs) | [https://openjfx.io/openjfx-docs/](https://openjfx.io/openjfx-docs/) |
| Javadoc **JavaFX 21** (module `javafx.graphics`) | [https://openjfx.io/javadoc/21/](https://openjfx.io/javadoc/21/) |
| **Scene Builder** (FXML visuel) | [https://gluonhq.com/products/scene-builder/](https://gluonhq.com/products/scene-builder/) |

## Concepts utiles

- **Application** : classe qui étend `javafx.application.Application`, point d’entrée `start(Stage)`.
- **Stage** : fenêtre (titre, taille).
- **Scene** : contenu de la fenêtre ; racine = un **Parent** (layout).
- **Node** : tout élément de la scène (`Button`, `Label`, `VBox`, `ImageView`, …).
- **Layouts courants** : `VBox`, `HBox`, `BorderPane`, `GridPane`, `StackPane`, `ScrollPane`.

Depuis **Java 11+**, JavaFX n’est plus dans le JDK : les dépendances **OpenJFX** (`org.openjfx`) sont ajoutées via Maven (comme dans ce projet).

## Dans ChriOnline

- **Classe principale client** : `ui.ChriOnlineClientApp` (`chrionline-server/src/main/java/ui/`).
- **Lancer l’UI avec Maven** (depuis `chrionline-server`) :

  ```text
  mvnw javafx:run
  ```

  (Sur Linux/macOS : `./mvnw javafx:run`.)

- Le plugin **javafx-maven-plugin** est configuré dans `pom.xml` avec cette `mainClass`.
- Le **JAR « fat »** produit par `package` est orienté **serveur** (`ServerMain`) ; JavaFX est **exclu** du shade — ne pas s’attendre à lancer l’UI depuis ce JAR seul.

## Modules JavaFX (JPMS)

Avec `java` en ligne de commande hors Maven, il faut souvent `--module-path` et `--add-modules javafx.controls,javafx.fxml` (voir [openjfx-docs](https://openjfx.io/openjfx-docs/)). Avec **javafx-maven-plugin**, le plugin gère en général ces détails.

## FXML et CSS

- **FXML** : description XML de la scène ; contrôleur Java avec `@FXML`.
- **CSS** : feuilles de style sur les nœuds (`node.setStyle(...)` ou `scene.getStylesheets().add(...)`).

ChriOnline construit une grande partie de l’UI **en code Java** ; les mêmes principes s’appliquent si vous extrayez des vues en FXML.
