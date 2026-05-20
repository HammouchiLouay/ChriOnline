package ecommerce.personne5.service;

import ecommerce.personne5.model.Wallet;

/** Recharge et débit du portefeuille virtuel. */
public class WalletService {

    public void rechargerWallet(Wallet wallet, double montant) {
        wallet.recharger(montant);
        System.out.println("Wallet recharge de " + montant + " MAD.");
    }

    public boolean payerAvecWallet(Wallet wallet, double montant) {
        boolean succes = wallet.payer(montant);
        if (succes) {
            System.out.println("Paiement wallet effectue avec succes.");
        } else {
            System.out.println("Solde wallet insuffisant.");
        }
        return succes;
    }

    public void afficherSolde(Wallet wallet) {
        System.out.println("Solde wallet : " + String.format("%.2f MAD", wallet.getSolde()));
    }
}