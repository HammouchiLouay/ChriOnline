package ecommerce.personne5.service;

/** Vérifie si une transaction n’a pas dépassé un délai de 5 minutes (démo). */
public class PaymentTimeoutService {

    private static final long TIMEOUT = 300000; // 5 minutes

    public boolean verifierTimeout(long debutTransaction) {

        long maintenant = System.currentTimeMillis();

        return (maintenant - debutTransaction) < TIMEOUT;
    }
}