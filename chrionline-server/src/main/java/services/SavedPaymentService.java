package services;

import common.JsonUtil;
import common.Message;
import persistence.SavedPaymentMethodDAO;

import java.util.Map;

/**
 * API socket pour les modèles de paiement enregistrés (carte masquée, libellé PayPal, etc.).
 */
public final class SavedPaymentService {

    private SavedPaymentService() {}

    /** {@code LIST_SAVED_PAYMENT_METHODS} : charge utile = identifiant utilisateur (nombre en texte). */
    public static Message list(Message request) {
        try {
            String raw = request.getPayload();
            if (raw == null || raw.trim().isEmpty()) {
                return err(request, "EMPTY_PAYLOAD");
            }
            int userId = Integer.parseInt(raw.trim());
            String json = SavedPaymentMethodDAO.toJsonArray(SavedPaymentMethodDAO.listByUser(userId));
            return new Message("LIST_SAVED_PAYMENT_METHODS", request.getRequestId(), "SUCCESS", json, "");
        } catch (Exception e) {
            return err(request, "INVALID_PAYLOAD");
        }
    }

    /** {@code DELETE_SAVED_PAYMENT_METHOD} : JSON avec {@code userId} et {@code idMethode}. */
    public static Message delete(Message request) {
        try {
            String raw = request.getPayload();
            if (raw == null || raw.trim().isEmpty()) {
                return err(request, "EMPTY_PAYLOAD");
            }
            Map<String, String> m = JsonUtil.toMap(raw);
            if (!m.containsKey("userId") || !m.containsKey("idMethode")) {
                return err(request, "MISSING_FIELDS");
            }
            int userId = Integer.parseInt(m.get("userId").trim());
            int idMethode = Integer.parseInt(m.get("idMethode").trim());
            boolean ok = SavedPaymentMethodDAO.deleteForUser(idMethode, userId);
            return new Message(
                    "DELETE_SAVED_PAYMENT_METHOD",
                    request.getRequestId(),
                    ok ? "SUCCESS" : "ERROR",
                    ok ? "OK" : "",
                    ok ? "" : "NOT_FOUND");
        } catch (Exception e) {
            return err(request, "INVALID_PAYLOAD");
        }
    }

    private static Message err(Message request, String code) {
        return new Message(request.getType(), request.getRequestId(), "ERROR", "", code);
    }
}
