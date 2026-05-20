package ecommerce.personne5.service;

/** Score de risque complémentaire basé sur le montant (démo). */
public class RiskAnalysisService {

    public int calculerScoreRisque(double montant) {

        int score = 0;

        if (montant > 3000) {

            score += 40;
        }

        if (montant > 6000) {

            score += 70;
        }

        if (montant > 10000) {

            score += 90;
        }

        return score;
    }
}