package persistence;

import chrionline.BaseDonnees;
import models.Commande;
import models.LigneCommande;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistance {@link Commande} / {@link LigneCommande} dans MySQL via les tables {@code orders} et {@code order_lines}
 * (schéma type XAMPP : {@code order_id}, {@code total_usd}, {@code created_at}, etc.).
 */
public final class CommandeDAO {

    private CommandeDAO() {}

    /** Insère la commande et ses lignes dans une transaction ; retourne l’identifiant généré. */
    public static int insert(Connection conn, Commande cmd) throws SQLException {
        conn.setAutoCommit(false);
        try {
            double total = cmd.calculerTotal();
            long dateMs = cmd.getDateCommandeMs();
            String st = cmd.getStatus() != null ? cmd.getStatus() : "EN_ATTENTE";
            try (PreparedStatement ps =
                    conn.prepareStatement(
                            "INSERT INTO orders (user_id, status, total_usd, created_at) VALUES (?,?,?,?)",
                            Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, cmd.getUserId());
                ps.setString(2, st);
                ps.setDouble(3, total);
                ps.setTimestamp(4, new Timestamp(dateMs));
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("order id not generated");
                    }
                    int id = keys.getInt(1);
                    insertLignes(conn, id, cmd.getLignes());
                    conn.commit();
                    return id;
                }
            }
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    private static void insertLignes(Connection conn, int orderId, List<LigneCommande> lignes)
            throws SQLException {
        String sql =
                "INSERT INTO order_lines (order_id, product_id, quantity, unit_price_usd) VALUES (?,?,?,?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (LigneCommande l : lignes) {
                ps.setInt(1, orderId);
                ps.setInt(2, l.getProduitId());
                ps.setInt(3, l.getQuantite());
                ps.setDouble(4, l.getPrixUnitaire());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    /** Ouvre une connexion puis délègue à {@link #findById(Connection, int)}. */
    public static Commande findById(int id) throws SQLException {
        try (Connection c = BaseDonnees.getConnection()) {
            return findById(c, id);
        }
    }

    /** Charge une commande et ses lignes (jointure produits pour le libellé de ligne). */
    public static Commande findById(Connection conn, int id) throws SQLException {
        try (PreparedStatement ps =
                conn.prepareStatement(
                        "SELECT order_id, user_id, status, total_usd, created_at FROM orders WHERE order_id = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                Commande cmd = mapCommande(rs);
                cmd.getLignes().addAll(loadLignes(conn, id));
                return cmd;
            }
        }
    }

    private static Commande mapCommande(ResultSet rs) throws SQLException {
        Commande cmd = new Commande(rs.getInt("order_id"), rs.getInt("user_id"));
        cmd.setStatus(rs.getString("status"));
        Timestamp ts = rs.getTimestamp("created_at");
        cmd.setDateCommandeMs(ts != null ? ts.getTime() : System.currentTimeMillis());
        cmd.getLignes().clear();
        return cmd;
    }

    private static List<LigneCommande> loadLignes(Connection conn, int orderId) throws SQLException {
        List<LigneCommande> list = new ArrayList<>();
        String sql =
                "SELECT ol.product_id, COALESCE(p.nom_produit, CONCAT('Produit #', ol.product_id)) AS line_name, "
                        + "ol.quantity, ol.unit_price_usd "
                        + "FROM order_lines ol "
                        + "LEFT JOIN products p ON p.product_id = ol.product_id "
                        + "WHERE ol.order_id = ? ORDER BY ol.line_id";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, orderId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(
                            new LigneCommande(
                                    rs.getInt("product_id"),
                                    rs.getString("line_name"),
                                    rs.getInt("quantity"),
                                    rs.getDouble("unit_price_usd")));
                }
            }
        }
        return list;
    }

    /** Toutes les commandes d’un utilisateur, tri décroissant par identifiant. */
    public static List<Commande> findByUserId(int userId) throws SQLException {
        List<Commande> out = new ArrayList<>();
        try (Connection conn = BaseDonnees.getConnection();
                PreparedStatement ps =
                        conn.prepareStatement(
                                "SELECT order_id, user_id, status, total_usd, created_at FROM orders WHERE user_id = ? ORDER BY order_id DESC")) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int oid = rs.getInt("order_id");
                    Commande cmd = mapCommande(rs);
                    cmd.getLignes().addAll(loadLignes(conn, oid));
                    out.add(cmd);
                }
            }
        }
        return out;
    }

    /** Met à jour le statut textuel de la commande. */
    public static boolean updateStatus(int orderId, String newStatus) throws SQLException {
        try (Connection conn = BaseDonnees.getConnection();
                PreparedStatement ps = conn.prepareStatement("UPDATE orders SET status = ? WHERE order_id = ?")) {
            ps.setString(1, newStatus);
            ps.setInt(2, orderId);
            return ps.executeUpdate() > 0;
        }
    }

    /** Passe de {@code EN_ATTENTE} à {@code VALIDE} si la ligne correspond. */
    public static boolean valider(int orderId) throws SQLException {
        try (Connection conn = BaseDonnees.getConnection();
                PreparedStatement ps =
                        conn.prepareStatement(
                                "UPDATE orders SET status = 'VALIDE' WHERE order_id = ? AND status = 'EN_ATTENTE'")) {
            ps.setInt(1, orderId);
            return ps.executeUpdate() > 0;
        }
    }

    /** Annule sauf si statut {@code VALIDE} ou {@code PAYEE}. */
    public static boolean annuler(int orderId) throws SQLException {
        try (Connection conn = BaseDonnees.getConnection();
                PreparedStatement ps =
                        conn.prepareStatement(
                                "UPDATE orders SET status = 'ANNULEE' WHERE order_id = ? AND status NOT IN ('VALIDE','PAYEE')")) {
            ps.setInt(1, orderId);
            return ps.executeUpdate() > 0;
        }
    }
}
