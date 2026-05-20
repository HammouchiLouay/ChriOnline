package persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Supprime un utilisateur et toutes les lignes liées. L’ordre des suppressions respecte les clés étrangères.
 *
 * <p>Les tables optionnelles ({@code historique_paiement}, {@code methode_paiement_enregistree}) sont ignorées si
 * absentes (erreur MySQL 1146).
 */
public final class AccountDeletionDAO {

    private AccountDeletionDAO() {}

    /**
     * Supprime l’historique de paiement, les lignes de commande, les commandes, les méthodes enregistrées, puis l’utilisateur.
     * L’appelant doit gérer {@link Connection#setAutoCommit(boolean)}, commit et rollback.
     */
    public static void deleteAllForUser(Connection conn, int userId) throws SQLException {
        deleteOptionalTable(conn, "DELETE FROM historique_paiement WHERE user_id = ?", userId);
        deleteOrderLinesForUser(conn, userId);
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM orders WHERE user_id = ?")) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        }
        deleteOptionalTable(conn, "DELETE FROM methode_paiement_enregistree WHERE user_id = ?", userId);
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM user WHERE id_user = ?")) {
            ps.setInt(1, userId);
            if (ps.executeUpdate() == 0) {
                throw new SQLException("user row not deleted");
            }
        }
    }

    /** Supprime les lignes des commandes de l’utilisateur via jointure sur {@code orders}. */
    private static void deleteOrderLinesForUser(Connection conn, int userId) throws SQLException {
        String sql =
                "DELETE ol FROM order_lines ol INNER JOIN orders o ON ol.order_id = o.order_id WHERE o.user_id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        }
    }

    /** Ignore l’erreur « table inexistante » pour les schémas sans table optionnelle. */
    private static void deleteOptionalTable(Connection conn, String sql, int userId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            if (e.getErrorCode() == 1146) {
                return;
            }
            throw e;
        }
    }
}
