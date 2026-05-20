package services;

import chrionline.BaseDonnees;
import chrionline.User;
import chrionline.UserDAO;
import common.JsonUtil;
import common.Message;
import common.PasswordHasher;
import persistence.AccountDeletionDAO;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

/**
 * Supprime le compte authentifié et les lignes associées en base après vérification du mot de passe (et OTP si
 * l’e-mail est vérifié). Efface aussi les états OTP en mémoire pour cet utilisateur.
 */
public final class AccountDeletionService {

    private AccountDeletionService() {}

    /**
     * Charge utile : {@code userId}, {@code currentPassword}, {@code securityOtp} optionnel (obligatoire si e-mail
     * vérifié).
     */
    public static Message deleteAccount(Message request) {
        try {
            String raw = request.getPayload();
            if (raw == null || raw.trim().isEmpty()) {
                return err(request, "EMPTY_PAYLOAD");
            }
            Map<String, String> data = JsonUtil.toMap(raw);
            if (!data.containsKey("userId") || !data.containsKey("currentPassword")) {
                return err(request, "MISSING_FIELDS");
            }
            int userId = Integer.parseInt(data.get("userId").trim());
            String currentPassword = data.get("currentPassword");
            String securityOtp = data.get("securityOtp");

            try (Connection c = BaseDonnees.getConnection()) {
                UserDAO dao = new UserDAO(c);
                User u = dao.findById(userId);
                if (u == null) {
                    return err(request, "NOT_FOUND");
                }
                if (!PasswordHasher.verify(currentPassword, u.get_hash_password())) {
                    return err(request, "BAD_PASSWORD");
                }
                if (u.isEmailVerified()) {
                    if (securityOtp == null
                            || !ProfileSecurityService.verifyAndConsumeProfileOtp(userId, securityOtp.trim())) {
                        return err(request, "BAD_OR_MISSING_OTP");
                    }
                }
                c.setAutoCommit(false);
                try {
                    AccountDeletionDAO.deleteAllForUser(c, userId);
                    c.commit();
                } catch (SQLException e) {
                    c.rollback();
                    throw e;
                } finally {
                    c.setAutoCommit(true);
                }
            }
            PasswordResetService.clearPendingForUser(userId);
            EmailVerificationService.clearPendingForUser(userId);
            ProfileSecurityService.clearPendingOtpForUser(userId);
            return new Message(request.getType(), request.getRequestId(), "SUCCESS", "{}", "");
        } catch (NumberFormatException e) {
            return err(request, "INVALID_PAYLOAD");
        } catch (Exception e) {
            return err(request, "DB_ERROR");
        }
    }

    private static Message err(Message request, String code) {
        return new Message(request.getType(), request.getRequestId(), "ERROR", "", code);
    }
}
