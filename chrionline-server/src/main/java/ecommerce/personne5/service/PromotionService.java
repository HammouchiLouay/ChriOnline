package ecommerce.personne5.service;

import java.time.DayOfWeek;
import java.time.LocalDate;

/**
 * Promotions automatiques (week-end, Black Friday, etc.) appliquées au montant avant coupon.
 */
public class PromotionService {

    // Appliquer les promotions automatiques (date / weekend)
    public double appliquerPromotion(double montant) {

        LocalDate today = LocalDate.now();

        // Promotion Black Friday
        if (today.getMonthValue() == 11 && today.getDayOfMonth() == 25) {
            System.out.println("Promotion Black Friday appliquee (-30%)");
            return montant * 0.7;
        }

        // Promotion weekend
        DayOfWeek day = today.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            System.out.println("Promotion Weekend appliquee (-10%)");
            return montant * 0.9;
        }

        return montant;
    }

    // Vérifier et appliquer un coupon promo
    public double verifierCoupon(String code, double montant) {

        if (code == null || code.trim().isEmpty()) {
            System.out.println("Aucun coupon saisi.");
            return montant;
        }

        code = code.trim().toUpperCase();

        switch (code) {
            case "PROMO10":
                System.out.println("Coupon PROMO10 applique (-10%)");
                return montant * 0.9;

            case "PROMO20":
                System.out.println("Coupon PROMO20 applique (-20%)");
                return montant * 0.8;

            case "PROMO30":
                System.out.println("Coupon PROMO30 applique (-30%)");
                return montant * 0.7;

            default:
                System.out.println("Coupon invalide.");
                return montant;
        }
    }
}