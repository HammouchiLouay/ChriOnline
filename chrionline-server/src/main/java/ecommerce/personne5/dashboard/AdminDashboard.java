package ecommerce.personne5.dashboard;

import ecommerce.personne5.model.PaiementStats;

/** Affichage console des statistiques agrégées. */
public class AdminDashboard {
    private AdminDashboard() {
    }

    public static void afficherDashboard(PaiementStats stats) {
        System.out.println("===== DASHBOARD ADMIN =====");
        System.out.println("Total paiements : " + stats.getTotalPaiements());
        System.out.println("Acceptes        : " + stats.getPaiementsAcceptes());
        System.out.println("Refuses         : " + stats.getPaiementsRefuses());
        System.out.println("En attente      : " + stats.getPaiementsEnAttente());
        System.out.println("Rembourses      : " + stats.getPaiementsRembourses());
        System.out.println("CA total        : " + String.format("%.2f MAD", stats.getChiffreAffaires()));
        System.out.println("===========================\n");
    }
}