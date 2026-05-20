package ecommerce.personne5.utils;

import ecommerce.personne5.model.Commande;
import ecommerce.personne5.model.TypePaiement;

/** Validations basiques sur commande et montants avant simulation. */
public class PaiementValidator {
    private PaiementValidator() {
    }

    public static boolean commandeValide(Commande commande) {
        return commande != null && commande.getIdCommande() != null && !commande.getIdCommande().trim().isEmpty();
    }

    public static boolean montantValide(double montant) {
        return montant > 0;
    }

    public static boolean typePaiementValide(TypePaiement typePaiement) {
        return typePaiement != null;
    }

    public static boolean valider(Commande commande, double montant, TypePaiement typePaiement) {
        return commandeValide(commande) && montantValide(montant) && typePaiementValide(typePaiement);
    }
}