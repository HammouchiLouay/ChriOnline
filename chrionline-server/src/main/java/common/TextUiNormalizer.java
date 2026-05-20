package common;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Normalizes French UI labels used by product categories.
 *
 * <p>The database may contain historical ASCII or mojibake labels. The UI displays the correctly accented form
 * while queries still match the older stored variants.
 */
public final class TextUiNormalizer {

    private static final String VETEMENTS = "V\u00eatements";
    private static final String VETEMENTS_MODE = VETEMENTS + " & mode";

    private TextUiNormalizer() {}

    public static List<String> categoryMatchVariants(String category) {
        if (category == null || category.isBlank()) {
            return List.of();
        }
        LinkedHashSet<String> set = new LinkedHashSet<>();
        String t = category.trim();
        set.add(t);
        String n = normalizeFrenchUi(t);
        set.add(n);
        if (isVetementsModeLabel(n) || isVetementsModeLabel(t)) {
            set.add("Vetements & mode");
            set.add(VETEMENTS_MODE);
        }
        return new ArrayList<>(set);
    }

    private static boolean isVetementsModeLabel(String s) {
        if (s == null) {
            return false;
        }
        String normalized = normalizeFrenchUi(s).trim();
        return normalized.equalsIgnoreCase(VETEMENTS_MODE)
                || normalized.equalsIgnoreCase("Vetements & mode")
                || normalized.equalsIgnoreCase("Vetements")
                || normalized.equalsIgnoreCase(VETEMENTS);
    }

    public static String normalizeFrenchUi(String s) {
        if (s == null || s.isEmpty()) {
            return s == null ? "" : s;
        }
        String t = s.trim();
        t = t.replace("Vetements & mode", VETEMENTS_MODE);
        t = t.replace("Vetements", VETEMENTS);

        // Historical corrupted spellings kept only as input aliases, never as display text.
        t = t.replace("V\u00c3\u00aatements & mode", VETEMENTS_MODE);
        t = t.replace("V\u00c3\u0192\u00c2\u00aatements & mode", VETEMENTS_MODE);
        t = t.replace("V\u00c3\u0192\u00c6\u2019\u00c3\u201a\u00c2\u00aatements & mode", VETEMENTS_MODE);
        t = t.replace("V\u00c3\u203atements & mode", VETEMENTS_MODE);
        t = t.replace("V\u00c3\u00aatements", VETEMENTS);
        t = t.replace("V\u00c3\u0192\u00c2\u00aatements", VETEMENTS);
        t = t.replace("V\u00c3\u0192\u00c6\u2019\u00c3\u201a\u00c2\u00aatements", VETEMENTS);
        t = t.replace("V\u00c3\u203atements", VETEMENTS);

        if (VETEMENTS.equalsIgnoreCase(t)) {
            return VETEMENTS_MODE;
        }
        return t;
    }
}
