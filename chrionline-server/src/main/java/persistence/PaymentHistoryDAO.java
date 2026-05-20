package persistence;

import chrionline.BaseDonnees;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * Journal des paiements simulés en base ({@code historique_paiement}).
 */
public final class PaymentHistoryDAO {

    private PaymentHistoryDAO() {}

    /** Enregistre une ligne après un paiement socket (tronque les champs trop longs). */
    public static void insert(
            int commandeId,
            int userId,
            String typePaiement,
            String idPaiementSimule,
            String statut,
            double montantFinal,
            String messageResume)
            throws SQLException {
        try (Connection c = BaseDonnees.getConnection();
                PreparedStatement ps =
                        c.prepareStatement(
                                "INSERT INTO historique_paiement (commande_id, user_id, type_paiement, "
                                        + "id_paiement_simule, statut, montant_final, message_resume, created_at) "
                                        + "VALUES (?,?,?,?,?,?,?,?)")) {
            ps.setInt(1, commandeId);
            ps.setInt(2, userId);
            ps.setString(3, typePaiement);
            ps.setString(4, truncate(idPaiementSimule, 80));
            ps.setString(5, statut);
            ps.setDouble(6, montantFinal);
            ps.setString(7, messageResume != null ? truncate(messageResume, 512) : null);
            ps.setTimestamp(8, Timestamp.valueOf(LocalDateTime.now()));
            ps.executeUpdate();
        }
    }

    /** Tronque pour respecter les colonnes VARCHAR. */
    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
