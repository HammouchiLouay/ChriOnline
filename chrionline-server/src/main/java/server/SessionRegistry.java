package server;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Jetons de session émis après {@code LOGIN} / {@code REGISTER} : associe un identifiant opaque à un utilisateur.
 * Le client envoie le jeton (pas un {@code userId} choisi par lui) pour les opérations sensibles comme
 * {@code GET_COMMANDES}.
 */
public final class SessionRegistry {

    private static final ConcurrentHashMap<String, Integer> TOKEN_TO_USER = new ConcurrentHashMap<>();

    private SessionRegistry() {}

    /** Crée un jeton et l’associe à l’utilisateur (remplace tout jeton précédent pour cet utilisateur si besoin). */
    public static String issueToken(int userId) {
        if (userId <= 0) {
            throw new IllegalArgumentException("userId");
        }
        String token = UUID.randomUUID().toString();
        TOKEN_TO_USER.put(token, userId);
        return token;
    }

    /** Résout l’utilisateur authentifié par le jeton, ou {@code null} si inconnu. */
    public static Integer resolveUser(String token) {
        if (token == null) {
            return null;
        }
        String t = token.trim();
        if (t.isEmpty()) {
            return null;
        }
        return TOKEN_TO_USER.get(t);
    }

    /** Invalide un jeton (déconnexion). */
    public static void revoke(String token) {
        if (token == null) {
            return;
        }
        TOKEN_TO_USER.remove(token.trim());
    }
}
