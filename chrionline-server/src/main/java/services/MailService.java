package services;

import common.ChrionlineLog;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Properties;

/**
 * Envoi de courriels sortants : API HTTP <strong>Resend</strong> (clé API, sans SMTP) ou <strong>SMTP</strong>
 * (ex. Gmail). La configuration est chargée par {@link MailConfigLoader} (classpath, {@code ~/.chrionline/},
 * variables {@code CHRIONLINE_*}) — pas besoin de modifier le code sur la machine serveur.
 */
public final class MailService {

    private static Properties config = new Properties();

    private static final HttpClient HTTP =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    static {
        reload();
    }

    private MailService() {}

    /** Recharge les propriétés depuis {@link MailConfigLoader#load()}. */
    public static void reload() {
        config = MailConfigLoader.load();
    }

    /** Indique si l’envoi est possible (Resend configuré, ou SMTP avec hôte + utilisateur + mot de passe). */
    public static boolean isMailConfigured() {
        if (!getResendApiKey().isEmpty()) {
            return true;
        }
        String host = config.getProperty("mail.smtp.host", "").trim();
        if (host.isEmpty()) {
            return false;
        }
        // SMTP sign-in needs user + password (Gmail: full e-mail + app password)
        return !smtpPassword().isEmpty() && !smtpUser().isEmpty();
    }

    /** Mot de passe SMTP sans espaces superflus. */
    private static String smtpPassword() {
        String raw = config.getProperty("mail.smtp.password", "");
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("\\s+", "");
    }

    /**
     * Adresse expéditrice effective : si {@code mail.from} est vide, retombe sur l’utilisateur SMTP ou une valeur par défaut.
     */
    private static String effectiveMailFrom() {
        String from = config.getProperty("mail.from");
        if (from != null) {
            from = from.trim();
        } else {
            from = "";
        }
        if (!from.isEmpty()) {
            return from;
        }
        String u = smtpUser();
        if (!u.isEmpty()) {
            return u;
        }
        return "noreply@localhost";
    }

    /** Expéditeur par défaut pour l’API Resend si {@code mail.from} est absent. */
    private static String effectiveMailFromResend() {
        String from = config.getProperty("mail.from");
        if (from != null) {
            from = from.trim();
        } else {
            from = "";
        }
        if (!from.isEmpty()) {
            return from;
        }
        return "onboarding@resend.dev";
    }

    /** Identifiant SMTP : {@code mail.smtp.user}, sinon adresse extraite de {@code mail.from}. */
    private static String smtpUser() {
        String u = config.getProperty("mail.smtp.user", "").trim();
        if (!u.isEmpty()) {
            return u;
        }
        String from = config.getProperty("mail.from", "").trim();
        if (from.isEmpty()) {
            return "";
        }
        try {
            return new InternetAddress(from).getAddress();
        } catch (Exception e) {
            return "";
        }
    }

    /** Au démarrage du serveur : affiche la config SMTP (sans le mot de passe). */
    public static void logMailDiagnostics() {
        if (!getResendApiKey().isEmpty()) {
            ChrionlineLog.info("Mail : Resend (clé API présente).");
            return;
        }
        String host = config.getProperty("mail.smtp.host", "").trim();
        if (host.isEmpty()) {
            return;
        }
        var userFile = MailConfigLoader.userConfigFilePath();
        ChrionlineLog.info(
                "Mail SMTP : fichier utilisateur = "
                        + userFile.toAbsolutePath()
                        + (Files.isRegularFile(userFile) ? " (présent)" : " (absent — créez-le pour la config SMTP)"));
        String user = smtpUser();
        int passLen = smtpPassword().length();
        ChrionlineLog.info(
                "Mail SMTP : "
                        + host
                        + ":"
                        + config.getProperty("mail.smtp.port", "587")
                        + " — utilisateur="
                        + (user.isEmpty() ? "(vide : renseignez mail.smtp.user ou mail.from avec votre Gmail complet)"
                                : user));
        ChrionlineLog.info(
                "Mail SMTP : longueur mot de passe application = "
                        + passLen
                        + (passLen > 0 && passLen != 16 ? " (Gmail : en général 16 caractères)" : ""));
        if (passLen == 0 && !host.isEmpty()) {
            ChrionlineLog.info(
                    "Mail SMTP : astuce — le mot de passe doit être sur la MÊME ligne que mail.smtp.password= "
                            + "dans %USERPROFILE%\\.chrionline\\email-config.properties (ou variable CHRIONLINE_SMTP_PASSWORD). "
                            + "Une ligne « seule » avec les 16 lettres n’est pas lue comme mot de passe.");
        }
    }

    /** @deprecated Utiliser {@link #isMailConfigured()}. */
    @Deprecated
    public static boolean isSmtpConfigured() {
        return isMailConfigured();
    }

    /** Clé API Resend depuis les propriétés. */
    private static String getResendApiKey() {
        return config.getProperty("resend.api.key", "").trim();
    }

