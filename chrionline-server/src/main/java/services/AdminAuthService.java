package services;

import chrionline.BaseDonnees;
import chrionline.User;
import chrionline.UserDAO;
import common.JsonUtil;
import common.Message;
import common.crypto.ChallengeGenerator;
import common.crypto.PemKeyUtil;
import common.crypto.RsaSignatureUtil;
import server.SessionRegistry;

import java.security.PublicKey;
import java.sql.Connection;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Authentification admin sans mot de passe via challenge-response RSA.
 *
 * <p>Flux (tutorial):
 * <ol>
 *   <li>Client demande un challenge pour un compte admin (par e-mail)</li>
 *   <li>Serveur génère challenge unique et le garde en mémoire (TTL court)</li>
 *   <li>Client signe le challenge avec sa clé privée</li>
 *   <li>Serveur vérifie la signature avec la clé publique stockée en base</li>
 * </ol>
 */
public final class AdminAuthService {

    private static final long CHALLENGE_TTL_MS = 30_000L;

    private static final class PendingChallenge {
        final int userId;
        final String challenge;
        final long expiresAtMs;

        PendingChallenge(int userId, String challenge, long expiresAtMs) {
            this.userId = userId;
            this.challenge = challenge;
            this.expiresAtMs = expiresAtMs;
        }
    }

    private static final ConcurrentHashMap<String, PendingChallenge> PENDING = new ConcurrentHashMap<>();

    private AdminAuthService() {}

    /**
     * {@code ADMIN_CHALLENGE_REQUEST} : payload {@code {"email":"admin@..."}}
     * Réponse SUCCESS : {@code {"challengeId":"...","challenge":"...","userId":123}}
     */
    public static Message requestChallenge(Message request) {
        try {
            Map<String, String> data = JsonUtil.toMap(safePayload(request));
            String email = data.get("email");
            if (email == null || email.isBlank()) {
                return err(request, "MISSING_FIELDS");
            }
            try (Connection c = BaseDonnees.getConnection()) {
                UserDAO dao = new UserDAO(c);
                User u = dao.findByEmail(email.trim());
                if (u == null) {
                    return err(request, "NOT_FOUND");
                }
                if (!"ADMIN".equalsIgnoreCase(u.get_role())) {
                    return err(request, "FORBIDDEN");
                }
                if (u.getAdminPublicKeyPem().isBlank()) {
                    return err(request, "ADMIN_KEY_MISSING");
                }
                String challenge = ChallengeGenerator.generateChallenge();
                String challengeId = UUID.randomUUID().toString();
                PENDING.put(challengeId, new PendingChallenge(u.get_id_user(), challenge, now() + CHALLENGE_TTL_MS));
                String payload =
                        "{"
                                + "\"challengeId\":\"" + esc(challengeId) + "\","
                                + "\"challenge\":\"" + esc(challenge) + "\","
                                + "\"userId\":" + u.get_id_user()
                                + "}";
                return new Message("ADMIN_CHALLENGE_REQUEST", request.getRequestId(), "SUCCESS", payload, "");
            }
        } catch (Exception e) {
            return err(request, "DB_ERROR");
        }
    }

    /**
     * {@code ADMIN_CHALLENGE_VERIFY} : payload
     * {@code {"challengeId":"...","signatureB64":"..."}}
     *
     * <p>Réponse SUCCESS : payload identique à LOGIN (inclut {@code sessionToken}) mais sans mot de passe.
     */
    public static Message verifyChallenge(Message request) {
        try {
            Map<String, String> data = JsonUtil.toMap(safePayload(request));
            String challengeId = data.get("challengeId");
            String signatureB64 = data.get("signatureB64");
            if (challengeId == null || signatureB64 == null || challengeId.isBlank() || signatureB64.isBlank()) {
                return err(request, "MISSING_FIELDS");
            }
            PendingChallenge pc = PENDING.remove(challengeId.trim());
            if (pc == null) {
                return err(request, "CHALLENGE_NOT_FOUND");
            }
            if (now() > pc.expiresAtMs) {
                return err(request, "CHALLENGE_EXPIRED");
            }
            byte[] sigBytes;
            try {
                sigBytes = Base64.getDecoder().decode(signatureB64.trim());
            } catch (IllegalArgumentException e) {
                return err(request, "INVALID_SIGNATURE");
            }
            try (Connection c = BaseDonnees.getConnection()) {
                UserDAO dao = new UserDAO(c);
                User u = dao.findById(pc.userId);
                if (u == null) {
                    return err(request, "NOT_FOUND");
                }
                if (!"ADMIN".equalsIgnoreCase(u.get_role())) {
                    return err(request, "FORBIDDEN");
                }
                String pubPem = u.getAdminPublicKeyPem();
                if (pubPem.isBlank()) {
                    return err(request, "ADMIN_KEY_MISSING");
                }
                PublicKey pub = PemKeyUtil.parseX509PublicKeyPem(pubPem);
                boolean ok = RsaSignatureUtil.verifyChallenge(pc.challenge, sigBytes, pub);
                if (!ok) {
                    return err(request, "BAD_SIGNATURE");
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
                                + "\"sessionToken\":\"" + esc(sessionToken) + "\""
                                + "}";
                return new Message("ADMIN_CHALLENGE_VERIFY", request.getRequestId(), "SUCCESS", payload, "");
            }
        } catch (Exception e) {
            return err(request, "DB_ERROR");
        }
    }

    /** Nettoyage best-effort des challenges expirés (appel optionnel). */
    public static void cleanupExpired() {
        long now = now();
        PENDING.entrySet().removeIf(e -> now > e.getValue().expiresAtMs);
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private static String safePayload(Message request) {
        String raw = request != null ? request.getPayload() : null;
        if (raw == null || raw.trim().isEmpty()) {
            return "{}";
        }
        return raw.trim();
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static Message err(Message request, String code) {
        String type = request != null ? request.getType() : "ADMIN";
        String rid = request != null ? request.getRequestId() : "0";
        return new Message(type, rid, "ERROR", "", code);
    }
}

