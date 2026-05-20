package ecommerce.personne5.service;

/** Recommandation heuristique du mode de paiement selon le montant (démo). */
public class PaymentRecommendationService {

    public String recommander(double montant) {

        if (montant < 200) {

            return "WALLET recommandé";

        } else if (montant < 1000) {

            return "CARTE BANCAIRE recommandée";

        } else {

            return "PAIEMENT EN PLUSIEURS FOIS recommandé";
        }
    }
}