package ecommerce.personne5.model;

/** Portefeuille avec solde et opérations recharge / paiement. */
public class Wallet {
    private double solde;

    public Wallet(double solde) {
        this.solde = solde;
    }

    public double getSolde() {
        return solde;
    }

    public void recharger(double montant) {
        if (montant > 0) {
            solde += montant;
        }
    }

    public boolean payer(double montant) {
        if (montant > 0 && solde >= montant) {
            solde -= montant;
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return "Wallet{" +
                "solde=" + solde +
                '}';
    }
}