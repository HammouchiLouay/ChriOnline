package ui;

import java.util.HashMap;
import java.util.Map;

/**
 * Traduit les codes d’erreur serveur en messages courts en français — jamais de bruts protocole ou SQL.
 */
public final class UiMessages {

    private static final Map<String, String> ERR = new HashMap<>();

    static {
        ERR.put(
                "BAD_CREDENTIALS",
                "Adresse e-mail, numéro de téléphone ou mot de passe incorrect.");
        ERR.put("EMAIL_OR_PHONE_EXISTS", "Cette adresse e-mail ou ce numéro est déjà utilisé.");
        ERR.put("INVALID_PHONE", "Numéro de téléphone invalide.");
        ERR.put("MISSING_FIELDS", "Veuillez remplir tous les champs obligatoires.");
        ERR.put("EMPTY_PAYLOAD", "Données incomplètes. Réessayez.");
        ERR.put("DB_ERROR", "Service temporairement indisponible. Réessayez plus tard.");
        ERR.put("BAD_PASSWORD", "Mot de passe actuel incorrect.");
        ERR.put("INVALID_PASSWORD", "Le nouveau mot de passe est trop court.");
        ERR.put("EMAIL_TAKEN", "Cette adresse e-mail est déjà utilisée par un autre compte.");
        ERR.put("PHONE_TAKEN", "Ce numéro est déjà associé à un autre compte.");
        ERR.put("NOT_FOUND", "Compte introuvable.");
        ERR.put("INVALID_CODE", "Code incorrect ou expiré. Demandez un nouveau code.");
        ERR.put("INVALID", "Données invalides.");
        ERR.put("PAYMENT_FAILED", "Le paiement n'a pas abouti.");
        ERR.put("MISSING_COMMANDE_ID", "Référence de commande manquante.");
        ERR.put("COMMANDE_NOT_FOUND", "Commande introuvable.");
        ERR.put("COMMANDE_NOT_PAYABLE", "Cette commande ne peut plus être payée.");
        ERR.put("INVALID_PAYLOAD", "Données invalides.");
        ERR.put("DB_UNAVAILABLE", "Le service de données est indisponible sur l'hôte du serveur.");
        ERR.put(
                "EMAIL_NOT_VERIFIED",
                "Vérifiez d'abord votre adresse e-mail (page Compte) ou utilisez la récupération par téléphone.");
        ERR.put(
                "BAD_OR_MISSING_OTP",
                "Code de sécurité incorrect ou expiré. Demandez un nouveau code puis réessayez.");
        ERR.put("AUTH_REQUIRED", "Connectez-vous pour accéder à vos commandes.");
        ERR.put("SESSION_INVALID", "Session expirée ou invalide. Reconnectez-vous.");
        ERR.put("NOT_SELLER", "Cette action est réservée aux comptes vendeur.");
        ERR.put("NOT_ADMIN", "Cette action est réservée aux administrateurs.");
        ERR.put("INVALID_STOCK", "Stock invalide. Utilisez un nombre entier positif ou zéro.");
        ERR.put("INVALID_PRICE", "Prix invalide. Utilisez un nombre positif, par exemple 249.99.");
        ERR.put("PRODUCT_SCHEMA_OUTDATED", "La table products n’est pas à jour. Exécutez sql/migration_product_listings_seller_admin.sql puis relancez le serveur.");
        ERR.put("SKU_EXISTS", "Ce SKU existe déjà. Laissez le champ SKU vide pour générer une référence automatiquement, ou choisissez un autre SKU.");
        ERR.put("INVALID_SKU", "SKU invalide. Utilisez seulement lettres, chiffres, tiret, underscore ou point.");
        ERR.put("SKU_TOO_LONG", "SKU trop long : maximum 40 caractères.");
        ERR.put("INVALID_IMAGE_URL", "URL image invalide. Elle doit commencer par http:// ou https://, ou rester vide.");
        ERR.put("INVALID_CATEGORY", "Catégorie invalide. Choisissez une catégorie dans la liste.");
        ERR.put("CRYPTO_UNSUPPORTED", "Le serveur ne supporte pas le mode RSA/AES actuellement.");
        ERR.put("CRYPTO_REQUIRED", "Le serveur exige le mode RSA/AES.");
        ERR.put("PROTOCOL_ERROR", "Erreur de protocole socket. Vérifiez que le client et le serveur sont à jour.");
        ERR.put("IMAGE_URL_TOO_LONG", "URL image trop longue : maximum 768 caractères.");
        ERR.put("FIELD_TOO_LONG", "Un champ est trop long pour la base de données. Raccourcissez le nom, la marque, la catégorie ou l’URL image.");
        ERR.put("INTEGRITY_ERROR", "Création impossible à cause d’une contrainte de base de données. Vérifiez le SKU et relancez le serveur après migration.");
        ERR.put(
                "IMPOSSIBLE",
                "Cette commande ne peut plus être annulée (déjà validée, payée ou traitée).");
        ERR.put("FORBIDDEN", "Vous ne pouvez pas annuler cette commande.");
    }

    private UiMessages() {}

    /** Texte lisible pour un code d’erreur (ne jamais afficher JSON brut ni pile d’exceptions). */
    public static String errorCode(String code) {
        if (code == null || code.isBlank()) {
            return "Une erreur est survenue. Réessayez.";
        }
        return ERR.getOrDefault(code, "Action impossible pour le moment. Réessayez ou contactez le support.");
    }

    /** Message générique d’échec. */
    public static String genericFailure() {
        return "Une erreur est survenue. Réessayez.";
    }

    /** Échec de connexion réseau au serveur. */
    public static String networkFailure() {
        return "Connexion au serveur impossible. Vérifiez l'hôte, le port et que l'application serveur est lancée.";
    }
}
