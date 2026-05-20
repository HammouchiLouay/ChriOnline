package ecommerce.personne5.model;

/** Cycle de vie d’une commande côté module paiement. */
public enum StatutCommande {
    EN_ATTENTE,
    VALIDEE,
    ANNULEE,
    PAYEE,
    REMBOURSEE
}