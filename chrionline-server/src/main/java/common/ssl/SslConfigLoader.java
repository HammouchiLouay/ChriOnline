package common.ssl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Charge une configuration SSL/TLS (keystore/truststore) depuis classpath + fichier utilisateur + variables
 * d’environnement, sans imposer de chemin projet.
 *
 * <p><strong>Ordre de fusion (le dernier l’emporte) :</strong>
 *
 * <ol>
 *   <li>Classpath: {@code /ssl-config.properties} (valeurs par défaut empaquetées)
 *   <li>Utilisateur: {@code ~/.chrionline/ssl-config.properties} (Windows: {@code %USERPROFILE%\.chrionline\...})
 *   <li>Variables d’environnement {@code CHRIONLINE_*} (pour les secrets)
 * </ol>
 */
public final class SslConfigLoader {

    private SslConfigLoader() {}

    public static Path userConfigFilePath() {
        return Paths.get(System.getProperty("user.home", ".")).resolve(".chrionline").resolve("ssl-config.properties");
    }

    public static Properties load() {
        Properties p = new Properties();
        loadClasspath(p);
        loadUserHome(p);
        applyEnvironment(p);
        return p;
    }

    private static void loadClasspath(Properties p) {
        try (InputStream in = SslConfigLoader.class.getResourceAsStream("/ssl-config.properties")) {
            if (in != null) {
                p.load(in);
            }
        } catch (IOException ignored) {
        }
    }

    private static void loadUserHome(Properties p) {
        Path file = userConfigFilePath();
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (InputStream in = Files.newInputStream(file)) {
            Properties overlay = new Properties();
            overlay.load(in);
            for (String name : overlay.stringPropertyNames()) {
                String v = overlay.getProperty(name);
                if (v != null) {
                    p.setProperty(name, v);
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static void applyEnvironment(Properties p) {
        putEnv(p, "CHRIONLINE_SERVER_SSL_ENABLED", "server.ssl.enabled");
        putEnv(p, "CHRIONLINE_SERVER_SSL_KEYSTORE_PATH", "server.ssl.key-store.path");
        putEnv(p, "CHRIONLINE_SERVER_SSL_KEYSTORE_PASSWORD", "server.ssl.key-store.password");
        putEnv(p, "CHRIONLINE_SERVER_SSL_KEYSTORE_TYPE", "server.ssl.key-store.type");
        putEnv(p, "CHRIONLINE_SERVER_SSL_KEY_ALIAS", "server.ssl.key-alias");
        putEnv(p, "CHRIONLINE_SERVER_SSL_KEY_PASSWORD", "server.ssl.key.password");
        putEnv(p, "CHRIONLINE_SERVER_SSL_PROTOCOL", "server.ssl.protocol");

        putEnv(p, "CHRIONLINE_CLIENT_SSL_ENABLED", "client.ssl.enabled");
        putEnv(p, "CHRIONLINE_CLIENT_SSL_TRUSTSTORE_PATH", "client.ssl.trust-store.path");
        putEnv(p, "CHRIONLINE_CLIENT_SSL_TRUSTSTORE_PASSWORD", "client.ssl.trust-store.password");
        putEnv(p, "CHRIONLINE_CLIENT_SSL_TRUSTSTORE_TYPE", "client.ssl.trust-store.type");
        putEnv(p, "CHRIONLINE_CLIENT_SSL_PROTOCOL", "client.ssl.protocol");

        putEnv(p, "CHRIONLINE_SERVER_SESSION_RSA_KEYSTORE_PATH", "server.crypto.session.rsa.keystore.path");
        putEnv(p, "CHRIONLINE_SERVER_SESSION_RSA_KEYSTORE_PASSWORD", "server.crypto.session.rsa.keystore.password");
        putEnv(p, "CHRIONLINE_SERVER_SESSION_RSA_KEY_ALIAS", "server.crypto.session.rsa.key.alias");

        putEnv(p, "CHRIONLINE_STORAGE_CRYPTO_ENABLED", "storage.crypto.enabled");
        putEnv(p, "CHRIONLINE_STORAGE_AES_KEY_BASE64", "storage.crypto.aes.key.base64");
    }

    private static void putEnv(Properties p, String envKey, String propKey) {
        String v = System.getenv(envKey);
        if (v != null && !v.isBlank()) {
            p.setProperty(propKey, v.trim());
        }
    }
}

