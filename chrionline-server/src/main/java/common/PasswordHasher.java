package common;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Hachage des mots de passe avec BCrypt pour la colonne {@code hash_password}. Les anciennes bases peuvent
 * encore contenir du texte brut : {@link #verify} l’accepte temporairement ; {@link chrionline.Authentification}
 * réécrit un vrai hash après une connexion réussie.
 */
public final class PasswordHasher {

    private static final int BCRYPT_LOG_ROUNDS = 12;

    private PasswordHasher() {}

    /**
     * Produit un nouveau hash BCrypt (sel inclus dans la chaîne) pour INSERT ou UPDATE.
     *
     * @param plainPassword mot de passe en clair
     * @return chaîne stockable en base
     * @throws IllegalArgumentException si {@code plainPassword} est null
     */
    public static String hash(String plainPassword) {
        if (plainPassword == null) {
            throw new IllegalArgumentException("password cannot be null");
        }
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt(BCRYPT_LOG_ROUNDS));
    }

    /**
     * Vérifie un mot de passe à la connexion ou lors d’une confirmation. Si {@code stored} ressemble à un hash
     * BCrypt ({@code $2a$} / {@code $2b$} / {@code $2y$}), utilise {@link BCrypt#checkpw} ; sinon comparaison
     * en clair (migration uniquement).
     */
    public static boolean verify(String plainPassword, String stored) {
        if (plainPassword == null || stored == null || stored.isEmpty()) {
            return false;
        }
        if (isBcryptHash(stored)) {
            try {
                return BCrypt.checkpw(plainPassword, stored);
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
        return plainPassword.equals(stored);
    }

    /**
     * Indique si la base contient encore un mot de passe non haché (à remplacer au prochain login réussi).
     */
    public static boolean needsRehash(String stored) {
        return stored != null && !stored.isEmpty() && !isBcryptHash(stored);
    }

    /** Détecte les préfixes standards des chaînes BCrypt. */
    private static boolean isBcryptHash(String stored) {
        return stored.length() >= 4 && stored.charAt(0) == '$' && stored.charAt(1) == '2';
    }
}
