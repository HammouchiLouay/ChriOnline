package ecommerce.personne5.service;

import ecommerce.personne5.model.Paiement;

import java.util.List;

/** Agrégats simples sur une liste de paiements (revenu, moyenne). */
public class PaymentAnalyticsService {

    public double calculerRevenuTotal(List<Paiement> paiements) {

        double total = 0;

        for (Paiement p : paiements) {

            total += p.getMontantFinal();
        }

        return total;
    }

    public void afficherAnalyse(List<Paiement> paiements) {

        System.out.println("===== ANALYSE DES PAIEMENTS =====");

        System.out.println("Nombre de paiements : " + paiements.size());

        System.out.println("Revenu total : " + calculerRevenuTotal(paiements));

        System.out.println("=================================");
    }
}