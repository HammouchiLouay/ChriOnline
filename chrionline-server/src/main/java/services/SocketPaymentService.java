package services;

import common.ChrionlineLog;
import common.JsonUtil;
import common.Message;
import ecommerce.personne5.model.Coupon;
import ecommerce.personne5.model.Paiement;
import ecommerce.personne5.model.StatutCommande;
import ecommerce.personne5.model.StatutPaiement;
import ecommerce.personne5.model.TypePaiement;
import ecommerce.personne5.model.Wallet;
import ecommerce.personne5.service.PaiementService;
import models.Commande;
import persistence.PaymentHistoryDAO;
import persistence.SavedPaymentMethodDAO;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;

/**
 * Relie les requêtes socket {@link Message} au module paiement {@link PaiementService} (personne 5).
 * Enregistre l’historique des paiements et peut sauver un modèle de paiement masqué si le client le demande.
 */
public final class SocketPaymentService {

    private static final PaiementService PAIEMENT = new PaiementService();
    private static final Object PAY_LOCK = new Object();

    private SocketPaymentService() {}

    /**
     * {@code SIMULATE_PAYMENT} : charge utile JSON (clés plates pour {@link JsonUtil#toMap}) —
     * {@code commandeId}, {@code userId} (obligatoires), {@code typePaiement}, {@code coupon} optionnel,
     * {@code saveTemplate}, champs modèle : {@code holderName}, {@code lastFour}, {@code brand}, {@code expMonth},
     * {@code expYear}, {@code paypalCode}, {@code walletAlias}.
     */
    public static Message simulatePayment(Message request) {
        try {
            String raw = request.getPayload();
            if (raw == null || raw.trim().isEmpty()) {
                return err(request, "EMPTY_PAYLOAD");
            }
            Map<String, String> data = JsonUtil.toMap(raw);
            if (!data.containsKey("commandeId")) {
                return err(request, "MISSING_COMMANDE_ID");
            }
            if (!data.containsKey("userId")) {
                return err(request, "MISSING_USERID");
            }
            int commandeId = Integer.parseInt(data.get("commandeId").trim());
            int userId = Integer.parseInt(data.get("userId").trim());
            String typeStr = data.getOrDefault("typePaiement", "CARTE_BANCAIRE").trim().toUpperCase(Locale.ROOT);
            String couponCode = data.get("coupon");
            if (couponCode != null) {
                couponCode = couponCode.trim();
                if (couponCode.isEmpty()) {
                    couponCode = null;
                }
            }

            Commande serverCmd = CommandeService.getCommandeById(commandeId);
            if (serverCmd == null) {
                return err(request, "COMMANDE_NOT_FOUND");
            }
            if (serverCmd.getUserId() != userId) {
                return err(request, "FORBIDDEN");
            }
            String st = serverCmd.getStatus();
            if ("PAYEE".equals(st) || "ANNULEE".equals(st)) {
                return err(request, "COMMANDE_NOT_PAYABLE");
            }

            TypePaiement type = parseType(typeStr);
            Coupon coupon = PAIEMENT.verifierCoupon(couponCode);
            Wallet wallet = null;

            ecommerce.personne5.model.Commande eco =
                    new ecommerce.personne5.model.Commande(
                            String.valueOf(serverCmd.getId()),
                            LocalDate.now().toString(),
                            StatutCommande.EN_ATTENTE,
                            serverCmd.calculerTotal());

            Paiement paiement;
            synchronized (PAY_LOCK) {
                paiement = PAIEMENT.simulerPaiement(eco, type, coupon, wallet);
                if (paiement != null) {
                    PAIEMENT.confirmerPaiement(paiement, eco);
                }
            }

            String statutName =
                    paiement != null && paiement.getStatut() != null
                            ? paiement.getStatut().name()
                            : "ERREUR";
            double montantFinal = paiement != null ? paiement.getMontantFinal() : 0;
            String msgResume = paiement != null ? paiement.getMessage() : "Echec";
            String idPay = paiement != null ? paiement.getIdPaiement() : "NONE";

            try {
                PaymentHistoryDAO.insert(
                        commandeId,
                        userId,
                        type.name(),
                        idPay,
                        statutName,
                        montantFinal,
                        msgResume);
            } catch (Exception logEx) {
                ChrionlineLog.err("PaymentHistoryDAO: " + logEx.getMessage());
            }

            if (paiement == null) {
                try {
                    CommandeService.updateCommandeStatus(commandeId, "ANNULEE");
                } catch (Exception ignored) {
                }
                return err(request, "PAYMENT_FAILED");
            }

            if (paiement.getStatut() == StatutPaiement.ACCEPTE) {
                CommandeService.updateCommandeStatus(commandeId, "PAYEE");
            } else {
                CommandeService.updateCommandeStatus(commandeId, "ANNULEE");
            }

            boolean saveTemplate = parseBool(data.get("saveTemplate"));
            if (saveTemplate
                    && paiement.getStatut() == StatutPaiement.ACCEPTE) {
                try {
                    maybeSavePaymentTemplate(userId, type, data);
                } catch (Exception saveEx) {
                    ChrionlineLog.err("Saved payment template: " + saveEx.getMessage());
                }
            }

            return new Message(
                    "SIMULATE_PAYMENT",
                    request.getRequestId(),
                    "SUCCESS",
                    toJsonPayload(paiement),
                    "");
        } catch (Exception e) {
            return err(request, "INVALID_PAYLOAD");
        }
    }

