package ecommerce.personne5.service;

/** Nouvelles tentatives de paiement simulées avec succès aléatoire (démo). */
public class RetryPaymentService {

    public boolean retryPaiement(int tentativeMax) {
        for (int i = 1; i <= tentativeMax; i++) {
            System.out.println("Tentative paiement : " + i);
            if (Math.random() > 0.5) {
                System.out.println("Paiement reussi apres retry.");
                return true;
            }
        }
        System.out.println("Paiement echoue apres plusieurs tentatives.");
        return false;
    }
}