package ecommerce.personne5.utils;

import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

/** Génère des identifiants uniques pour commandes et paiements (compteurs + date). */
public class IdGeneratorUtil {
    private static final AtomicInteger paiementCounter = new AtomicInteger(1000);
    private static final AtomicInteger commandeCounter = new AtomicInteger(100);

    private IdGeneratorUtil() {
    }

    public static String genererIdPaiement() {
        return "PAY-" + LocalDate.now().getYear() + "-" + paiementCounter.incrementAndGet();
    }

    public static String genererIdCommande() {
        return "CMD-" + LocalDate.now().getYear() + "-" + commandeCounter.incrementAndGet();
    }
}