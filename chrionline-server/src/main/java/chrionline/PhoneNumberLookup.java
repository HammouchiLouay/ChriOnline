package chrionline;

/**
 * Conversion des saisies utilisateur vers la colonne {@code user.phone_number} (INT MySQL). Gère les préfixes
 * français +33 / 0033 et les chaînes trop longues pour {@link Integer#parseInt(String)}.
 */
public final class PhoneNumberLookup {

    private PhoneNumberLookup() {}

    /** Ne conserve que les chiffres ASCII (supprime espaces, +, tirets, etc.). */
    public static String digitsOnly(String s) {
        if (s == null) {
            return "";
        }
        return s.replaceAll("\\D+", "");
    }

    /**
     * Interprète une chaîne de chiffres comme l’entier stocké en base, ou {@code null} si invalide.
     * Gère le dépassement de capité int (ex. +33…) en dérivant les 9 chiffres nationaux.
     */
    public static Integer parseStoredPhoneInt(String digitsOnly) {
        if (digitsOnly == null || digitsOnly.isEmpty()) {
            return null;
        }
        String d = digitsOnly.replaceAll("\\D+", "");
        if (d.isEmpty()) {
            return null;
        }
        try {
            long v = Long.parseLong(d);
            if (v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE) {
                return (int) v;
            }
        } catch (NumberFormatException e) {
            return null;
        }
        if (d.length() >= 11 && d.startsWith("33")) {
            String national = d.substring(2);
            if (national.length() == 9) {
                try {
                    return Integer.parseInt(national);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        if (d.length() >= 13 && d.startsWith("0033")) {
            String national = d.substring(4);
            if (national.length() == 9) {
                try {
                    return Integer.parseInt(national);
                } catch (NumberFormatException e) {
                    return null;
                }
            }
        }
        if (d.length() >= 9) {
            String last9 = d.substring(d.length() - 9);
            try {
                return Integer.parseInt(last9);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
}
