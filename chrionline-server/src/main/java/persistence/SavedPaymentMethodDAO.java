package persistence;

import chrionline.BaseDonnees;
import services.StorageCryptoService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Accès à la table {@code methode_paiement_enregistree} : modèles de paiement masqués par utilisateur.
 */
public final class SavedPaymentMethodDAO {

    /** Ligne affichable côté client. */
    public record SavedRow(int id, String typePaiement, String displayLabel, String createdAtIso) {}

    private SavedPaymentMethodDAO() {}

    /** Insère un modèle (JSON libre pour les détails non sensibles). */
    public static void insert(int userId, String typePaiement, String displayLabel, String templateJson)
            throws SQLException {
        try (Connection c = BaseDonnees.getConnection();
                PreparedStatement ps =
                        c.prepareStatement(
                                "INSERT INTO methode_paiement_enregistree (user_id, type_paiement, display_label, template_json, created_at) "
                                        + "VALUES (?,?,?,?,?)")) {
            ps.setInt(1, userId);
            ps.setString(2, typePaiement);
            ps.setString(3, displayLabel);
            ps.setString(4, StorageCryptoService.sealIfEnabled(templateJson));
            ps.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now()));
            ps.executeUpdate();
        }
    }

    /** Liste décroissante par identifiant de méthode. */
    public static List<SavedRow> listByUser(int userId) throws SQLException {
        List<SavedRow> list = new ArrayList<>();
        try (Connection c = BaseDonnees.getConnection();
                PreparedStatement ps =
                        c.prepareStatement(
                                "SELECT id_methode, type_paiement, display_label, created_at FROM methode_paiement_enregistree WHERE user_id = ? ORDER BY id_methode DESC")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Timestamp ts = rs.getTimestamp("created_at");
                    String iso = ts != null ? ts.toLocalDateTime().toString() : "";
                    list.add(
                            new SavedRow(
                                    rs.getInt("id_methode"),
                                    rs.getString("type_paiement"),
                                    rs.getString("display_label"),
                                    iso));
                }
            }
        }
        return list;
    }

    /** Supprime si la méthode appartient bien à l’utilisateur. */
    public static boolean deleteForUser(int methodId, int userId) throws SQLException {
        try (Connection c = BaseDonnees.getConnection();
                PreparedStatement ps =
                        c.prepareStatement(
                                "DELETE FROM methode_paiement_enregistree WHERE id_methode = ? AND user_id = ?")) {
            ps.setInt(1, methodId);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        }
    }

    /** Sérialise les lignes en tableau JSON pour réponse socket. */
    public static String toJsonArray(List<SavedRow> rows) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < rows.size(); i++) {
            SavedRow r = rows.get(i);
            if (i > 0) {
                sb.append(",");
            }
            sb.append("{")
                    .append("\"id\":")
                    .append(r.id())
                    .append(",\"type\":\"")
                    .append(esc(r.typePaiement()))
                    .append("\",\"label\":\"")
                    .append(esc(r.displayLabel()))
                    .append("\",\"createdAt\":\"")
                    .append(esc(r.createdAtIso()))
                    .append("\"}");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
