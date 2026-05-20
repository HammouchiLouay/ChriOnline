package server;

import chrionline.BaseDonnees;
import common.Message;
import common.JsonUtil;
import services.AccountDeletionService;
import services.AdminAuthService;
import services.AdminProductService;
import services.AuthService;
import services.CommandeService;
import services.EmailVerificationService;
import services.PasswordResetService;
import services.ProductListingService;
import services.ProductService;
import services.ProfileSecurityService;
import services.ProfileService;
import services.SavedPaymentService;
import services.SocketPaymentService;
import models.Commande;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Aiguille chaque message entrant (type en majuscules) vers le service métier approprié et renvoie la réponse.
 */
public class RequestRouter {

    /**
     * Dispatche selon {@link Message#getType()} : authentification, catalogue, commandes, paiement, etc.
     *
     * @return message de réponse (SUCCESS ou ERROR avec code)
     */
    public static Message route(Message message) {
        if (message == null) {
            return new Message("ERROR", "0", "ERROR", "", "NULL_MESSAGE");
        }

        try {
            String rawType = message.getType();
            if (rawType == null || rawType.trim().isEmpty()) {
                return new Message("UNKNOWN", message.getRequestId(), "ERROR", "", "EMPTY_TYPE");
            }

            String type = rawType.trim().toUpperCase();

            switch (type) {

                case "PING":
                    try {
                        BaseDonnees.verifyConnection();
                        return new Message(type, message.getRequestId(), "SUCCESS", "PONG", "");
                    } catch (SQLException e) {
                        return new Message(type, message.getRequestId(), "ERROR", "", "DB_UNAVAILABLE");
                    }

                case "LOGIN":
                    return AuthService.login(message);

                case "REGISTER":
                    return AuthService.register(message);

                case "ADMIN_CHALLENGE_REQUEST":
                    return AdminAuthService.requestChallenge(message);

                case "ADMIN_CHALLENGE_VERIFY":
                    return AdminAuthService.verifyChallenge(message);

                case "FORGOT_PASSWORD":
                    return PasswordResetService.forgotPassword(message);

                case "RESET_PASSWORD":
                    return PasswordResetService.resetPassword(message);

                case "EMAIL_VERIFY_SEND":
                    return EmailVerificationService.send(message);

                case "EMAIL_VERIFY_CONFIRM":
                    return EmailVerificationService.confirm(message);

                case "PROFILE_OTP_SEND":
                    return ProfileSecurityService.sendOtp(message);

                case "UPDATE_PROFILE":
                    return ProfileService.updateProfile(message);

                case "DELETE_ACCOUNT":
                    return AccountDeletionService.deleteAccount(message);

                case "PRODUCT_LIST":
                    return ProductService.list(message);

                case "PRODUCT_CATEGORIES":
                    return ProductService.categories(message);

                case "PRODUCT_DETAILS":
                    return ProductService.details(message);

                case "STOCK_UPDATE":
                    return ProductService.updateStock(message);

                case "SUBMIT_PRODUCT_LISTING":
                    return ProductListingService.submit(message);

                case "LIST_PENDING_PRODUCTS":
                    return ProductListingService.listPending(message);

                case "LIST_MY_PRODUCT_LISTINGS":
                    return ProductListingService.listMine(message);

                case "APPROVE_PRODUCT_LISTING":
                    return ProductListingService.approve(message);

                case "REJECT_PRODUCT_LISTING":
                    return ProductListingService.reject(message);

                case "ADMIN_PRODUCT_LIST":
                    return AdminProductService.list(message);

                case "ADMIN_PRODUCT_CATEGORIES":
                    return AdminProductService.categories(message);

                case "ADMIN_PRODUCT_CREATE":
                    return AdminProductService.create(message);

                case "ADMIN_PRODUCT_UPDATE_STOCK":
                    return AdminProductService.updateStock(message);

                case "ADMIN_PRODUCT_DELETE":
                    return AdminProductService.delete(message);

                case "CREATE_COMMANDE":
                    try {
                        String payload = message.getPayload();
                        if (payload == null || payload.trim().isEmpty()) {
                            return error(type, message, "EMPTY_PAYLOAD");
                        }
                        Map<String, String> data = JsonUtil.toMap(payload);
                        if (data == null || data.isEmpty()) {
                            return error(type, message, "INVALID_JSON");
                        }
                        if (data.containsKey("produits")) {
                            if (!data.containsKey("userId")) {
                                return error(type, message, "MISSING_USERID");
                            }
                            int userId = Integer.parseInt(data.get("userId"));
                            String produits = data.get("produits");
                            Commande cmd = CommandeService.createCommandeAvecProduits(userId, produits);
                            return success(type, message, cmd.toJson());
                        }
                        if (!data.containsKey("userId") || !data.containsKey("total")) {
                            return error(type, message, "MISSING_FIELDS");
                        }
                        int userId = Integer.parseInt(data.get("userId"));
                        double total = Double.parseDouble(data.get("total"));
                        Commande cmd = CommandeService.createCommande(userId, total);
                        return success(type, message, cmd.toJson());
                    } catch (SQLException e) {
                        return error(type, message, "DB_ERROR");
                    } catch (Exception e) {
                        return error(type, message, "INVALID_JSON");
                    }

                case "VALIDER_COMMANDE":
                    try {
                        String payload = message.getPayload();
                        if (payload == null || payload.trim().isEmpty()) {
                            return error(type, message, "EMPTY_PAYLOAD");
                        }
                        int cmdId = Integer.parseInt(payload);
                        boolean ok = CommandeService.validerCommande(cmdId);
                        return new Message(
                                type,
                                message.getRequestId(),
                                ok ? "SUCCESS" : "ERROR",
                                ok ? "COMMANDE_VALIDEE" : "",
                                ok ? "" : "NOT_FOUND"
                        );
                    } catch (SQLException e) {
                        return error(type, message, "DB_ERROR");
                    } catch (Exception e) {
                        return error(type, message, "INVALID_PAYLOAD");
                    }

                case "LOGOUT":
                    try {
                        String payload = message.getPayload();
                        String token = extractSessionToken(payload);
                        if (token != null && !token.isBlank()) {
                            SessionRegistry.revoke(token);
                        }
                        return success(type, message, "OK");
                    } catch (Exception e) {
                        return error(type, message, "INVALID_PAYLOAD");
                    }

                case "GET_COMMANDES":
                    try {
                        String payload = message.getPayload();
                        if (payload == null || payload.trim().isEmpty()) {
                            return error(type, message, "EMPTY_PAYLOAD");
                        }
                        String sessionToken = extractSessionToken(payload);
                        if (sessionToken == null || sessionToken.isBlank()) {
                            return error(type, message, "AUTH_REQUIRED");
                        }
                        Integer sessionUserId = SessionRegistry.resolveUser(sessionToken);
                        if (sessionUserId == null || sessionUserId <= 0) {
                            return error(type, message, "SESSION_INVALID");
                        }
                        List<Commande> commandes = CommandeService.getCommandesByUser(sessionUserId);
                        StringBuilder json = new StringBuilder("[");
                        for (int i = 0; i < commandes.size(); i++) {
                            json.append(commandes.get(i).toJson());
                            if (i < commandes.size() - 1) {
                                json.append(",");
                            }
                        }
                        json.append("]");
                        return success(type, message, json.toString());
                    } catch (SQLException e) {
                        return error(type, message, "DB_ERROR");
                    } catch (Exception e) {
                        return error(type, message, "INVALID_PAYLOAD");
                    }

                case "ANNULER_COMMANDE":
                    try {
                        String payload = message.getPayload();
                        if (payload == null || payload.trim().isEmpty()) {
                            return error(type, message, "EMPTY_PAYLOAD");
                        }
                        String trimmed = payload.trim();
                        if (!trimmed.startsWith("{")) {
                            return error(type, message, "AUTH_REQUIRED");
                        }
                        Map<String, String> data = JsonUtil.toMap(payload);
                        if (data == null || data.isEmpty()) {
                            return error(type, message, "INVALID_JSON");
                        }
                        String sessionToken = data.get("sessionToken");
                        if (sessionToken == null || sessionToken.isBlank()) {
                            return error(type, message, "AUTH_REQUIRED");
                        }
                        Integer sessionUserId = SessionRegistry.resolveUser(sessionToken.trim());
                        if (sessionUserId == null || sessionUserId <= 0) {
                            return error(type, message, "SESSION_INVALID");
                        }
                        String cid = data.get("commandeId");
                        if (cid == null || cid.isBlank()) {
                            return error(type, message, "MISSING_COMMANDE_ID");
                        }
                        int cmdId = Integer.parseInt(cid.trim());
                        Commande existing = CommandeService.getCommandeById(cmdId);
                        if (existing == null) {
                            return error(type, message, "NOT_FOUND");
                        }
                        if (existing.getUserId() != sessionUserId) {
                            return error(type, message, "FORBIDDEN");
                        }
                        boolean ok = CommandeService.annulerCommande(cmdId);
                        return new Message(
                                type,
                                message.getRequestId(),
                                ok ? "SUCCESS" : "ERROR",
                                ok ? "COMMANDE_ANNULEE" : "",
                                ok ? "" : "IMPOSSIBLE"
                        );
                    } catch (SQLException e) {
                        return error(type, message, "DB_ERROR");
                    } catch (Exception e) {
                        return error(type, message, "INVALID_PAYLOAD");
                    }

                case "LIST_SAVED_PAYMENT_METHODS":
                    return SavedPaymentService.list(message);

                case "DELETE_SAVED_PAYMENT_METHOD":
                    return SavedPaymentService.delete(message);

                case "SIMULATE_PAYMENT":
                    return SocketPaymentService.simulatePayment(message);

                default:
                    return error(type, message, "UNKNOWN_REQUEST");
            }

        } catch (Exception e) {
            return error("SERVER_ERROR", message, "SERVER_ERROR");
        }
    }

    /** Réponse d’erreur typée avec code court (ex. {@code EMPTY_PAYLOAD}). */
    private static Message error(String type, Message msg, String code) {
        return new Message(type, msg.getRequestId(), "ERROR", "", code);
    }

    /** Réponse de succès avec charge utile (souvent JSON). */
    private static Message success(String type, Message msg, String payload) {
        return new Message(type, msg.getRequestId(), "SUCCESS", payload, "");
    }

    private static String extractSessionToken(String payload) {
        if (payload == null) {
            return null;
        }
        String p = payload.trim();
        if (p.isEmpty()) {
            return null;
        }
        if (p.startsWith("{")) {
            Map<String, String> m = JsonUtil.toMap(p);
            if (m == null) {
                return null;
            }
            String t = m.get("sessionToken");
            return t != null ? t.trim() : null;
        }
        return null;
    }
}
