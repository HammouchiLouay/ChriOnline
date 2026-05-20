package ecommerce.personne5.utils;

/** Notifications simulées sur la console (succès, échec, remboursement). */
public class NotificationUtil {
    private NotificationUtil() {
    }

    public static void envoyerNotificationSucces(String idCommande) {
        System.out.println("Notification : paiement de la commande " + idCommande + " confirme.");
        System.out.println("Email de confirmation envoye avec succes.");
    }

    public static void envoyerNotificationEchec(String idCommande) {
        System.out.println("Notification : echec du paiement pour la commande " + idCommande + ".");
    }

    public static void envoyerNotificationRemboursement(String idCommande) {
        System.out.println("Notification : remboursement effectue pour la commande " + idCommande + ".");
    }
}