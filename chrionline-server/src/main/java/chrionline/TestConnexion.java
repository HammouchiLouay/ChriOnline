package chrionline;

import java.sql.Connection;
import java.sql.SQLException;

/** Test rapide : vérifie les paramètres JDBC de {@link BaseDonnees}. */
public class TestConnexion {

    /** Ouvre une connexion et affiche succès ou erreur SQL. */
    public static void main(String[] args) {
        try (Connection c = BaseDonnees.getConnection()) {
            System.out.println("Connexion OK: " + !c.isClosed());
        } catch (SQLException e) {
            System.err.println("Echec connexion MySQL — verifiez BaseDonnees (URL, user, mot de passe, base chrionline).");
            e.printStackTrace();
        }
    }
}
