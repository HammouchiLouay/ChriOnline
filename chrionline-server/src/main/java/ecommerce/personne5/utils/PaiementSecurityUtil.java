package ecommerce.personne5.utils;

/** Vérification de sécurité simulée (toujours vraie en démo). */
public class PaiementSecurityUtil {
    private PaiementSecurityUtil() {
    }

    public static boolean verifierTransaction() {
        LoggerUtil.info("Verification de securite en cours...");
        LoggerUtil.success("Transaction securisee - SSL Verified.");
        return true;
    }
}