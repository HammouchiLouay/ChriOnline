package models;

import java.util.ArrayList;
import java.util.List;

/**
 * Commande e-commerce : identifiant, utilisateur, lignes, statut, horodatage pour l’historique UI.
 */
public class Commande {

    private int id;
    private int userId;
    private List<LigneCommande> lignes;
    private String status;
    /** Millisecondes depuis l’epoch à la création (affichage historique). */
    private long dateCommandeMs;

    public Commande() {
        this.lignes = new ArrayList<>();
        this.dateCommandeMs = System.currentTimeMillis();
    }

    /** Construit une commande avec statut initial {@code EN_ATTENTE}. */
    public Commande(int id, int userId) {
        this.id = id;
        this.userId = userId;
        this.lignes = new ArrayList<>();
        this.status = "EN_ATTENTE";
        this.dateCommandeMs = System.currentTimeMillis();
    }

    public long getDateCommandeMs() {
        return dateCommandeMs;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setDateCommandeMs(long dateCommandeMs) {
        this.dateCommandeMs = dateCommandeMs;
    }

    public int getUserId() {
        return userId;
    }

    public List<LigneCommande> getLignes() {
        return lignes;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    /** Ajoute une ligne au panier commande. */
    public void ajouterLigne(LigneCommande ligne) {
        lignes.add(ligne);
    }

    /** Retire la ligne correspondant à l’identifiant produit. */
    public void supprimerLigne(int produitId) {
        lignes.removeIf(l -> l.getProduitId() == produitId);
    }

    /** Somme des sous-totaux des lignes. */
    public double calculerTotal() {
        double total = 0;
        for (LigneCommande l : lignes) {
            total += l.calculerSousTotal();
        }
        return total;
    }

    /** Sérialise la commande et ses lignes en JSON pour le protocole socket. */
    public String toJson() {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"id\":").append(id).append(",");
        json.append("\"userId\":").append(userId).append(",");
        json.append("\"status\":\"").append(status).append("\",");
        json.append("\"total\":").append(calculerTotal()).append(",");
        json.append("\"dateCommande\":").append(dateCommandeMs).append(",");
        json.append("\"lignes\":[");
        for (int i = 0; i < lignes.size(); i++) {
            json.append(lignes.get(i).toJson());
            if (i < lignes.size() - 1) {
                json.append(",");
            }
        }
        json.append("]}");
        return json.toString();
    }

    @Override
    public String toString() {
        return "Commande{"
                + "id=" + id
                + ", userId=" + userId
                + ", total=" + calculerTotal()
                + ", status='" + status + '\''
                + ", lignes=" + lignes
                + '}';
    }
}
