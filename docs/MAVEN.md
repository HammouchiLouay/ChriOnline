# Maven dans ChriOnline

## Rôle dans le projet

Maven décrit le projet Java sous `chrionline-server/` : dépendances (MySQL, OpenJFX, mail, WebP), compilateur, encodage UTF-8, JAR serveur avec dépendances, lancement du client JavaFX. Un seul `pom.xml` couvre le code serveur et client dans `src/main/java`.

## Emplacement

- Projet : `chrionline-server/pom.xml`
- Wrapper : `chrionline-server/mvnw`, `mvnw.cmd` (et `.mvn/` si présent)
- Sources : `chrionline-server/src/main/java`
- Ressources : `chrionline-server/src/main/resources` (ex. `email-config.properties`)
- Tests : `chrionline-server/src/test/java` si présent
- Sortie : `chrionline-server/target/`

## Coordonnées POM

- `groupId` : `com.chrionline`
- `artifactId` : `chrionline-server`
- `version` : `1.0-SNAPSHOT`
- `packaging` : `jar`

## Propriétés

- `maven.compiler.source` / `target` : Java 17
- `project.build.sourceEncoding` : UTF-8
- `javafx.version` : version OpenJFX pour les trois dépendances `org.openjfx` (ex. 21.0.2)

## Dépendances

- `com.mysql:mysql-connector-j` — JDBC MySQL (serveur, DAO).
- `org.openjfx:javafx-controls`, `javafx-base`, `javafx-graphics` — client JavaFX ; déclarés explicitement pour IDE et plugin.
- `com.twelvemonkeys.imageio:imageio-webp` — WebP pour l’UI.
- `org.eclipse.angus:angus-mail` — SMTP serveur.

BCrypt est vendored sous `src/main/java/org/mindrot/jbcrypt/` (voir commentaire dans le POM).

## Plugins

- `maven-compiler-plugin` 3.13.0 — `release` 17, UTF-8.
- `maven-jar-plugin` 3.4.2 — `Main-Class` = `server.ServerMain` pour le JAR non shaded.
- `javafx-maven-plugin` 0.0.8 — `mainClass` = `ui.ChriOnlineClientApp` pour `javafx:run`.
- `maven-shade-plugin` 3.6.0 — phase `package`, artefact `jar-with-dependencies`, `Main-Class` `ServerMain`, `ServicesResourceTransformer`, filtres META-INF. Exclusion `org.openjfx:*` : pas de JavaFX dans le fat JAR serveur.

## Commandes (répertoire `chrionline-server`)

- `mvnw.cmd clean compile` ou `./mvnw clean compile`
- `mvnw.cmd test`
- `mvnw.cmd package -DskipTests` — JAR + `*-jar-with-dependencies.jar`
- `mvnw.cmd javafx:run` — client JavaFX

`clean` supprime `target/`.

## Deux entrées, un module

Le manifest du JAR « léger » pointe vers `ServerMain`. Le client utilise `javafx:run`. `java -jar` sur le fat JAR démarre le serveur, pas `ChriOnlineClientApp`.

## Changer la version JavaFX

Modifier `javafx.version` dans `pom.xml` ; garder les trois artefacts `org.openjfx` alignés sur la même version et compatible JDK 17.
