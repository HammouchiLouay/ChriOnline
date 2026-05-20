package services;

import chrionline.BaseDonnees;
import chrionline.User;
import chrionline.UserDAO;
import common.JsonUtil;
import common.Message;
import product.ProductCatalogDAO;
import product.ProductListingInfo;
import server.SessionRegistry;

import java.sql.Connection;
import java.util.List;
import java.util.Map;

/**
 * Soumission vendeur et modération administrateur sur le catalogue ({@code listing_status}).
 */
public final class ProductListingService {

    private ProductListingService() {}

    public static Message submit(Message request) {
        try {
            Map<String, String> data = parseJsonPayload(request);
            if (data == null) {
                return err(request, "EMPTY_PAYLOAD");
            }
            Integer uid = resolveSessionUser(data);
            if (uid == null || uid <= 0) {
                return err(request, "AUTH_REQUIRED");
            }
            try (Connection c = BaseDonnees.getConnection()) {
                UserDAO dao = new UserDAO(c);
                User u = dao.findById(uid);
                if (u == null || !isSeller(u)) {
                    return err(request, "NOT_SELLER");
                }
            }
            String nom = data.get("nomProduit");
            if (nom == null || nom.isBlank()) {
                return err(request, "MISSING_FIELDS");
            }
            String cat = data.getOrDefault("categorieMetier", "Général").trim();
            double prix;
            try {
                prix = Double.parseDouble(data.getOrDefault("prixUsd", "0").trim().replace(',', '.'));
            } catch (NumberFormatException e) {
                return err(request, "INVALID_PRICE");
            }
            if (prix <= 0) {
                return err(request, "INVALID_PRICE");
            }
            int stock;
            try {
                stock = Integer.parseInt(data.getOrDefault("stock", "0").trim());
            } catch (NumberFormatException e) {
                return err(request, "INVALID_STOCK");
            }
            if (stock < 0) {
                return err(request, "INVALID_STOCK");
            }
            String marque = data.get("marque");
            String desc = data.get("description");
            String img = data.get("imageUrl");
            String sku = data.get("sku");
            int newId =
                    ProductCatalogDAO.insertPendingListing(
                            sku, nom.trim(), marque, cat, prix, stock, desc, img, uid);
            return new Message(
                    request.getType(),
                    request.getRequestId(),
                    "SUCCESS",
                    "{\"productId\":" + newId + ",\"status\":\"PENDING\"}",
                    "");
        } catch (Exception e) {
            return err(request, "DB_ERROR");
        }
    }

    public static Message listPending(Message request) {
        try {
            Map<String, String> data = parseJsonPayload(request);
            if (data == null) {
                return err(request, "EMPTY_PAYLOAD");
            }
            Integer uid = resolveSessionUser(data);
            if (uid == null || uid <= 0) {
                return err(request, "AUTH_REQUIRED");
            }
            try (Connection c = BaseDonnees.getConnection()) {
                UserDAO dao = new UserDAO(c);
                User u = dao.findById(uid);
                if (u == null || !isAdmin(u)) {
                    return err(request, "NOT_ADMIN");
                }
            }
            List<ProductListingInfo> rows = ProductCatalogDAO.listPending();
            return new Message(
                    request.getType(), request.getRequestId(), "SUCCESS", toJsonArray(rows), "");
        } catch (Exception e) {
            return err(request, "DB_ERROR");
        }
    }

    public static Message listMine(Message request) {
        try {
            Map<String, String> data = parseJsonPayload(request);
            if (data == null) {
                return err(request, "EMPTY_PAYLOAD");
            }
            Integer uid = resolveSessionUser(data);
            if (uid == null || uid <= 0) {
                return err(request, "AUTH_REQUIRED");
            }
            try (Connection c = BaseDonnees.getConnection()) {
                UserDAO dao = new UserDAO(c);
                User u = dao.findById(uid);
                if (u == null || !isSeller(u)) {
                    return err(request, "NOT_SELLER");
                }
            }
            List<ProductListingInfo> rows = ProductCatalogDAO.listBySellerId(uid);
            return new Message(
                    request.getType(), request.getRequestId(), "SUCCESS", toJsonArray(rows), "");
        } catch (Exception e) {
            return err(request, "DB_ERROR");
        }
    }

    public static Message approve(Message request) {
        return moderate(request, true);
    }

    public static Message reject(Message request) {
        return moderate(request, false);
    }

    private static Message moderate(Message request, boolean approve) {
        try {
            Map<String, String> data = parseJsonPayload(request);
            if (data == null) {
                return err(request, "EMPTY_PAYLOAD");
            }
            Integer uid = resolveSessionUser(data);
            if (uid == null || uid <= 0) {
                return err(request, "AUTH_REQUIRED");
            }
            try (Connection c = BaseDonnees.getConnection()) {
                UserDAO dao = new UserDAO(c);
                User u = dao.findById(uid);
                if (u == null || !isAdmin(u)) {
                    return err(request, "NOT_ADMIN");
                }
            }
            if (!data.containsKey("productId") || data.get("productId") == null || data.get("productId").isBlank()) {
                return err(request, "INVALID_PAYLOAD");
            }
            int productId;
            try {
                productId = Integer.parseInt(data.get("productId").trim());
            } catch (Exception e) {
                return err(request, "INVALID_PAYLOAD");
            }
            boolean ok;
            if (approve) {
                ok = ProductCatalogDAO.approve(productId, uid);
            } else {
                String reason = data.get("reason");
                ok = ProductCatalogDAO.reject(productId, uid, reason);
            }
            if (!ok) {
                return err(request, "NOT_FOUND");
            }
            return new Message(request.getType(), request.getRequestId(), "SUCCESS", "OK", "");
        } catch (Exception e) {
            return err(request, "DB_ERROR");
        }
    }

    private static Map<String, String> parseJsonPayload(Message request) {
        String raw = request.getPayload();
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        return JsonUtil.toMap(raw);
    }

    private static Integer resolveSessionUser(Map<String, String> data) {
        if (data == null) {
            return null;
        }
        String t = data.get("sessionToken");
        if (t == null || t.isBlank()) {
            return null;
        }
        return SessionRegistry.resolveUser(t.trim());
    }

    private static boolean isAdmin(User u) {
        return u.get_role() != null && "ADMIN".equalsIgnoreCase(u.get_role().trim());
    }

    private static boolean isSeller(User u) {
        return u.get_role() != null && "SELLER".equalsIgnoreCase(u.get_role().trim());
    }

    private static String toJsonArray(List<ProductListingInfo> rows) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < rows.size(); i++) {
            ProductListingInfo r = rows.get(i);
            if (i > 0) {
                sb.append(",");
            }
            sb.append("{")
                    .append("\"productId\":")
                    .append(r.productId())
                    .append(",\"nomProduit\":\"")
                    .append(esc(r.nomProduit()))
                    .append("\",\"sku\":\"")
                    .append(esc(r.sku()))
                    .append("\",\"sellerId\":")
                    .append(r.sellerId())
                    .append(",\"listingStatus\":\"")
                    .append(esc(r.listingStatus()))
                    .append("\",\"submittedAtMs\":")
                    .append(r.submittedAtMs())
                    .append(",\"rejectionReason\":\"")
                    .append(esc(r.rejectionReason()))
                    .append("\"}");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static Message err(Message request, String code) {
        return new Message(request.getType(), request.getRequestId(), "ERROR", "", code);
    }
}
