package ecommerce.personne5.model;

/** Modèle de paiement simulé : montants, statut, type, message et métadonnées de sécurité. */
public class Paiement {
    private String idPaiement;
    private double montantInitial;
    private double fraisLivraison;
    private double reductionCoupon;
    private double montantFinal;
    private String datePaiement;
    private StatutPaiement statut;
    private TypePaiement typePaiement;
    private String idCommande;
    private String message;
    private boolean paiementSecurise;
    private String tokenPaiement;
    private int scoreFraude;

    public Paiement(String idPaiement,
                    double montantInitial,
                    double fraisLivraison,
                    double reductionCoupon,
                    double montantFinal,
                    String datePaiement,
                    StatutPaiement statut,
                    TypePaiement typePaiement,
                    String idCommande,
                    String message,
                    boolean paiementSecurise,
                    String tokenPaiement,
                    int scoreFraude) {
        this.idPaiement = idPaiement;
        this.montantInitial = montantInitial;
        this.fraisLivraison = fraisLivraison;
        this.reductionCoupon = reductionCoupon;
        this.montantFinal = montantFinal;
        this.datePaiement = datePaiement;
        this.statut = statut;
        this.typePaiement = typePaiement;
        this.idCommande = idCommande;
        this.message = message;
        this.paiementSecurise = paiementSecurise;
        this.tokenPaiement = tokenPaiement;
        this.scoreFraude = scoreFraude;
    }

    public void traiterPaiement() {
        System.out.println("Traitement du paiement en cours...");
    }

    public String getIdPaiement() {
        return idPaiement;
    }

    public double getMontantInitial() {
        return montantInitial;
    }

    public double getFraisLivraison() {
        return fraisLivraison;
    }

    public double getReductionCoupon() {
        return reductionCoupon;
    }

    public double getMontantFinal() {
        return montantFinal;
    }

    public String getDatePaiement() {
        return datePaiement;
    }

    public StatutPaiement getStatut() {
        return statut;
    }

    public TypePaiement getTypePaiement() {
        return typePaiement;
    }

    public String getIdCommande() {
        return idCommande;
    }

    public String getMessage() {
        return message;
    }

    public boolean isPaiementSecurise() {
        return paiementSecurise;
    }

    public String getTokenPaiement() {
        return tokenPaiement;
    }

    public int getScoreFraude() {
        return scoreFraude;
    }

    public void setStatut(StatutPaiement statut) {
        this.statut = statut;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public void setMontantFinal(double montantFinal) {
        this.montantFinal = montantFinal;
    }

    @Override
    public String toString() {
        return "Paiement{" +
                "idPaiement='" + idPaiement + '\'' +
                ", montantInitial=" + montantInitial +
                ", fraisLivraison=" + fraisLivraison +
                ", reductionCoupon=" + reductionCoupon +
                ", montantFinal=" + montantFinal +
                ", datePaiement='" + datePaiement + '\'' +
                ", statut=" + statut +
                ", typePaiement=" + typePaiement +
                ", idCommande='" + idCommande + '\'' +
                ", message='" + message + '\'' +
                ", paiementSecurise=" + paiementSecurise +
                ", tokenPaiement='" + tokenPaiement + '\'' +
                ", scoreFraude=" + scoreFraude +
                '}';
    }
}