package common;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Journaux serveur : affichage console et fichier sous {@code ~/.chrionline/server.log} (Windows :
 * {@code %USERPROFILE%\.chrionline\server.log}).
 */
public final class ChrionlineLog {

    private static final Object LOCK = new Object();
    private static final DateTimeFormatter FILE_TS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private ChrionlineLog() {}

    /** Répertoire de données utilisateur (créé à l’écriture si besoin). */
    public static Path dotChrionlineDir() {
        return Path.of(System.getProperty("user.home", ".")).resolve(".chrionline");
    }

    /** Fichier journal principal du serveur socket. */
    public static Path serverLogPath() {
        return dotChrionlineDir().resolve("server.log");
    }

    /** {@code System.out} + ligne horodatée dans {@link #serverLogPath()}. */
    public static void info(String message) {
        String m = message == null ? "" : message;
        System.out.println(m);
        appendFile("INFO", m, null);
    }

    /** Comme {@link #info(String)} avec niveau WARN. */
    public static void warn(String message) {
        String m = message == null ? "" : message;
        System.out.println(m);
        appendFile("WARN", m, null);
    }

    /** {@code System.err} + ligne horodatée dans le fichier. */
    public static void err(String message) {
        String m = message == null ? "" : message;
        System.err.println(m);
        appendFile("ERROR", m, null);
    }

    /** {@code System.err}, stack trace, et fichier. */
    public static void err(String message, Throwable t) {
        String m = message == null ? "" : message;
        System.err.println(m);
        if (t != null) {
            t.printStackTrace(System.err);
        }
        appendFile("ERROR", m, t);
    }

    private static void appendFile(String level, String message, Throwable t) {
        synchronized (LOCK) {
            try {
                Files.createDirectories(dotChrionlineDir());
                String ts = FILE_TS.format(Instant.now());
                StringBuilder sb = new StringBuilder();
                for (String line : message.split("\\R", -1)) {
                    sb.append(ts).append(" [").append(level).append("] ").append(line).append(System.lineSeparator());
                }
                if (t != null) {
                    StringWriter sw = new StringWriter();
                    t.printStackTrace(new PrintWriter(sw));
                    for (String line : sw.toString().split("\\R", -1)) {
                        sb.append(ts).append(" [").append(level).append("] ").append(line).append(System.lineSeparator());
                    }
                }
                Files.writeString(
                        serverLogPath(),
                        sb.toString(),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
            } catch (Exception ignored) {
                // best-effort : ne pas casser le serveur si le disque est plein
            }
        }
    }
}
