package services;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.Set;

/**
 * Charge la configuration e-mail sans modifier les fichiers sous {@code src/main/resources} sur la machine
 * qui exécute le serveur socket ({@code server.ServerMain}).
 *
 * <p><strong>Ordre de fusion (le dernier l’emporte) :</strong>
 *
 * <ol>
 *   <li>Fichier optionnel {@code /email-config.properties} sur le classpath (valeurs par défaut empaquetées)
 *   <li>{@code ~/.chrionline/email-config.properties} (utilisateur ou installateur — pas besoin du dépôt)
 *   <li>Variables d’environnement {@code CHRIONLINE_*} (cloud, Docker, systemd) — souvent pour les secrets
 * </ol>
 *
 * <p>Les e-mails de vérification sont toujours envoyés <em>à</em> l’adresse enregistrée par l’utilisateur ;
 * l’<em>envoi</em> utilise les identifiants applicatifs (SMTP ou Resend), pas le mot de passe personnel de la boîte.
 */
public final class MailConfigLoader {

    /** Clés du fichier utilisateur où une valeur vide signifie « non défini » et ne doit pas effacer les défauts. */
    private static final Set<String> SKIP_BLANK_USER_OVERLAY =
            Set.of("mail.smtp.password", "resend.api.key");

    private MailConfigLoader() {}

    /** Chemin absolu vers {@code ~/.chrionline/email-config.properties} (le fichier peut être absent). */
    public static Path userConfigFilePath() {
        return Paths.get(System.getProperty("user.home", "."))
                .resolve(".chrionline")
                .resolve("email-config.properties");
    }

    /** Fusionne classpath, répertoire utilisateur et variables d’environnement. */
    public static Properties load() {
        Properties p = new Properties();
        loadClasspath(p);
        loadUserHome(p);
        applyEnvironment(p);
        return p;
    }

    /** Charge {@code /email-config.properties} depuis le classpath. */
    private static void loadClasspath(Properties p) {
        try (InputStream in = MailConfigLoader.class.getResourceAsStream("/email-config.properties")) {
            if (in != null) {
                p.load(in);
            }
        } catch (IOException ignored) {
        }
    }

    /** Superpose le fichier utilisateur (Windows : {@code %USERPROFILE%\.chrionline\...}). */
    private static void loadUserHome(Properties p) {
        Path file =
                Paths.get(System.getProperty("user.home", "."))
                        .resolve(".chrionline")
                        .resolve("email-config.properties");
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (InputStream in = Files.newInputStream(file)) {
            Properties overlay = new Properties();
            overlay.load(in);
            for (String name : overlay.stringPropertyNames()) {
                String v = overlay.getProperty(name);
                if (v == null) {
                    continue;
                }
                // A line like `mail.smtp.password=` with nothing after `=` must not wipe a value from
                // classpath / earlier layers — otherwise the template looks "filled" but SMTP has no
                // password (length 0 in MailService diagnostics).
                if (v.isBlank() && SKIP_BLANK_USER_OVERLAY.contains(name)) {
                    continue;
                }
                p.setProperty(name, v);
            }
        } catch (IOException ignored) {
        }
    }

    /** Applique les variables {@code CHRIONLINE_*} aux propriétés correspondantes. */
    private static void applyEnvironment(Properties p) {
        putEnv(p, "CHRIONLINE_RESEND_API_KEY", "resend.api.key");
        putEnv(p, "CHRIONLINE_SMTP_HOST", "mail.smtp.host");
        putEnv(p, "CHRIONLINE_SMTP_PORT", "mail.smtp.port");
        putEnv(p, "CHRIONLINE_SMTP_USER", "mail.smtp.user");
        putEnv(p, "CHRIONLINE_SMTP_PASSWORD", "mail.smtp.password");
        putEnv(p, "CHRIONLINE_MAIL_FROM", "mail.from");
        putEnv(p, "CHRIONLINE_SMTP_STARTTLS", "mail.smtp.starttls.enable");
        putEnv(p, "CHRIONLINE_SMTP_STARTTLS_REQUIRED", "mail.smtp.starttls.required");
        putEnv(p, "CHRIONLINE_SMTP_AUTH", "mail.smtp.auth");
        putEnv(p, "CHRIONLINE_SMTP_SSL_TRUST", "mail.smtp.ssl.trust");
        putEnv(p, "CHRIONLINE_SMTP_SSL_CHECK", "mail.smtp.ssl.checkserveridentity");
    }

    /** Si la variable d’environnement est non vide, remplace la propriété. */
    private static void putEnv(Properties p, String envKey, String propKey) {
        String v = System.getenv(envKey);
        if (v != null && !v.isBlank()) {
            p.setProperty(propKey, v.trim());
        }
    }
}
