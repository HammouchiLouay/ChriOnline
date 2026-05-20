package ecommerce.personne5.model;

/** Commande du module personne 5 (identifiant texte, total, statut) — distinct du modèle serveur {@code models.Commande}. */
public class Commande {
    private String idCommande;
    private String dateCommande;
    private StatutCommande statut;
    private double total;

    public Commande(String idCommande, String dateCommande, StatutCommande statut, double total) {
        this.idCommande = idCommande;
        this.dateCommande = dateCommande;
        this.statut = statut;
        this.total = total;
    }

    public String getIdCommande() {
        return idCommande;
    }

    public String getDateCommande() {
        return dateCommande;
    }

    public StatutCommande getStatut() {
        return statut;
    }

    public double getTotal() {
        return total;
    }

    public void setStatut(StatutCommande statut) {
        this.statut = statut;
    }

    public void setTotal(double total) {
        this.total = total;
    }

    @Override
    public String toString() {
        return "Commande{" +
                "idCommande='" + idCommande + '\'' +
                ", dateCommande='" + dateCommande + '\'' +
                ", statut=" + statut +
                ", total=" + total +
                '}';
    }
}