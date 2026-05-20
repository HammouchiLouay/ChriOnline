package ecommerce.personne5.model;

/** Coupon pourcentage actif ou non. */
public class Coupon {
    private String code;
    private double reductionPourcentage;
    private boolean actif;

    public Coupon(String code, double reductionPourcentage, boolean actif) {
        this.code = code;
        this.reductionPourcentage = reductionPourcentage;
        this.actif = actif;
    }

    public String getCode() {
        return code;
    }

    public double getReductionPourcentage() {
        return reductionPourcentage;
    }

    public boolean isActif() {
        return actif;
    }

    @Override
    public String toString() {
        return "Coupon{" +
                "code='" + code + '\'' +
                ", reductionPourcentage=" + reductionPourcentage +
                ", actif=" + actif +
                '}';
    }
}