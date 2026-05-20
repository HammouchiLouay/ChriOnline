package ecommerce.personne5.utils;

import ecommerce.personne5.model.Paiement;

/** Génère un reçu texte formaté pour un {@link Paiement}. */
public class RecuGenerator {
    private RecuGenerator() {
    }

    public static String genererRecu(Paiement paiement) {
        return "\n======= RECU DE PAIEMENT =======\n" +
                "Paiement ID      : " + paiement.getIdPaiement() + "\n" +
                "Commande ID      : " + paiement.getIdCommande() + "\n" +
                "Montant initial  : " + String.format("%.2f MAD", paiement.getMontantInitial()) + "\n" +
                "Livraison        : " + String.format("%.2f MAD", paiement.getFraisLivraison()) + "\n" +
                "Reduction coupon : -" + String.format("%.2f MAD", paiement.getReductionCoupon()) + "\n" +
                "Montant final    : " + String.format("%.2f MAD", paiement.getMontantFinal()) + "\n" +
                "Type             : " + paiement.getTypePaiement() + "\n" +
                "Statut           : " + paiement.getStatut() + "\n" +
                "Securise         : " + (paiement.isPaiementSecurise() ? "OUI" : "NON") + "\n" +
                "Token            : " + paiement.getTokenPaiement() + "\n" +
                "Fraude Score     : " + paiement.getScoreFraude() + "%\n" +
                "Date             : " + paiement.getDatePaiement() + "\n" +
                "Message          : " + paiement.getMessage() + "\n" +
                "Merci pour votre achat.\n" +
                "===============================\n";
    }
}