package model;

import java.time.LocalDate;

/**
 * Campagne promotionnelle : période d’activité et remise en pourcentage sur un montant.
 */
public class PromotionCampaign {

    private String name;
    private LocalDate startDate;
    private LocalDate endDate;
    private double discount; // pourcentage

    public PromotionCampaign(String name, LocalDate startDate, LocalDate endDate, double discount) {
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
        this.discount = discount;
    }

    /** Indique si la date du jour est dans l’intervalle [start, end]. */
    public boolean isActive() {
        LocalDate today = LocalDate.now();
        return (today.isEqual(startDate) || today.isAfter(startDate)) &&
               (today.isEqual(endDate) || today.isBefore(endDate));
    }

    /** Applique la remise proportionnelle au pourcentage stocké. */
    public double applyDiscount(double amount) {
        return amount - (amount * discount);
    }

    public String getName() {
        return name;
    }

    public double getDiscount() {
        return discount;
    }
}