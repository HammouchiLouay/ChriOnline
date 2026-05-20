package ecommerce.personne5.service;

/** Conversions indicatives MAD vers USD / EUR (démo). */
public class CurrencyService {

    public double madToUSD(double mad) {
        return mad * 0.10;
    }

    public double madToEUR(double mad) {
        return mad * 0.092;
    }

    public void afficherConversions(double mad) {
        System.out.println("Montant en MAD : " + String.format("%.2f", mad));
        System.out.println("Equivalent USD : " + String.format("%.2f", madToUSD(mad)));
        System.out.println("Equivalent EUR : " + String.format("%.2f", madToEUR(mad)));
    }
}