package ecommerce.personne5.service;

import java.util.Random;

/** Score de risque pseudo-aléatoire borné, utilisé pour refuser certains paiements simulés. */
public class FraudDetectionService {
    private final Random random = new Random();

    public int calculerScoreFraude(double montant) {
        int base = (int) Math.min(60, montant / 20);
        int aleatoire = random.nextInt(41);
        int score = Math.min(100, base + aleatoire);
        System.out.println("Score fraude : " + score + "%");
        return score;
    }

    public boolean transactionAutorisee(double montant, int seuilMax) {
        int score = calculerScoreFraude(montant);
        return score <= seuilMax;
    }
}