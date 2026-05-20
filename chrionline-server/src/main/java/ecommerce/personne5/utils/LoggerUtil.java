package ecommerce.personne5.utils;

import common.ChrionlineLog;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Journalisation console + fichier {@code ~/.chrionline/server.log} pour la démo personne 5. */
public class LoggerUtil {
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private LoggerUtil() {
    }

    private static String now() {
        return LocalDateTime.now().format(FORMATTER);
    }

    public static void info(String message) {
        ChrionlineLog.info("[personne5] [" + now() + "] [INFO] " + message);
    }

    public static void success(String message) {
        ChrionlineLog.info("[personne5] [" + now() + "] [SUCCESS] " + message);
    }

    public static void warning(String message) {
        ChrionlineLog.warn("[personne5] [" + now() + "] [WARNING] " + message);
    }

    public static void error(String message) {
        ChrionlineLog.err("[personne5] [" + now() + "] [ERROR] " + message);
    }
}