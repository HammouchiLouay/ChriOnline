package common;

/**
 * Masquage partiel des e-mails et numéros pour les réponses « mot de passe oublié » : assez d’indices pour que
 * le propriétaire reconnaisse son contact sans divulguer la valeur complète.
 */
public final class MaskingUtil {

    private MaskingUtil() {}

    /** Masque la partie locale et le domaine d’une adresse e-mail. */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "";
        }
        String e = email.trim();
        int at = e.indexOf('@');
        if (at < 1 || at == e.length() - 1) {
            return "***";
        }
        String local = e.substring(0, at);
        String domain = e.substring(at + 1);
        return maskLocalPart(local) + "@" + maskDomainPart(domain);
    }

    /** Masque la partie avant @ (longueur variable). */
    private static String maskLocalPart(String local) {
        if (local.length() <= 2) {
            return local.charAt(0) + "*";
        }
        if (local.length() <= 4) {
            return local.charAt(0) + "***" + local.charAt(local.length() - 1);
        }
        return local.substring(0, 2) + "***" + local.substring(local.length() - 2);
    }

    /** Masque le nom de domaine en gardant une lettre et l’extension. */
    private static String maskDomainPart(String domain) {
        int dot = domain.lastIndexOf('.');
        if (dot <= 0 || dot >= domain.length() - 1) {
            return domain.charAt(0) + "***";
        }
        String name = domain.substring(0, dot);
        String tld = domain.substring(dot);
        if (name.length() <= 1) {
            return "*" + tld;
        }
        return name.charAt(0) + "***" + tld;
    }

    /**
     * Masque une chaîne de chiffres ; laisse en clair les deux derniers chiffres lorsque c’est possible.
     */
    public static String maskPhoneDigits(String digits) {
        if (digits == null || digits.isBlank()) {
            return "";
        }
        String d = digits.replaceAll("\\D+", "");
        if (d.length() <= 2) {
            return "**";
        }
        if (d.length() <= 4) {
            return "**" + d.substring(d.length() - 2);
        }
        return "*** *** " + d.substring(d.length() - 2);
    }
}
