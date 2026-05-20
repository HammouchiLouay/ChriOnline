package ecommerce.personne5.service;

/** Cashback forfaitaire sur le montant (démo). */
public class CashbackService {

    private static final double TAUX_CASHBACK = 0.05;

    public double calculerCashback(double montant) {

        return montant * TAUX_CASHBACK;
    }

    public void afficherCashback(double montant) {

        double cashback = calculerCashback(montant);

        System.out.println("Cashback gagné : " + cashback + " MAD");
    }
}
