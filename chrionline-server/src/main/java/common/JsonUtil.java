package common;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilitaires JSON minimaux pour le protocole {@link Message}, et sérialisation Java binaire pour le catalogue produits.
 *
 * <p>Cette classe reste volontairement légère parce que le projet n’utilise pas de dépendance JSON externe. La correction
 * importante ici est que {@link #toMap(String)} sait maintenant découper les paires clé/valeur sans casser les chaînes qui
 * contiennent des virgules, des deux-points ou des guillemets échappés.
 */
public class JsonUtil {

    /** Sérialise un {@link Message} en une seule ligne JSON (champs quotés et échappés). */
    public static String toJson(Message m) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"type\":").append(quote(m.getType())).append(",");
        sb.append("\"requestId\":").append(quote(m.getRequestId())).append(",");
        sb.append("\"status\":").append(quote(m.getStatus())).append(",");
        sb.append("\"payload\":").append(quote(m.getPayload())).append(",");
        sb.append("\"errorCode\":").append(quote(m.getErrorCode()));
        sb.append("}");
        return sb.toString();
    }

    /** Désérialise une ligne JSON vers un {@link Message} (champs manquants → chaînes vides). */
    public static Message fromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new Message();
        }
        Message m = new Message();
        m.setType(unquote(getField(json, "type")));
        m.setRequestId(unquote(getField(json, "requestId")));
        m.setStatus(unquote(getField(json, "status")));
        m.setPayload(unquote(getField(json, "payload")));
        m.setErrorCode(unquote(getField(json, "errorCode")));
        return m;
    }

    /** Met une chaîne entre guillemets JSON avec échappement des caractères spéciaux. */
    private static String quote(String s) {
        if (s == null) {
            return "null";
        }
        String escaped = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
        return "\"" + escaped + "\"";
    }

    /** Extrait la valeur d’un champ nommé dans un objet JSON (analyse manuelle simplifiée). */
    private static String getField(String json, String field) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*");
        Matcher m = p.matcher(json);
        if (!m.find()) {
            return "";
        }
        int start = m.end();
        if (start < json.length() && json.charAt(start) == '"') {
            StringBuilder sb = new StringBuilder();
            boolean escape = false;
            for (int i = start + 1; i < json.length(); i++) {
                char c = json.charAt(i);
                if (escape) {
                    switch (c) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        default -> sb.append(c);
                    }
                    escape = false;
                    continue;
                }
                if (c == '\\') {
                    escape = true;
                    continue;
                }
                if (c == '"') {
                    break;
                }
                sb.append(c);
            }
            return sb.toString();
        }
        int end = findPrimitiveValueEnd(json, start);
        String raw = json.substring(start, end).trim();
        if ("null".equals(raw)) {
            return null;
        }
        return raw;
    }

    private static int findPrimitiveValueEnd(String json, int start) {
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == ',' || c == '}') {
                return i;
            }
        }
        return json.length();
    }

    /** Normalise les littéraux {@code null} JSON en chaîne vide pour l’UI. */
    private static String unquote(String s) {
        if (s == null || "null".equals(s)) {
            return "";
        }
        return s;
    }

    public static Map<String, String> toMap(String json) {
        Map<String, String> map = new HashMap<>();
        if (json == null || json.trim().isEmpty()) {
            return map;
        }
        String t = json.trim();
        if (t.startsWith("{") && t.endsWith("}")) {
            t = t.substring(1, t.length() - 1).trim();
        }
        for (String part : splitTopLevel(t)) {
            int colon = findTopLevelColon(part);
            if (colon <= 0) {
                continue;
            }
            String key = trimQuotes(part.substring(0, colon).trim());
            String val = part.substring(colon + 1).trim();
            if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
                val = unescapeJsonString(val.substring(1, val.length() - 1));
            } else if ("null".equals(val)) {
                val = "";
            }
            map.put(key, val);
        }
        return map;
    }

    private static String trimQuotes(String s) {
        if (s != null && s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
            return unescapeJsonString(s.substring(1, s.length() - 1));
        }
        return s != null ? s : "";
    }

    private static int findTopLevelColon(String s) {
        boolean inString = false;
        boolean escape = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\') {
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (c == ':' && !inString) {
                return i;
            }
        }
        return -1;
    }

    /** Découpe par virgules au niveau racine sans couper à l’intérieur des chaînes, accolades ou tableaux. */
    private static String[] splitTopLevel(String s) {
        java.util.List<String> parts = new java.util.ArrayList<>();
        int objectDepth = 0;
        int arrayDepth = 0;
        boolean inString = false;
        boolean escape = false;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escape) {
                escape = false;
                continue;
            }
            if (c == '\\') {
                escape = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (c == '{') {
                objectDepth++;
            } else if (c == '}') {
                objectDepth--;
            } else if (c == '[') {
                arrayDepth++;
            } else if (c == ']') {
                arrayDepth--;
            } else if (c == ',' && objectDepth == 0 && arrayDepth == 0) {
                parts.add(s.substring(start, i).trim());
                start = i + 1;
            }
        }
        parts.add(s.substring(start).trim());
        return parts.toArray(new String[0]);
    }

    private static String unescapeJsonString(String s) {
        if (s == null || s.indexOf('\\') < 0) {
            return s != null ? s : "";
        }
        StringBuilder sb = new StringBuilder();
        boolean escape = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!escape) {
                if (c == '\\') {
                    escape = true;
                } else {
                    sb.append(c);
                }
                continue;
            }
            switch (c) {
                case '"' -> sb.append('"');
                case '\\' -> sb.append('\\');
                case '/' -> sb.append('/');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                default -> sb.append(c);
            }
            escape = false;
        }
        if (escape) {
            sb.append('\\');
        }
        return sb.toString();
    }

    /** Sérialise un objet Java en octets (liste de produits, détail produit, etc.). */
    @SuppressWarnings("unchecked")
    public static byte[] toBinary(Object obj) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(obj);
        }
        return bos.toByteArray();
    }

    /** Désérialise des octets vers une instance du type demandé. */
    @SuppressWarnings("unchecked")
    public static <T> T fromBinary(byte[] data, Class<T> clazz) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        try (ObjectInputStream ois = new ObjectInputStream(bis)) {
            return clazz.cast(ois.readObject());
        }
    }
}
