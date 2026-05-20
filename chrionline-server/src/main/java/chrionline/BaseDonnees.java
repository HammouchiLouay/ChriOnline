package chrionline;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Point d’accès JDBC vers MySQL pour le processus serveur uniquement. Les clients JavaFX distants n’ouvrent
 * jamais cette URL : ils passent par TCP ({@code ServerMain}) ; seul le serveur dialogue avec MySQL.
 *
 * <p>Adapter utilisateur / mot de passe selon votre instance MySQL locale ou variables d’environnement.
 */
public final class BaseDonnees {

    /**
     * URL par défaut : boucle locale — MySQL sur la même machine que le serveur. Surchargée par
     * {@code CHRIONLINE_JDBC_URL} si besoin (toujours côté serveur, jamais exposée aux clients).
     */
    private static final String DEFAULT_URL =
            "jdbc:mysql://127.0.0.1:3306/chrionline?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";

    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASSWORD = "";

    private BaseDonnees() {}

    /** Lit l’URL JDBC depuis l’environnement ou la valeur par défaut. */
    public static String jdbcUrl() {
        String v = System.getenv("CHRIONLINE_JDBC_URL");
        if (v != null && !v.isBlank()) {
            return v.trim();
        }
        return DEFAULT_URL;
    }

    /** Utilisateur MySQL ({@code CHRIONLINE_DB_USER} ou {@code root}). */
    public static String dbUser() {
        String v = System.getenv("CHRIONLINE_DB_USER");
        if (v != null && !v.isBlank()) {
            return v.trim();
        }
        return DEFAULT_USER;
    }

    /** Mot de passe MySQL ({@code CHRIONLINE_DB_PASSWORD} ou vide). */
    private static String dbPassword() {
        String v = System.getenv("CHRIONLINE_DB_PASSWORD");
        if (v != null) {
            return v;
        }
        return DEFAULT_PASSWORD;
    }

    /** Ouvre une nouvelle connexion JDBC vers la base configurée. */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl(), dbUser(), dbPassword());
    }

    /**
     * Vérifie que MySQL répond (requête {@code SELECT 1}. À appeler au démarrage et pour les contrôles de santé
     * (PING).
     */
    public static void verifyConnection() throws SQLException {
        try (Connection c = getConnection();
                Statement st = c.createStatement()) {
            st.execute("SELECT 1");
        }
    }

    public static String currentDatabaseName() throws SQLException {
        try (Connection c = getConnection();
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("SELECT DATABASE()")) {
            return rs.next() ? rs.getString(1) : "";
        }
    }

    public static String describeProductsTable() throws SQLException {
        StringBuilder sb = new StringBuilder();
        try (Connection c = getConnection();
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery("DESCRIBE products")) {
            while (rs.next()) {
                if (!sb.isEmpty()) {
                    sb.append("; ");
                }
                sb.append(rs.getString("Field"))
                        .append(" ")
                        .append(rs.getString("Type"))
                        .append(" null=")
                        .append(rs.getString("Null"))
                        .append(" key=")
                        .append(rs.getString("Key"));
            }
        }
        return sb.toString();
    }
}
