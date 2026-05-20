package ecommerce.personne5.service;

import java.time.LocalTime;

/** Remise flash sur une plage horaire (démo). */
public class FlashSaleService {

    public double appliquerFlashSale(double montant) {

        LocalTime now = LocalTime.now();

        // Flash sale entre 20:00 et 22:00
        if (now.isAfter(LocalTime.of(20,0)) && now.isBefore(LocalTime.of(22,0))) {

            System.out.println("FLASH SALE ACTIVE (-40%)");

            return montant * 0.6;
        }

        return montant;
    }
}