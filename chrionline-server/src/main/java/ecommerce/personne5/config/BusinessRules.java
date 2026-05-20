package ecommerce.personne5.config;

/**
 * Constantes métier du module paiement : seuils de livraison, fraude, montants minimum pour le fractionnement.
 */
public class BusinessRules {
    public static final double LIVRAISON_GRATUITE_A_PARTIR = 500.0;
    public static final double FRAIS_LIVRAISON_STANDARD = 40.0;
    public static final int SCORE_FRAUDE_MAX = 80;
    public static final double MONTANT_MIN_PAIEMENT_2X = 200.0;
    public static final double MONTANT_MIN_PAIEMENT_3X = 600.0;

    private BusinessRules() {
    }
}