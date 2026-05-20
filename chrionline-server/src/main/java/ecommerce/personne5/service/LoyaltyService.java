package ecommerce.personne5.service;

import ecommerce.personne5.model.Commande;

/** Points de fidélité dérivés du montant de commande (démo). */
public class LoyaltyService {

    public int calculerPoints(Commande commande) {

        if (commande == null) {
            return 0;
        }

        double montant = commande.getTotal();

        return (int) (montant / 10); // 1 point pour chaque 10 MAD
    }

    public void afficherPoints(int points) {

        System.out.println("Points fidelite gagnes : " + points);
    }
}