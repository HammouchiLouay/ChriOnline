package common;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Chargement de la configuration client JavaFX : valeurs embarquées dans le JAR puis fichier utilisateur
 * sous {@code ~/.chrionline/}. Ordre de priorité : classpath puis fichier (les couches suivantes écrasent).
 *
 * <p>La propriété système {@code -Dchrionline.client.allow.loopback} surcharge {@code client.allow.loopback}.
 */
public final class ClientConfigLoader {

    private ClientConfigLoader() {}

    /** Chemin absolu du fichier {@code chrionline-client.properties} dans le répertoire utilisateur. */
    public static Path userClientConfigPath() {
        return Paths.get(System.getProperty("user.home", "."))
                .resolve(".chrionline")
                .resolve("chrionline-client.properties");
    }

    /** Fusionne les propriétés classpath et fichier utilisateur. */
    public static Properties load() {
        Properties p = new Properties();
        try (InputStream in = ClientConfigLoader.class.getResourceAsStream("/chrionline-client.properties")) {
            if (in != null) {
                p.load(in);
            }
        } catch (IOException ignored) {
        }
        Path userFile = userClientConfigPath();
        if (Files.isRegularFile(userFile)) {
            try (InputStream in = Files.newInputStream(userFile)) {
                Properties overlay = new Properties();
                overlay.load(in);
                for (String name : overlay.stringPropertyNames()) {
                    p.setProperty(name, overlay.getProperty(name));
                }
            } catch (IOException ignored) {
            }
        }
        return p;
    }

    /**
     * Si {@code true}, le client peut se connecter à {@code 127.0.0.1} / {@code localhost}. Si {@code false},
     * seules les adresses « style LAN » sont autorisées (voir config).
     */
    public static boolean isAllowLoopback(Properties merged) {
        String sys = System.getProperty("chrionline.client.allow.loopback");
        if (sys != null && !sys.isBlank()) {
            return Boolean.parseBoolean(sys.trim());
        }
        return Boolean.parseBoolean(merged.getProperty("client.allow.loopback", "true"));
    }
}