    /**
     * Envoie un message texte ; si aucune config mail, journalise le contenu en console (repli).
     *
     * @param to destinataire
     * @param subject sujet
     * @param body corps en texte brut
     */
    public static void sendPlain(String to, String subject, String body) {
        if (to == null || to.isBlank()) {
            return;
        }
        if (!isMailConfigured()) {
            ChrionlineLog.info(
                    "[ChriOnline mail — aucune config mail (Resend ou SMTP). Copie console] À : "
                            + to
                            + "\nSujet : "
                            + subject
                            + "\n"
                            + body);
            return;
        }
        if (!getResendApiKey().isEmpty()) {
            sendViaResend(to, subject, body);
            return;
        }
        sendViaSmtp(to, subject, body);
    }

    /** Envoi via l’API HTTPS Resend. */
    private static void sendViaResend(String to, String subject, String body) {
        String apiKey = getResendApiKey();
        String from = effectiveMailFromResend();
        try {
            String json =
                    "{\"from\":\""
                            + escJson(from)
                            + "\",\"to\":[\""
                            + escJson(to.trim())
                            + "\"],\"subject\":\""
                            + escJson(subject)
                            + "\",\"text\":\""
                            + escJson(body)
                            + "\"}";
            HttpRequest req =
                    HttpRequest.newBuilder()
                            .uri(URI.create("https://api.resend.com/emails"))
                            .timeout(Duration.ofSeconds(30))
                            .header("Authorization", "Bearer " + apiKey)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                            .build();
            HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
            int code = res.statusCode();
            if (code < 200 || code >= 300) {
                ChrionlineLog.err("[ChriOnline mail] Resend HTTP " + code + " : " + res.body());
                fallbackConsole(to, subject, body);
            }
        } catch (Exception e) {
            ChrionlineLog.err("[ChriOnline mail] Resend : " + e.getMessage());
            fallbackConsole(to, subject, body);
        }
    }

    /** Envoi via Jakarta Mail (SMTP). */
    private static void sendViaSmtp(String to, String subject, String body) {
        try {
            Properties props = new Properties();
            for (String name : config.stringPropertyNames()) {
                if (name.startsWith("mail.")) {
                    // Password is supplied only via Authenticator — avoid duplicating it in Session props.
                    if ("mail.smtp.password".equals(name)) {
                        continue;
                    }
                    props.setProperty(name, config.getProperty(name));
                }
            }
            if (!props.containsKey("mail.smtp.connectiontimeout")) {
                props.setProperty("mail.smtp.connectiontimeout", "15000");
            }
            if (!props.containsKey("mail.smtp.timeout")) {
                props.setProperty("mail.smtp.timeout", "15000");
            }
            if (!props.containsKey("mail.smtp.writetimeout")) {
                props.setProperty("mail.smtp.writetimeout", "15000");
            }
            final String user = smtpUser();
            final String pass = smtpPassword();
            if (user.isEmpty()) {
                ChrionlineLog.err(
                        "[ChriOnline mail] SMTP : renseignez mail.smtp.user (e-mail Gmail complet) ou mail.from.");
                fallbackConsole(to, subject, body);
                return;
            }
            Session session =
                    Session.getInstance(
                            props,
                            user.isEmpty()
                                    ? null
                                    : new Authenticator() {
                                        @Override
                                        protected PasswordAuthentication getPasswordAuthentication() {
                                            return new PasswordAuthentication(user, pass);
                                        }
                                    });
            MimeMessage msg = new MimeMessage(session);
            String from = effectiveMailFrom();
            msg.setFrom(new InternetAddress(from));
            msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to, false));
            msg.setSubject(subject, "UTF-8");
            msg.setText(body, "UTF-8");
            Transport.send(msg);
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            ChrionlineLog.err("[ChriOnline mail] Échec envoi SMTP : " + e.getMessage());
            ChrionlineLog.err("[ChriOnline mail] Détail : " + root.getMessage());
            fallbackConsole(to, subject, body);
        }
    }

    /** Repli : affiche le message dans la console du serveur. */
    private static void fallbackConsole(String to, String subject, String body) {
        ChrionlineLog.info(
                "[ChriOnline mail — repli console] À : "
                        + to
                        + "\nSujet : "
                        + subject
                        + "\n"
                        + body);
    }

    /** Échappe une chaîne pour inclusion dans un corps JSON HTTP. */
    private static String escJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /** E-mail « vérification d’adresse » avec code à six chiffres. */
    public static void sendEmailVerification(String to, String code) {
        sendPlain(
                to,
                "ChriOnline — vérification de l’e-mail",
                "Votre code de vérification : "
                        + code
                        + "\nIl expire dans 15 minutes. Si vous n’êtes pas à l’origine de cette demande, ignorez ce message.");
    }

    /** E-mail « réinitialisation du mot de passe » avec code à six chiffres. */
    public static void sendPasswordResetCode(String to, String code) {
        sendPlain(
                to,
                "ChriOnline — réinitialisation du mot de passe",
                "Votre code de réinitialisation : "
                        + code
                        + "\nIl expire dans 15 minutes.");
    }

    /** E-mail OTP pour modifications sensibles du profil (mot de passe, e-mail, téléphone). */
    public static void sendProfileSecurityCode(String to, String code) {
        sendPlain(
                to,
                "ChriOnline — code de sécurité",
                "Pour confirmer la modification d’informations sensibles sur votre compte, saisissez ce code : "
                        + code
                        + "\nIl expire dans 15 minutes.");
    }
}
