package models;

import java.io.Serializable;

/**
 * Ligne de commande : produit, libellé affiché, quantité, prix unitaire.
 */
public class LigneCommande implements Serializable {
    private static final long serialVersionUID = 1L;

    private int produitId;
    private String nom;
    private int quantite;
    private double prixUnitaire;

    public LigneCommande() {}

    /** Construit une ligne avec les montants unitaires. */
    public LigneCommande(int produitId, String nom, int quantite, double prixUnitaire) {
        this.produitId = produitId;
        this.nom = nom;
        this.quantite = quantite;
        this.prixUnitaire = prixUnitaire;
    }

    public int getProduitId() {
        return produitId;
    }

    public String getNom() {
        return nom;
    }

    public int getQuantite() {
        return quantite;
    }

    public double getPrixUnitaire() {
        return prixUnitaire;
    }

    public double calculerSousTotal() {
        return quantite * prixUnitaire;
    }

    /** JSON d’une ligne pour inclusion dans {@link Commande#toJson()}. */
    public String toJson() {
        return "{"
                + "\"produitId\":" + produitId
                + ",\"nom\":\"" + escape(nom) + "\""
                + ",\"quantite\":" + quantite
                + ",\"prixUnitaire\":" + prixUnitaire
                + "}";
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override
    public String toString() {
        return "LigneCommande{produitId=" + produitId + ", nom='" + nom + "', quantite=" + quantite + ", prixUnitaire=" + prixUnitaire + "}";
    }
}
