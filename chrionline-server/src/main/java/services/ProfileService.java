package services;

import chrionline.BaseDonnees;
import chrionline.User;
import chrionline.UserDAO;
import common.JsonUtil;
import common.Message;
import common.PasswordHasher;
import chrionline.PhoneNumberLookup;

import java.sql.Connection;
import java.util.Map;

/**
 * Met à jour les champs du profil après vérification du mot de passe actuel ; OTP obligatoire pour les changements
 * sensibles si l’e-mail est vérifié.
 */
public final class ProfileService {

    private ProfileService() {}

    /**
     * {@code UPDATE_PROFILE} : charge utile {@code userId}, {@code currentPassword}, champs optionnels
     * {@code newPassword}, {@code newEmail}, {@code newPhone}, {@code securityOtp}.
     */
    public static Message updateProfile(Message request) {
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
            String newPassword = data.get("newPassword");
            String newEmail = data.get("newEmail");
            String newPhone = data.get("newPhone");
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
                boolean wantsPw = newPassword != null && !newPassword.isBlank();
                boolean wantsEmail =
                        newEmail != null
                                && !newEmail.isBlank()
                                && !newEmail.trim().equals(u.get_email());
                boolean wantsPhone = false;
                if (newPhone != null && !newPhone.isBlank()) {
                    String digits = PhoneNumberLookup.digitsOnly(newPhone.trim());
                    if (!digits.isEmpty()) {
                        Integer np = PhoneNumberLookup.parseStoredPhoneInt(digits);
                        if (np != null) {
                            Integer op = u.get_phone_number();
                            wantsPhone = op == null || !np.equals(op);
                        }
                    }
                }
                boolean sensitive = wantsPw || wantsEmail || wantsPhone;
                if (sensitive) {
                    if (!u.isEmailVerified()) {
                        return err(request, "EMAIL_NOT_VERIFIED");
                    }
                    if (securityOtp == null
                            || !ProfileSecurityService.verifyAndConsumeProfileOtp(userId, securityOtp.trim())) {
                        return err(request, "BAD_OR_MISSING_OTP");
                    }
                }
                if (newPassword != null && !newPassword.isBlank()) {
                    if (newPassword.length() < 4) {
                        return err(request, "INVALID_PASSWORD");
                    }
                    dao.updatePassword(userId, PasswordHasher.hash(newPassword.trim()));
                }
                if (newEmail != null && !newEmail.isBlank()) {
                    String em = newEmail.trim();
                    User other = dao.findByEmail(em);
                    if (other != null && !other.get_id_user().equals(userId)) {
                        return err(request, "EMAIL_TAKEN");
                    }
                    dao.updateEmail(userId, em);
                }
                if (newPhone != null && !newPhone.isBlank()) {
                    String digits = PhoneNumberLookup.digitsOnly(newPhone.trim());
                    if (digits.isEmpty()) {
                        return err(request, "INVALID_PHONE");
                    }
                    Integer phoneBox = PhoneNumberLookup.parseStoredPhoneInt(digits);
                    if (phoneBox == null) {
                        return err(request, "INVALID_PHONE");
                    }
                    int phone = phoneBox;
                    if (dao.phoneNumberExists(phone)) {
                        User byPhone = dao.findByPhoneNumber(phone);
                        if (byPhone != null && !byPhone.get_id_user().equals(userId)) {
                            return err(request, "PHONE_TAKEN");
                        }
                    }
                    dao.updatePhoneNumber(userId, phone);
                }

                User fresh = dao.findById(userId);
                if (fresh == null) {
                    return err(request, "NOT_FOUND");
                }
                String payload =
                        "{"
                                + "\"userId\":" + fresh.get_id_user() + ","
                                + "\"username\":\"" + esc(fresh.get_username()) + "\","
                                + "\"email\":\"" + esc(fresh.get_email()) + "\","
                                + "\"phone\":" + fresh.get_phone_number() + ","
                                + "\"emailVerified\":" + fresh.isEmailVerified()
                                + "}";
                return new Message("UPDATE_PROFILE", request.getRequestId(), "SUCCESS", payload, "");
            }
        } catch (NumberFormatException e) {
            return err(request, "INVALID_PAYLOAD");
        } catch (Exception e) {
            return err(request, "DB_ERROR");
        }
    }

    /** Échappe les guillemets pour JSON. */
    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static Message err(Message request, String code) {
        return new Message(request.getType(), request.getRequestId(), "ERROR", "", code);
    }
}
