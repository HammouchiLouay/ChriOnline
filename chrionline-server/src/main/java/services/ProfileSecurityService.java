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
 * Codes à usage unique envoyés à l’e-mail <strong>vérifié</strong> pour les changements sensibles du profil
 * (mot de passe, e-mail, téléphone).
 */
public final class ProfileSecurityService {

    private static final long TTL_MS = 15 * 60 * 1000L;
    private static final Random RND = new Random();
    private static final ConcurrentHashMap<Integer, Pending> OTP = new ConcurrentHashMap<>();

    private static final class Pending {
        final String code;
        final long expiryMs;

        Pending(String code, long expiryMs) {
            this.code = code;
            this.expiryMs = expiryMs;
        }
    }

    private ProfileSecurityService() {}

    /** Client : {@code PROFILE_OTP_SEND}, charge utile {@code {"userId":"1"}}. */
    public static Message sendOtp(Message request) {
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
                if (!u.isEmailVerified()) {
                    return err(request, "EMAIL_NOT_VERIFIED");
                }
                String code = String.format("%06d", RND.nextInt(1_000_000));
                OTP.put(userId, new Pending(code, System.currentTimeMillis() + TTL_MS));
                MailService.sendProfileSecurityCode(u.get_email(), code);
            }
            return new Message(request.getType(), request.getRequestId(), "SUCCESS", "{}", "");
        } catch (NumberFormatException e) {
            return err(request, "INVALID_PAYLOAD");
        } catch (Exception e) {
            return err(request, "DB_ERROR");
        }
    }

    /** Vérifie le code et le consomme en cas de succès (une seule utilisation). */
    public static boolean verifyAndConsumeProfileOtp(int userId, String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        Pending p = OTP.get(userId);
        if (p == null || System.currentTimeMillis() > p.expiryMs) {
            OTP.remove(userId);
            return false;
        }
        if (!p.code.equals(code.trim())) {
            return false;
        }
        OTP.remove(userId);
        return true;
    }

    /** Supprime l’OTP en attente pour cet utilisateur (ex. suppression de compte). */
    public static void clearPendingOtpForUser(int userId) {
        OTP.remove(userId);
    }

    private static Message err(Message request, String code) {
        return new Message(request.getType(), request.getRequestId(), "ERROR", "", code);
    }
}