    /** Interprète une valeur booléenne textuelle du client. */
    private static boolean parseBool(String v) {
        if (v == null) {
            return false;
        }
        String s = v.trim().toLowerCase(Locale.ROOT);
        return "true".equals(s) || "1".equals(s) || "yes".equals(s) || "on".equals(s);
    }

    /** Insère une ligne dans {@link persistence.SavedPaymentMethodDAO} selon le type de paiement. */
    private static void maybeSavePaymentTemplate(int userId, TypePaiement type, Map<String, String> data)
            throws Exception {
        switch (type) {
            case CARTE_BANCAIRE:
            case STRIPE:
            case PAIEMENT_2X:
            case PAIEMENT_3X:
                {
                    String last = digitsOnly(data.get("lastFour"), 4);
                    if (last.length() < 4) {
                        return;
                    }
                    String brand = nz(data.get("brand"), "Carte");
                    String holder = nz(data.get("holderName"), "Titulaire");
                    String em = nz(data.get("expMonth"), "");
                    String ey = nz(data.get("expYear"), "");
                    String label = brand + " •••• " + last;
                    String json =
                            "{\"holder\":\""
                                    + escJson(holder)
                                    + "\",\"lastFour\":\""
                                    + last
                                    + "\",\"brand\":\""
                                    + escJson(brand)
                                    + "\",\"expMonth\":\""
                                    + escJson(em)
                                    + "\",\"expYear\":\""
                                    + escJson(ey)
                                    + "\"}";
                    SavedPaymentMethodDAO.insert(userId, type.name(), label, json);
                }
                break;
            case PAYPAL:
                {
                    String code = paypalCodeFrom(data);
                    if (code.length() < 4) {
                        return;
                    }
                    String label = "PayPal •••• " + code.substring(Math.max(0, code.length() - 4));
                    String json = "{\"paypalCode\":\"" + escJson(code) + "\"}";
                    SavedPaymentMethodDAO.insert(userId, type.name(), label, json);
                }
                break;
            case WALLET:
                {
                    String alias = nz(data.get("walletAlias"), "").trim();
                    if (alias.isEmpty()) {
                        return;
                    }
                    String label = "Wallet — " + alias;
                    String json = "{\"walletAlias\":\"" + escJson(alias) + "\"}";
                    SavedPaymentMethodDAO.insert(userId, type.name(), label, json);
                }
                break;
            default:
                break;
        }
    }

    /** PayPal : préfère {@code paypalCode} ; anciens clients peuvent encore envoyer {@code paypalEmail}. */
    private static String paypalCodeFrom(Map<String, String> data) {
        String c = nz(data.get("paypalCode"), "").trim();
        if (!c.isEmpty()) {
            return c;
        }
        return nz(data.get("paypalEmail"), "").trim();
    }

    /** Extrait au plus {@code maxLen} chiffres depuis la fin. */
    private static String digitsOnly(String s, int maxLen) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (Character.isDigit(c)) {
                sb.append(c);
            }
        }
        String d = sb.toString();
        if (d.length() > maxLen) {
            return d.substring(d.length() - maxLen);
        }
        return d;
    }

    private static String nz(String s, String def) {
        return s == null || s.isBlank() ? def : s.trim();
    }

    private static String escJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** Retourne {@link TypePaiement#CARTE_BANCAIRE} si la valeur est inconnue. */
    private static TypePaiement parseType(String s) {
        if (s == null || s.isEmpty()) {
            return TypePaiement.CARTE_BANCAIRE;
        }
        try {
            return TypePaiement.valueOf(s);
        } catch (IllegalArgumentException e) {
            return TypePaiement.CARTE_BANCAIRE;
        }
    }

    /** Sérialise le résultat du paiement pour la réponse socket. */
    private static String toJsonPayload(Paiement p) {
        return "{"
                + "\"idPaiement\":\"" + esc(p.getIdPaiement()) + "\","
                + "\"idCommande\":\"" + esc(p.getIdCommande()) + "\","
                + "\"statut\":\"" + esc(String.valueOf(p.getStatut())) + "\","
                + "\"montantFinal\":" + p.getMontantFinal() + ","
                + "\"scoreFraude\":" + p.getScoreFraude() + ","
                + "\"message\":\"" + esc(p.getMessage()) + "\""
                + "}";
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static Message err(Message request, String code) {
        return new Message("SIMULATE_PAYMENT", request.getRequestId(), "ERROR", "", code);
    }
}
