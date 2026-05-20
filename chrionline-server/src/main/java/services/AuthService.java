package services;

import chrionline.Authentification;
import chrionline.BaseDonnees;
import chrionline.User;
import chrionline.UserDAO;
import common.ChrionlineLog;
import common.JsonUtil;
import common.Message;
import common.PasswordHasher;
import chrionline.PhoneNumberLookup;
import server.SessionRegistry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Point d’entrée socket pour {@code LOGIN} et {@code REGISTER} : table MySQL {@code user} via {@link UserDAO}.
 * Les charges utiles sont des objets JSON plats (voir {@link JsonUtil#toMap(String)}).
 */
public final class AuthService {

    private AuthService() {}

    private static final DateTimeFormatter LOGIN_TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    /**
     * Authentifie un utilisateur : charge utile {@code email} (e-mail ou téléphone) + {@code password}.
     * Réponse JSON : {@code userId}, {@code username}, {@code email}, {@code phone}, {@code emailVerified}.
     */
    public static Message login(Message request) {
        String emailOrPhone = null;
        try {
            String raw = request.getPayload();
            if (raw == null || raw.trim().isEmpty()) {
                logLoginAttempt(null, false, "EMPTY_PAYLOAD", null);
                return err(request, "EMPTY_PAYLOAD");
            }
            Map<String, String> data = JsonUtil.toMap(raw);
            emailOrPhone = data.get("email");
            String password = data.get("password");
            if (emailOrPhone == null || password == null) {
                logLoginAttempt(emailOrPhone, false, "MISSING_FIELDS", null);
                return err(request, "MISSING_FIELDS");
            }
            try (Connection c = BaseDonnees.getConnection()) {
                Authentification auth = new Authentification(c);
                User u = auth.loginByEmailOrPhone(emailOrPhone.trim(), password);
                if (u == null) {
                    logLoginAttempt(emailOrPhone, false, "BAD_CREDENTIALS", null);
                    return err(request, "BAD_CREDENTIALS");
                }
                String sessionToken = SessionRegistry.issueToken(u.get_id_user());
                String payload =
                        "{"
                                + "\"userId\":" + u.get_id_user() + ","
                                + "\"username\":\"" + esc(u.get_username()) + "\","
                                + "\"email\":\"" + esc(u.get_email()) + "\","
                                + "\"phone\":" + u.get_phone_number() + ","
                                + "\"emailVerified\":" + u.isEmailVerified() + ","
                                + "\"role\":\"" + esc(u.get_role()) + "\","
                                + "\"sessionToken\":\"" + sessionToken + "\""
                                + "}";
                logLoginAttempt(emailOrPhone, true, "", u.get_id_user());
                return new Message("LOGIN", request.getRequestId(), "SUCCESS", payload, "");
            }
        } catch (Exception e) {
            logLoginAttempt(emailOrPhone, false, "DB_ERROR", null);
            return err(request, "DB_ERROR");
        }
    }

    /**
     * Crée un compte : {@code username}, {@code email}, {@code password}, {@code phone} ; rôle par défaut CLIENT.
     * Envoie ensuite un premier e-mail de vérification si la config mail le permet.
     */
    public static Message register(Message request) {
        try {
            String raw = request.getPayload();
            if (raw == null || raw.trim().isEmpty()) {
                return err(request, "EMPTY_PAYLOAD");
            }
            Map<String, String> data = JsonUtil.toMap(raw);
            String username = data.get("username");
            String email = data.get("email");
            String password = data.get("password");
            String phoneStr = data.get("phone");
            if (username == null || email == null || password == null || phoneStr == null) {
                return err(request, "MISSING_FIELDS");
            }
            String phoneDigits = PhoneNumberLookup.digitsOnly(phoneStr.trim());
            Integer phoneBox = PhoneNumberLookup.parseStoredPhoneInt(phoneDigits);
            if (phoneBox == null) {
                return err(request, "INVALID_PHONE");
            }
            int phone = phoneBox;
            try (Connection c = BaseDonnees.getConnection()) {
                UserDAO dao = new UserDAO(c);
                int id = dao.getNextUserId();
                User u = new User();
                u.set_id_user(id);
                u.set_username(username.trim());
                u.set_email(email.trim());
                u.set_phone_number(phone);
                u.set_hash_password(PasswordHasher.hash(password));
                u.set_date_creation(Date.valueOf(LocalDate.now()));
                u.set_role(data.getOrDefault("role", "CLIENT"));
                u.setEmailVerified(false);
                Authentification auth = new Authentification(c);
                if (!auth.register(u)) {
                    return err(request, "EMAIL_OR_PHONE_EXISTS");
                }
                EmailVerificationService.scheduleInitialVerificationEmail(id);
                String sessionToken = SessionRegistry.issueToken(id);
                String payload =
                        "{"
                                + "\"userId\":" + id + ","
                                + "\"username\":\"" + esc(username.trim()) + "\","
                                + "\"email\":\"" + esc(email.trim()) + "\","
                                + "\"phone\":" + phone + ","
                                + "\"emailVerified\":false,"
                                + "\"role\":\"" + esc(u.get_role()) + "\","
                                + "\"sessionToken\":\"" + sessionToken + "\""
                                + "}";
                return new Message("REGISTER", request.getRequestId(), "SUCCESS", payload, "");
            }
        } catch (Exception e) {
            return err(request, "DB_ERROR");
        }
    }

    /** Échappe guillemets et antislashs pour inclusion dans un littéral JSON. */
    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** Construit une réponse d’erreur avec le même type et requestId que la requête. */
    private static Message err(Message request, String code) {
        return new Message(request.getType(), request.getRequestId(), "ERROR", "", code);
    }

    /**
     * Append-only audit log for LOGIN attempts.
     *
     * <p>Location: {@code ${user.home}/.chrionline/login-audit.log}
     *
     * <p>Format (one line): {@code yyyy-MM-dd HH:mm:ss | LOGIN | OK/FAIL | userId=? | id=<masked> | error=<code>}
     */
    private static void logLoginAttempt(String emailOrPhone, boolean success, String errorCode, Integer userId) {
        try {
            String home = System.getProperty("user.home");
            if (home == null || home.isBlank()) {
                return;
            }
            Path dir = Path.of(home, ".chrionline");
            Files.createDirectories(dir);
            Path file = dir.resolve("login-audit.log");

            String ts = LOGIN_TS.format(Instant.now());
            String status = success ? "OK" : "FAIL";
            String uid = userId != null ? String.valueOf(userId) : "?";
            String idMasked = maskIdentifier(emailOrPhone);
            String err = errorCode != null ? errorCode : "";

            String line =
                    ts
                            + " | LOGIN | "
                            + status
                            + " | userId="
                            + uid
                            + " | id="
                            + idMasked
                            + (err.isEmpty() ? "" : (" | error=" + err))
                            + System.lineSeparator();

            Files.writeString(file, line, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
            ChrionlineLog.info("[login-audit] " + line.strip());
        } catch (Exception ignored) {
            // best-effort logging; never fail the login call because of logging
        }
    }

    private static String maskIdentifier(String emailOrPhone) {
        if (emailOrPhone == null) {
            return "-";
        }
        String s = emailOrPhone.trim();
        if (s.isEmpty()) {
            return "-";
        }
        // If it looks like a phone number, keep only last 2 digits.
        String digits = s.replaceAll("\\D+", "");
        if (digits.length() >= 6 && digits.length() >= (s.length() * 0.6)) {
            String last2 = digits.substring(Math.max(0, digits.length() - 2));
            return "phone=***" + last2;
        }
        // Otherwise treat as email/username; keep first char and domain if present.
        int at = s.indexOf('@');
        if (at > 0 && at < s.length() - 1) {
            String local = s.substring(0, at);
            String domain = s.substring(at + 1);
            String localMasked = local.length() <= 1 ? "*" : (local.charAt(0) + "***");
            return "email=" + localMasked + "@" + domain;
        }
        if (s.length() <= 2) {
            return "*";
        }
        return s.charAt(0) + "***" + s.charAt(s.length() - 1);
    }
}
