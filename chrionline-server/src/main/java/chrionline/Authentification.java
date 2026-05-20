package chrionline;

import common.PasswordHasher;

import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * Règles métier d’inscription et de connexion au-dessus du {@link UserDAO} (sans protocole socket).
 * Les mots de passe sont vérifiés avec {@link PasswordHasher} (BCrypt) ; les anciennes lignes en clair sont
 * migrées en hash après une connexion réussie.
 */
public class Authentification {

    private final UserDAO userDAO;

    /** Construit un DAO utilisateur sur la connexion JDBC fournie. */
    public Authentification(Connection connexion) {
        this.userDAO = new UserDAO(connexion);
    }

    /**
     * Enregistre un utilisateur si l’e-mail et le téléphone sont uniques. Le préfixe
     * {@code hash_password} doit déjà contenir un hash BCrypt.
     *
     * @return {@code false} si doublon e-mail ou téléphone
     */
    public boolean register(User user) throws SQLException {
        if (userDAO.emailExists(user.get_email())) {
            return false;
        }
        if (userDAO.phoneNumberExists(user.get_phone_number())) {
            return false;
        }
        userDAO.createUser(user);
        return true;
    }

    /**
     * Connexion par e-mail ou par numéro de téléphone (chiffres, comme à l’inscription).
     * Si la chaîne contient {@code @}, recherche par e-mail ; sinon normalisation des chiffres puis
     * {@code phone_number}.
     *
     * @return l’utilisateur ou {@code null} si inconnu / mot de passe incorrect
     */
    public User loginByEmailOrPhone(String emailOrPhone, String plainPassword) throws SQLException {
        User u = findUserForLogin(emailOrPhone);
        if (u == null) {
            return null;
        }
        String stored = u.get_hash_password();
        if (!PasswordHasher.verify(plainPassword, stored)) {
            return null;
        }
        if (PasswordHasher.needsRehash(stored)) {
            String upgraded = PasswordHasher.hash(plainPassword);
            userDAO.updatePassword(u.get_id_user(), upgraded);
            u.set_hash_password(upgraded);
        }
        return u;
    }

    /** Résout l’utilisateur à partir d’un identifiant e-mail ou téléphone. */
    private User findUserForLogin(String emailOrPhone) throws SQLException {
        if (emailOrPhone == null) {
            return null;
        }
        String id = emailOrPhone.trim();
        if (id.isEmpty()) {
            return null;
        }
        if (id.indexOf('@') >= 0) {
            return userDAO.findByEmail(id);
        }
        String digits = PhoneNumberLookup.digitsOnly(id);
        if (digits.isEmpty()) {
            return null;
        }
        Integer phone = PhoneNumberLookup.parseStoredPhoneInt(digits);
        if (phone == null) {
            return null;
        }
        return userDAO.findByPhoneNumber(phone);
    }

    /** Fabrique d’exemple pour démos / tests (IDs à gérer proprement en production). */
    public static User buildSampleUser(int id, String username, String email, int phone, String plainPassword, String role) {
        User u = new User();
        u.set_id_user(id);
        u.set_username(username);
        u.set_email(email);
        u.set_phone_number(phone);
        u.set_hash_password(PasswordHasher.hash(plainPassword));
        u.set_date_creation(Date.valueOf(LocalDate.now()));
        u.set_role(role);
        return u;
    }
}
