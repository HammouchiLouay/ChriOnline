package common;

import java.util.prefs.Preferences;

/**
 * Accès tolérant aux {@link Preferences} Java : ne fait jamais planter l’interface si le registre Windows
 * ou le stockage des préférences est indisponible (droits restreints, etc.).
 */
public final class ClientPrefs {

    private ClientPrefs() {}

    /** Exécute une opération ; ignore les erreurs de sécurité ou d’état. */
    public static void runQuietly(Runnable op) {
        if (op == null) {
            return;
        }
        try {
            op.run();
        } catch (SecurityException | IllegalStateException ignored) {
        }
    }

    /** Lit une chaîne dans un nœud de préférences, ou la valeur par défaut en cas d’échec. */
    public static String getString(String nodePath, String key, String def) {
        try {
            return Preferences.userRoot().node(nodePath).get(key, def);
        } catch (SecurityException | IllegalStateException e) {
            return def;
        }
    }

    /** Lit un entier dans un nœud de préférences. */
    public static int getInt(String nodePath, String key, int def) {
        try {
            return Preferences.userRoot().node(nodePath).getInt(key, def);
        } catch (SecurityException | IllegalStateException e) {
            return def;
        }
    }

    /** Lit un booléen dans un nœud de préférences. */
    public static boolean getBoolean(String nodePath, String key, boolean def) {
        try {
            return Preferences.userRoot().node(nodePath).getBoolean(key, def);
        } catch (SecurityException | IllegalStateException e) {
            return def;
        }
    }

    /** Enregistre une chaîne (sans lever d’exception vers l’UI). */
    public static void putString(String nodePath, String key, String value) {
        runQuietly(() -> Preferences.userRoot().node(nodePath).put(key, value));
    }

    /** Enregistre un entier. */
    public static void putInt(String nodePath, String key, int value) {
        runQuietly(() -> Preferences.userRoot().node(nodePath).putInt(key, value));
    }

    /** Enregistre un booléen. */
    public static void putBoolean(String nodePath, String key, boolean value) {
        runQuietly(() -> Preferences.userRoot().node(nodePath).putBoolean(key, value));
    }

    /** Supprime une clé du nœud indiqué. */
    public static void remove(String nodePath, String key) {
        runQuietly(() -> Preferences.userRoot().node(nodePath).remove(key));
    }
}
