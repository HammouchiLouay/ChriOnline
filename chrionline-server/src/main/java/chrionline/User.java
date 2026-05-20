package chrionline;

import java.sql.Date;

/**
 * Modèle domaine représentant une ligne de la table MySQL {@code user}. Utilisé par le DAO JDBC et les
 * services qui renvoient des données utilisateur au client.
 */
public class User {

    private Integer id_user;
    private String username;
    private String hash_password;
    private String email;
    private Integer phone_number;
    private Date date_creation;
    private String role;
    /** Si {@code true}, l’adresse e-mail est confirmée et peut recevoir codes OTP / reset. */
    private boolean emailVerified;
    /** Clé publique PEM (X509) utilisée pour l’auth admin challenge-response (nullable). */
    private String adminPublicKeyPem;

    /** Bean vide pour le mapping JDBC ou remplissage manuel. */
    public User() {}

    /**
     * Constructeur complet (identifiant, hash BCrypt, coordonnées, rôle, état de vérification e-mail).
     */
    public User(
            Integer id_user,
            String username,
            String hash_password,
            String email,
            Integer phone_number,
            Date date_creation,
            String role,
            boolean emailVerified) {
        this.id_user = id_user;
        this.username = username;
        this.hash_password = hash_password;
        this.email = email;
        this.phone_number = phone_number;
        this.date_creation = date_creation;
        this.role = role;
        this.emailVerified = emailVerified;
        this.adminPublicKeyPem = null;
    }

    /** @return identifiant utilisateur */
    public Integer get_id_user() {
        return id_user;
    }

    /** @return nom d’utilisateur affiché */
    public String get_username() {
        return username;
    }

    /** @return hash BCrypt (ou legacy en clair le temps de la migration) */
    public String get_hash_password() {
        return hash_password;
    }

    /** @return adresse e-mail */
    public String get_email() {
        return email;
    }

    /** @return numéro de téléphone stocké en entier (schéma INT) */
    public Integer get_phone_number() {
        return phone_number;
    }

    /** @return date de création du compte */
    public Date get_date_creation() {
        return date_creation;
    }

    /** @return rôle applicatif (ex. CLIENT) */
    public String get_role() {
        return role;
    }

    /** @return {@code true} si l’e-mail a été vérifié */
    public boolean isEmailVerified() {
        return emailVerified;
    }

    /** @return clé publique PEM (ou chaîne vide si absente). */
    public String getAdminPublicKeyPem() {
        return adminPublicKeyPem == null ? "" : adminPublicKeyPem;
    }

    /** @throws IllegalArgumentException si {@code id_user} est null */
    public void set_id_user(Integer id_user) {
        if (id_user == null) {
            throw new IllegalArgumentException("ID Utilisateur ne peut pas être null.");
        }
        this.id_user = id_user;
    }

    /** @throws IllegalArgumentException si null */
    public void set_username(String username) {
        if (username == null) {
            throw new IllegalArgumentException("Nom de l'utilisateur ne peut pas être null.");
        }
        this.username = username;
    }

    /** @throws IllegalArgumentException si null */
    public void set_hash_password(String hash_password) {
        if (hash_password == null) {
            throw new IllegalArgumentException("Le mot de passe ne peut pas être null.");
        }
        this.hash_password = hash_password;
    }

    /** @throws IllegalArgumentException si null */
    public void set_email(String email) {
        if (email == null) {
            throw new IllegalArgumentException("L'email ne peut pas être null.");
        }
        this.email = email;
    }

    /** @throws IllegalArgumentException si null */
    public void set_phone_number(Integer phone_number) {
        if (phone_number == null) {
            throw new IllegalArgumentException("Le numéro de téléphone ne peut pas être null.");
        }
        this.phone_number = phone_number;
    }

    /** @throws IllegalArgumentException si null */
    public void set_date_creation(Date date_creation) {
        if (date_creation == null) {
            throw new IllegalArgumentException("La date de création ne peut pas être null.");
        }
        this.date_creation = date_creation;
    }

    /** @throws IllegalArgumentException si null */
    public void set_role(String role) {
        if (role == null) {
            throw new IllegalArgumentException("Le rôle ne peut pas être null.");
        }
        this.role = role;
    }

    /** Met à jour le drapeau de vérification e-mail sans contraintes sur les autres champs. */
    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public void setAdminPublicKeyPem(String adminPublicKeyPem) {
        this.adminPublicKeyPem = adminPublicKeyPem;
    }
}
