package ecommerce.personne5.model;

/** Compteurs et chiffre d’affaires pour les paiements simulés. */
public class PaiementStats {
    private int totalPaiements;
    private int paiementsAcceptes;
    private int paiementsRefuses;
    private int paiementsEnAttente;
    private int paiementsRembourses;
    private double chiffreAffaires;

    public int getTotalPaiements() {
        return totalPaiements;
    }

    public int getPaiementsAcceptes() {
        return paiementsAcceptes;
    }

    public int getPaiementsRefuses() {
        return paiementsRefuses;
    }

    public int getPaiementsEnAttente() {
        return paiementsEnAttente;
    }

    public int getPaiementsRembourses() {
        return paiementsRembourses;
    }

    public double getChiffreAffaires() {
        return chiffreAffaires;
    }

    public void incrementerTotalPaiements() {
        totalPaiements++;
    }

    public void incrementerPaiementsAcceptes() {
        paiementsAcceptes++;
    }

    public void incrementerPaiementsRefuses() {
        paiementsRefuses++;
    }

    public void incrementerPaiementsEnAttente() {
        paiementsEnAttente++;
    }

    public void incrementerPaiementsRembourses() {
        paiementsRembourses++;
    }

    public void ajouterChiffreAffaires(double montant) {
        chiffreAffaires += montant;
    }

    @Override
    public String toString() {
        return "\n===== STATISTIQUES PAIEMENT =====\n" +
                "Total paiements      : " + totalPaiements + "\n" +
                "Paiements acceptes   : " + paiementsAcceptes + "\n" +
                "Paiements refuses    : " + paiementsRefuses + "\n" +
                "Paiements en attente : " + paiementsEnAttente + "\n" +
                "Paiements rembourses : " + paiementsRembourses + "\n" +
                "Chiffre d'affaires   : " + String.format("%.2f MAD", chiffreAffaires) + "\n" +
                "=================================\n";
    }
}