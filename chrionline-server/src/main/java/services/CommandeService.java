package services;

import chrionline.BaseDonnees;
import common.ChrionlineLog;
import models.Commande;
import models.LigneCommande;
import persistence.CommandeDAO;
import product.Product;
import product.ProductRepository;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Logique métier des commandes : création (total global ou lignes produits), validation, annulation, lecture.
 */
public final class CommandeService {

    private CommandeService() {}

    /** Crée une commande avec une seule ligne « total global » (hors détail catalogue). */
    public static Commande createCommande(int userId, double total) throws SQLException {
        try (Connection c = BaseDonnees.getConnection()) {
            Commande cmd = new Commande(0, userId);
            LigneCommande ligne = new LigneCommande(0, "Commande globale", 1, total);
            cmd.ajouterLigne(ligne);
            int id = CommandeDAO.insert(c, cmd);
            cmd.setId(id);
            return cmd;
        }
    }

    /**
     * Parse {@code produitsData} au format {@code id:qty;id:qty}, enrichit nom/prix depuis le catalogue puis insère.
     */
    public static Commande createCommandeAvecProduits(int userId, String produitsData) throws SQLException {
        Commande cmd = new Commande(0, userId);
        try {
            String[] produits = produitsData.split(";");
            for (String p : produits) {
                String[] parts = p.split(":");
                int produitId = Integer.parseInt(parts[0].trim());
                int quantite = Integer.parseInt(parts[1].trim());
                Product catalog =
                        ProductRepository.findById(String.valueOf(produitId)).orElse(null);
                String nom = catalog != null ? catalog.getName() : "Produit_" + produitId;
                double prix = catalog != null ? catalog.getPrice() : 100.0 * produitId;
                LigneCommande ligne = new LigneCommande(produitId, nom, quantite, prix);
                cmd.ajouterLigne(ligne);
            }
        } catch (Exception e) {
            ChrionlineLog.info("Erreur parsing produits");
        }
        try (Connection c = BaseDonnees.getConnection()) {
            int id = CommandeDAO.insert(c, cmd);
            cmd.setId(id);
            return cmd;
        }
    }

    /** Passe le statut de {@code EN_ATTENTE} à {@code VALIDE} si possible. */
    public static boolean validerCommande(int id) throws SQLException {
        return CommandeDAO.valider(id);
    }

    /** Annule la commande sauf si déjà {@code VALIDE} ou {@code PAYEE}. */
    public static boolean annulerCommande(int id) throws SQLException {
        return CommandeDAO.annuler(id);
    }

    /** Liste les commandes d’un utilisateur (plus récentes en premier). */
    public static List<Commande> getCommandesByUser(int userId) throws SQLException {
        return CommandeDAO.findByUserId(userId);
    }

    /** Charge une commande avec ses lignes par identifiant. */
    public static Commande getCommandeById(int id) throws SQLException {
        return CommandeDAO.findById(id);
    }

    /** Met à jour le statut en base (ex. après paiement simulé). */
    public static void updateCommandeStatus(int id, String status) throws SQLException {
        CommandeDAO.updateStatus(id, status);
    }
}
