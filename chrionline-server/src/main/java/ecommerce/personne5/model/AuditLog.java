package ecommerce.personne5.model;

/** Entrée de journal d’audit (horodatage, acteur, action, détail). */
public class AuditLog {
    private String dateHeure;
    private String acteur;
    private String action;
    private String details;

    public AuditLog(String dateHeure, String acteur, String action, String details) {
        this.dateHeure = dateHeure;
        this.acteur = acteur;
        this.action = action;
        this.details = details;
    }

    public String getDateHeure() {
        return dateHeure;
    }

    public String getActeur() {
        return acteur;
    }

    public String getAction() {
        return action;
    }

    public String getDetails() {
        return details;
    }

    @Override
    public String toString() {
        return "[" + dateHeure + "] " + acteur + " - " + action + " - " + details;
    }
}