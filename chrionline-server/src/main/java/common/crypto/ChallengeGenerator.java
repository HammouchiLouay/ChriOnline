package common.crypto;

import java.security.SecureRandom;
import java.util.Base64;

/** Génère des challenges aléatoires (anti-rejeu) pour un flux challenge-response. */
public final class ChallengeGenerator {

    private static final SecureRandom RNG = new SecureRandom();

    private ChallengeGenerator() {}

    /**
     * Génère un challenge Base64 URL-safe (sans padding) d'environ 32 octets.
     * Cette chaîne doit être signée telle quelle côté client (UTF-8).
     */
    public static String generateChallenge() {
        byte[] random = new byte[32];
        RNG.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }
}

