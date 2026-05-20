package services;

import chrionline.BaseDonnees;
import chrionline.User;
import chrionline.UserDAO;
import common.JsonUtil;
import common.Message;

import java.sql.Connection;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Codes de vérification e-mail (inscription et page compte). Durée de vie courte, stockage en mémoire sur le serveur
 * (perdu au redémarrage — l’utilisateur peut redemander un code).
 */
public final class EmailVerificationService {

    private static final long TTL_MS = 15 * 60 * 1000L;
    private static final Random RND = new Random();
    private static final ConcurrentHashMap<Integer, Pending> PENDING = new ConcurrentHashMap<>();

    private static final class Pending {
        final String code;
        final long expiryMs;

        Pending(String code, long expiryMs) {
            this.code = code;
            this.expiryMs = expiryMs;
        }
    }

    private EmailVerificationService() {}

    /** Après inscription : envoie le premier e-mail de vérification dans un thread en arrière-plan. */
    public static void scheduleInitialVerificationEmail(int userId) {
        Thread t =
                new Thread(
                        () -> {
                            try {
                                sendCodeForUser(userId);
                            } catch (Exception ignored) {
                            }
                        },
                        "chrionline-email-verify");
        t.setDaemon(true);
        t.start();
    }

    /** Charge l’utilisateur et émet un code s’il existe et n’est pas déjà vérifié. */
    private static void sendCodeForUser(int userId) throws Exception {
        try (Connection c = BaseDonnees.getConnection()) {
            UserDAO dao = new UserDAO(c);
            User u = dao.findById(userId);
            if (u == null || u.isEmailVerified()) {
                return;
            }
            issueCode(u);
        }
    }

    /** Génère un code à six chiffres, le mémorise et envoie l’e-mail. */
    private static void issueCode(User u) {
        String code = String.format("%06d", RND.nextInt(1_000_000));
        PENDING.put(u.get_id_user(), new Pending(code, System.currentTimeMillis() + TTL_MS));
        MailService.sendEmailVerification(u.get_email(), code);
    }

    /** Client : {@code EMAIL_VERIFY_SEND}, charge utile {@code {"userId":"1"}}. */
    public static Message send(Message request) {
        try {
            Map<String, String> data = JsonUtil.toMap(request.getPayload());
            if (!data.containsKey("userId")) {
                return err(request, "MISSING_FIELDS");
            }
            int userId = Integer.parseInt(data.get("userId").trim());
            try (Connection c = BaseDonnees.getConnection()) {
                UserDAO dao = new UserDAO(c);
                User u = dao.findById(userId);
                if (u == null) {
                    return err(request, "NOT_FOUND");
                }
                if (u.isEmailVerified()) {
                    return new Message(
                            request.getType(),
                            request.getRequestId(),
                            "SUCCESS",
                            "{\"alreadyVerified\":true}",
                            "");
                }
                issueCode(u);
                return new Message(request.getType(), request.getRequestId(), "SUCCESS", "{}", "");
            }
        } catch (NumberFormatException e) {
            return err(request, "INVALID_PAYLOAD");
        } catch (Exception e) {
            return err(request, "DB_ERROR");
        }
    }

    /** Client : {@code EMAIL_VERIFY_CONFIRM}, charge utile {@code {"userId":"1","code":"123456"}}. */
    public static Message confirm(Message request) {
        try {
            Map<String, String> data = JsonUtil.toMap(request.getPayload());
            if (!data.containsKey("userId") || !data.containsKey("code")) {
                return err(request, "MISSING_FIELDS");
            }
            int userId = Integer.parseInt(data.get("userId").trim());
            String code = data.get("code").trim();
            Pending p = PENDING.get(userId);
            if (p == null || System.currentTimeMillis() > p.expiryMs) {
                PENDING.remove(userId);
                return err(request, "INVALID_CODE");
            }
            if (!p.code.equals(code)) {
                return err(request, "INVALID_CODE");
            }
            try (Connection c = BaseDonnees.getConnection()) {
                UserDAO dao = new UserDAO(c);
                User u = dao.findById(userId);
                if (u == null) {
                    return err(request, "NOT_FOUND");
                }
                dao.updateEmailVerified(userId, true);
            }
            PENDING.remove(userId);
            return new Message(request.getType(), request.getRequestId(), "SUCCESS", "{\"emailVerified\":true}", "");
        } catch (NumberFormatException e) {
            return err(request, "INVALID_PAYLOAD");
        } catch (Exception e) {
            return err(request, "DB_ERROR");
        }
    }

    /** Supprime le code en attente pour cet utilisateur (ex. après suppression de compte). */
    public static void clearPendingForUser(int userId) {
        PENDING.remove(userId);
    }

    private static Message err(Message request, String code) {
        return new Message(request.getType(), request.getRequestId(), "ERROR", "", code);
    }
}
