package chrionline;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Accès JDBC à la table {@code user}. Une instance réutilise la {@link Connection} fournie au constructeur.
 */
public class UserDAO {

    private final Connection connexion;

    public UserDAO(Connection connexion) {
        this.connexion = connexion;
    }

    /**
     * Calcule le prochain {@code id_user} lorsque la table n’utilise pas {@code AUTO_INCREMENT} sur cette colonne.
     */
    public int getNextUserId() throws SQLException {
        String query = "SELECT COALESCE(MAX(id_user), 0) + 1 AS n FROM user";
        try (PreparedStatement statement = connexion.prepareStatement(query);
                ResultSet rs = statement.executeQuery()) {
            if (rs.next()) {
                return rs.getInt("n");
            }
        }
        return 1;
    }

    /** Insère une nouvelle ligne utilisateur (tous les champs, y compris {@code email_verified}). */
    public void createUser(User user) throws SQLException {
        String query =
                "INSERT INTO user (id_user, username, email, phone_number, hash_password, date_creation, role, email_verified) VALUES (?,?,?,?,?,?,?,?)";
        PreparedStatement statement = connexion.prepareStatement(query);
        statement.setInt(1, user.get_id_user());
        statement.setString(2, user.get_username());
        statement.setString(3, user.get_email());
        statement.setInt(4, user.get_phone_number());
        statement.setString(5, user.get_hash_password());
        statement.setDate(6, user.get_date_creation());
        statement.setString(7, user.get_role());
        statement.setBoolean(8, user.isEmailVerified());
        statement.executeUpdate();
    }

    /** Charge un utilisateur par identifiant, ou {@code null} si absent. */
    public User findById(int id_user) throws SQLException {
        String query = "SELECT * FROM user WHERE id_user = ?";
        try (PreparedStatement statement = connexion.prepareStatement(query)) {
            statement.setInt(1, id_user);
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return mapUser(rs);
                }
            }
        }
        return null;
    }

    /** Recherche par égalité stricte sur la colonne {@code email}. */
    public User findByEmail(String email) throws SQLException {
        String query = "SELECT * FROM user WHERE email = ?";
        PreparedStatement statement = connexion.prepareStatement(query);
        statement.setString(1, email);
        ResultSet rs = statement.executeQuery();
        if (rs.next()) {
            return mapUser(rs);
        }
        return null;
    }

    /** Indique si un e-mail est déjà présent (unicité). */
    public boolean emailExists(String email) throws SQLException {
        String query = "SELECT COUNT(*) FROM user WHERE email = ?";
        PreparedStatement statement = connexion.prepareStatement(query);
        statement.setString(1, email);
        ResultSet rs = statement.executeQuery();
        if (rs.next()) {
            return rs.getInt(1) > 0;
        }
        return false;
    }

    /** Recherche par numéro de téléphone (colonne INT). */
    public User findByPhoneNumber(Integer phone_number) throws SQLException {
        String query = "SELECT * FROM user WHERE phone_number = ?";
        PreparedStatement statement = connexion.prepareStatement(query);
        statement.setInt(1, phone_number);
        ResultSet rs = statement.executeQuery();
        if (rs.next()) {
            return mapUser(rs);
        }
        return null;
    }

    /** Indique si un numéro de téléphone est déjà utilisé. */
    public boolean phoneNumberExists(Integer phone_number) throws SQLException {
        String query = "SELECT COUNT(*) FROM user WHERE phone_number = ?";
        PreparedStatement statement = connexion.prepareStatement(query);
        statement.setInt(1, phone_number);
        ResultSet rs = statement.executeQuery();
        if (rs.next()) {
            return rs.getInt(1) > 0;
        }
        return false;
    }

    /** Met à jour le pseudo. */
    public void updateUsername(Integer id_user, String username) throws SQLException {
        String query = "UPDATE user SET username = ? WHERE id_user = ?";
        PreparedStatement statement = connexion.prepareStatement(query);
        statement.setString(1, username);
        statement.setInt(2, id_user);
        statement.executeUpdate();
    }

    /** Met à jour le hash du mot de passe (chaîne déjà produite par {@link common.PasswordHasher}). */
    public void updatePassword(Integer id_user, String hash_password) throws SQLException {
        String query = "UPDATE user SET hash_password = ? WHERE id_user = ?";
        PreparedStatement statement = connexion.prepareStatement(query);
        statement.setString(1, hash_password);
        statement.setInt(2, id_user);
        statement.executeUpdate();
    }

    /** Met à jour l’adresse e-mail. */
    public void updateEmail(Integer id_user, String email) throws SQLException {
        String query = "UPDATE user SET email = ? WHERE id_user = ?";
        PreparedStatement statement = connexion.prepareStatement(query);
        statement.setString(1, email);
        statement.setInt(2, id_user);
        statement.executeUpdate();
    }

    /** Met à jour le numéro de téléphone. */
    public void updatePhoneNumber(Integer id_user, Integer phone_number) throws SQLException {
        String query = "UPDATE user SET phone_number = ? WHERE id_user = ?";
        PreparedStatement statement = connexion.prepareStatement(query);
        statement.setInt(1, phone_number);
        statement.setInt(2, id_user);
        statement.executeUpdate();
    }

    /** Supprime la ligne utilisateur. */
    public void deleteUser(Integer id_user) throws SQLException {
        String query = "DELETE FROM user WHERE id_user = ?";
        PreparedStatement statement = connexion.prepareStatement(query);
        statement.setInt(1, id_user);
        statement.executeUpdate();
    }

    /** Met à jour le booléen {@code email_verified}. */
    public void updateEmailVerified(int id_user, boolean verified) throws SQLException {
        String query = "UPDATE user SET email_verified = ? WHERE id_user = ?";
        try (PreparedStatement statement = connexion.prepareStatement(query)) {
            statement.setBoolean(1, verified);
            statement.setInt(2, id_user);
            statement.executeUpdate();
        }
    }

    /** Construit un {@link User} depuis une ligne courante du {@link ResultSet}. */
    private static User mapUser(ResultSet rs) throws SQLException {
        User u =
                new User(
                rs.getInt("id_user"),
                rs.getString("username"),
                rs.getString("hash_password"),
                rs.getString("email"),
                rs.getInt("phone_number"),
                rs.getDate("date_creation"),
                rs.getString("role"),
                readEmailVerified(rs));
        u.setAdminPublicKeyPem(readAdminPublicKeyPem(rs));
        return u;
    }

    /**
     * Lit {@code email_verified} ; si la colonne est absente (ancien schéma), retourne {@code false} sans
     * faire échouer le mapping.
     */
    private static boolean readEmailVerified(ResultSet rs) {
        try {
            return rs.getBoolean("email_verified");
        } catch (SQLException e) {
            return false;
        }
    }

    /** Lit {@code admin_public_key_pem} ; si colonne absente, retourne chaîne vide. */
    private static String readAdminPublicKeyPem(ResultSet rs) {
        try {
            String v = rs.getString("admin_public_key_pem");
            return v == null ? "" : v;
        } catch (SQLException e) {
            return "";
        }
    }
}
