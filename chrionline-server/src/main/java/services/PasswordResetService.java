package services;

import chrionline.BaseDonnees;
import chrionline.User;
import chrionline.UserDAO;
import common.ChrionlineLog;
import common.JsonUtil;
import common.MaskingUtil;
import common.Message;
import common.PasswordHasher;
import chrionline.PhoneNumberLookup;

import java.sql.Connection;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Récupération de mot de passe : code à six chiffres par e-mail (adresses vérifiées uniquement) ou par téléphone
 * (affichage console en démo). Les réponses peuvent inclure des indices masqués sur le contact.
 */
public final class PasswordResetService {

    private static final long TTL_MS = 15 * 60 * 1000L;
    private static final Random RND = new Random();

    private static final ConcurrentHashMap<String, Pending> PENDING = new ConcurrentHashMap<>();

    private static final class Pending {
        final int userId;
        final String code;
        final long expiryMs;

        Pending(int userId, String code, long expiryMs) {
            this.userId = userId;
            this.code = code;
            this.expiryMs = expiryMs;
        }
    }

    private PasswordResetService() {}

    /** Clé de stockage en mémoire pour une réinitialisation par e-mail. */
    private static String keyEmail(String email) {
        return "e:" + email.trim().toLowerCase();
    }

    /** Clé de stockage pour une réinitialisation par numéro normalisé. */
    private static String keyPhoneDigits(String digits) {
        return "p:" + digits;
    }

    /**
     * {@code FORGOT_PASSWORD} : envoie un code (e-mail ou journal téléphone) ; charge utile avec {@code email} ou
     * {@code phone}.
     */
    public static Message forgotPassword(Message request) {
        try {
            String raw = request.getPayload();
            if (raw == null || raw.trim().isEmpty()) {
                return err(request, "EMPTY_PAYLOAD");
            }
            Map<String, String> data = JsonUtil.toMap(raw);
            String email = data.get("email");
            String phoneStr = data.get("phone");
            String maskedEmail = null;
            String maskedPhone = null;
            try (Connection c = BaseDonnees.getConnection()) {
                UserDAO dao = new UserDAO(c);
                User u = null;
                String storageKey = null;
                if (email != null && !email.isBlank()) {
                    u = dao.findByEmail(email.trim());
                    if (u != null) {
                        if (!u.isEmailVerified()) {
                            return err(request, "EMAIL_NOT_VERIFIED");
                        }
                        storageKey = keyEmail(email);
                        String code = String.format("%06d", RND.nextInt(1_000_000));
                        long exp = System.currentTimeMillis() + TTL_MS;
                        PENDING.put(storageKey, new Pending(u.get_id_user(), code, exp));
                        MailService.sendPasswordResetCode(u.get_email(), code);
                        maskedEmail = MaskingUtil.maskEmail(u.get_email());
                    }
                } else if (phoneStr != null && !phoneStr.isBlank()) {
                    String digits = PhoneNumberLookup.digitsOnly(phoneStr.trim());
                    if (digits.isEmpty()) {
                        return err(request, "INVALID_PHONE");
                    }
                    Integer phoneInt = PhoneNumberLookup.parseStoredPhoneInt(digits);
                    if (phoneInt == null) {
                        return err(request, "INVALID_PHONE");
                    }
                    u = dao.findByPhoneNumber(phoneInt);
                    storageKey = keyPhoneDigits(String.valueOf(phoneInt));
                    if (u != null) {
                        String code = String.format("%06d", RND.nextInt(1_000_000));
                        long exp = System.currentTimeMillis() + TTL_MS;
                        PENDING.put(storageKey, new Pending(u.get_id_user(), code, exp));
                        ChrionlineLog.info(
                                "[ChriOnline] Réinitialisation par téléphone — code pour "
                                        + storageKey
                                        + " : "
                                        + code);
                        maskedPhone = MaskingUtil.maskPhoneDigits(digits);
                    }
                } else {
                    return err(request, "MISSING_FIELDS");
                }
            }
            String payload = buildForgotPayload(maskedEmail, maskedPhone);
            return new Message("FORGOT_PASSWORD", request.getRequestId(), "SUCCESS", payload, "");
        } catch (Exception e) {
            return err(request, "DB_ERROR");
        }
    }

    /** Construit le JSON de réponse avec indices masqués. */
    private static String buildForgotPayload(String maskedEmail, String maskedPhone) {
        if (maskedEmail != null && !maskedEmail.isEmpty()) {
            return "{\"maskedEmail\":\"" + esc(maskedEmail) + "\"}";
        }
        if (maskedPhone != null && !maskedPhone.isEmpty()) {
            return "{\"maskedPhone\":\"" + esc(maskedPhone) + "\"}";
        }
        return "{}";
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * {@code RESET_PASSWORD} : vérifie le code puis met à jour le hachage du mot de passe (e-mail ou téléphone
     * cohérent avec l’envoi).
     */
    public static Message resetPassword(Message request) {
        try {
            String raw = request.getPayload();
            if (raw == null || raw.trim().isEmpty()) {
                return err(request, "EMPTY_PAYLOAD");
            }
            Map<String, String> data = JsonUtil.toMap(raw);
            String code = data.get("code");
            String newPw = data.get("newPassword");
            if (code == null || newPw == null || newPw.length() < 4) {
                return err(request, "INVALID");
            }
            String email = data.get("email");
            String phoneStr = data.get("phone");
            String storageKey;
            if (email != null && !email.isBlank()) {
                storageKey = keyEmail(email);
            } else if (phoneStr != null && !phoneStr.isBlank()) {
                String digits = PhoneNumberLookup.digitsOnly(phoneStr.trim());
                if (digits.isEmpty()) {
                    return err(request, "INVALID_PHONE");
                }
                Integer phoneInt = PhoneNumberLookup.parseStoredPhoneInt(digits);
                if (phoneInt == null) {
                    return err(request, "INVALID_PHONE");
                }
                storageKey = keyPhoneDigits(String.valueOf(phoneInt));
            } else {
                return err(request, "MISSING_FIELDS");
            }
            Pending p = PENDING.get(storageKey);
            if (p == null || System.currentTimeMillis() > p.expiryMs || !p.code.equals(code.trim())) {
                return err(request, "INVALID_CODE");
            }
            try (Connection c = BaseDonnees.getConnection()) {
                UserDAO dao = new UserDAO(c);
                dao.updatePassword(p.userId, PasswordHasher.hash(newPw.trim()));
            }
            PENDING.remove(storageKey);
            return new Message("RESET_PASSWORD", request.getRequestId(), "SUCCESS", "{}", "");
        } catch (Exception e) {
            return err(request, "DB_ERROR");
        }
    }

    /** Supprime tous les codes de réinitialisation en mémoire associés à cet utilisateur. */
    public static void clearPendingForUser(int userId) {
        PENDING.entrySet().removeIf(e -> e.getValue().userId == userId);
    }

    private static Message err(Message request, String code) {
        return new Message(request.getType(), request.getRequestId(), "ERROR", "", code);
    }
}
