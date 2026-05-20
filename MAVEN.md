# Maven — documentation de référence (ChriOnline)

Guide court pour **Apache Maven** dans ce dépôt. Le module Maven se trouve sous **`chrionline-server/`**.

## Ressources officielles

| Ressource | URL |
|-----------|-----|
| Site Apache **Maven** | [https://maven.apache.org/](https://maven.apache.org/) |
| Guide « 5 minutes » | [https://maven.apache.org/guides/getting-started/maven-in-five-minutes.html](https://maven.apache.org/guides/getting-started/maven-in-five-minutes.html) |
| **POM reference** (`pom.xml`) | [https://maven.apache.org/pom.html](https://maven.apache.org/pom.html) |
| **Plugin** Compiler | [https://maven.apache.org/plugins/maven-compiler-plugin/](https://maven.apache.org/plugins/maven-compiler-plugin/) |
| **Plugin** JAR | [https://maven.apache.org/plugins/maven-jar-plugin/](https://maven.apache.org/plugins/maven-jar-plugin/) |
| **Plugin** Shade (fat JAR) | [https://maven.apache.org/plugins/maven-shade-plugin/](https://maven.apache.org/plugins/maven-shade-plugin/) |
| **javafx-maven-plugin** (OpenJFX) | [https://github.com/openjfx/javafx-maven-plugin](https://github.com/openjfx/javafx-maven-plugin) |

## Wrapper Maven (`mvnw`)

Le dépôt inclut **`mvnw`** / **`mvnw.cmd`** : vous n’avez pas besoin d’installer Maven globalement.

Depuis **`chrionline-server`** (Windows) :

```text
mvnw.cmd compile
mvnw.cmd test
mvnw.cmd package -DskipTests
mvnw.cmd javafx:run
```

## Objectifs courants

| Commande | Effet |
|----------|--------|
| `compile` | Compile les sources `src/main/java`. |
| `test` | Lance les tests `src/test/java`. |
| `package` | Produit le JAR (et le JAR shaded `*-jar-with-dependencies.jar` ici). |
| `clean` | Supprime `target/`. |
| `javafx:run` | Lance l’application JavaFX (`ui.ChriOnlineClientApp`). |

## Spécificités ChriOnline (`pom.xml`)

- **Java 17** (`maven.compiler.release` / `source` / `target`).
- **Main serveur** (manifest JAR standard) : `server.ServerMain`.
- **Shade** : JAR avec dépendances pour le serveur ; artefacts **`org.openjfx`** exclus du fat JAR.
- **JavaFX** : dépendances `javafx-controls`, `javafx-base`, `javafx-graphics` + plugin `javafx-maven-plugin` pour le client.

Pour modifier la version de JavaFX, changer la propriété **`javafx.version`** dans `pom.xml` et vérifier la compatibilité avec votre JDK.

## Répertoires standard Maven

```text
src/main/java       — code Java
src/main/resources  — fichiers du classpath (properties, images, …)
src/test/java       — tests
target/             — sortie de build (généré, souvent ignoré par Git)
```
