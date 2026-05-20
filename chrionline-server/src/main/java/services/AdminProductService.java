package services;

import chrionline.BaseDonnees;
import chrionline.User;
import chrionline.UserDAO;
import common.ChrionlineLog;
import common.JsonUtil;
import common.Message;
import product.AdminProductRow;
import product.ProductCatalogDAO;
import server.SessionRegistry;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * CRUD catalogue réservé aux administrateurs : création immédiate, suppression, recherche et mise à jour du stock.
 */
public final class AdminProductService {

    private static final int SKU_MAX = 40;
    private static final int NAME_MAX = 255;
    private static final int BRAND_MAX = 120;
    private static final int CATEGORY_MAX = 120;
    private static final int IMAGE_URL_MAX = 768;

    private AdminProductService() {}

    public static Message list(Message request) {
        try {
            Map<String, String> data = parseJsonPayload(request);
            if (data == null) {
                return err(request, "EMPTY_PAYLOAD");
            }
            ChrionlineLog.info("ADMIN_PRODUCT_CREATE debug: received payload keys=" + data.keySet());
            Integer uid = resolveSessionUser(data);
            if (!isAdminUser(uid)) {
                return uid == null ? err(request, "AUTH_REQUIRED") : err(request, "NOT_ADMIN");
            }
            String search = data.getOrDefault("search", "");
            List<AdminProductRow> rows = ProductCatalogDAO.listAllForAdmin(search);
            return new Message(request.getType(), request.getRequestId(), "SUCCESS", toJsonArray(rows), "");
        } catch (SQLException e) {
            ChrionlineLog.err("ADMIN_PRODUCT_LIST failed", e);
            return err(request, sqlErrorCode(e));
        } catch (Exception e) {
            ChrionlineLog.err("ADMIN_PRODUCT_LIST failed", e);
            return err(request, "DB_ERROR");
        }
    }

    public static Message categories(Message request) {
        try {
            Map<String, String> data = parseJsonPayload(request);
            if (data == null) {
                return err(request, "EMPTY_PAYLOAD");
            }
            Integer uid = resolveSessionUser(data);
            if (!isAdminUser(uid)) {
                return uid == null ? err(request, "AUTH_REQUIRED") : err(request, "NOT_ADMIN");
            }
            return new Message(
                    request.getType(),
                    request.getRequestId(),
                    "SUCCESS",
                    toJsonStringArray(ProductCatalogDAO.distinctCategoriesForAdmin()),
                    "");
        } catch (SQLException e) {
            ChrionlineLog.err("ADMIN_PRODUCT_CATEGORIES failed", e);
            // La liste par défaut côté client reste utilisable, donc on ne bloque pas l’interface.
            return new Message(
                    request.getType(),
                    request.getRequestId(),
                    "SUCCESS",
                    toJsonStringArray(ProductCatalogDAO.defaultAdminCategories()),
                    "");
        } catch (Exception e) {
            ChrionlineLog.err("ADMIN_PRODUCT_CATEGORIES failed", e);
            return new Message(
                    request.getType(),
                    request.getRequestId(),
                    "SUCCESS",
                    toJsonStringArray(ProductCatalogDAO.defaultAdminCategories()),
                    "");
        }
    }

    public static Message create(Message request) {
        try {
            Map<String, String> data = parseJsonPayload(request);
            if (data == null) {
                return err(request, "EMPTY_PAYLOAD");
            }
            Integer uid = resolveSessionUser(data);
            if (!isAdminUser(uid)) {
                return uid == null ? err(request, "AUTH_REQUIRED") : err(request, "NOT_ADMIN");
            }

            String nom = clean(data.get("nomProduit"));
            String cat = clean(data.get("categorieMetier"));
            String marque = clean(data.get("marque"));
            String desc = clean(data.get("description"));
            String img = clean(data.get("imageUrl"));
            String sku = clean(data.get("sku"));

            String validationError = validateCreatePayload(nom, sku, marque, cat, img);
            if (validationError != null) {
                return err(request, validationError);
            }

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

            int newId =
                    ProductCatalogDAO.insertAdminDirectApproved(
                            sku, nom, marque, cat, prix, stock, desc, img, uid);
            return new Message(
                    request.getType(),
                    request.getRequestId(),
                    "SUCCESS",
                    "{\"productId\":" + newId + ",\"status\":\"APPROVED\"}",
                    "");
        } catch (SQLException e) {
            logSqlFailure("ADMIN_PRODUCT_CREATE", e);
            return err(request, sqlErrorCode(e), sqlDebugPayload(e));
        } catch (Exception e) {
            ChrionlineLog.err("ADMIN_PRODUCT_CREATE failed", e);
            return err(request, "DB_ERROR", debugPayload(e));
        }
    }

    public static Message updateStock(Message request) {
        try {
            Map<String, String> data = parseJsonPayload(request);
            if (data == null) {
                return err(request, "EMPTY_PAYLOAD");
            }
            Integer uid = resolveSessionUser(data);
            if (!isAdminUser(uid)) {
                return uid == null ? err(request, "AUTH_REQUIRED") : err(request, "NOT_ADMIN");
            }
            int productId;
            int stock;
            try {
                productId = Integer.parseInt(data.getOrDefault("productId", "").trim());
                stock = Integer.parseInt(data.getOrDefault("stock", "").trim());
            } catch (NumberFormatException e) {
                return err(request, "INVALID_PAYLOAD");
            }
            if (productId <= 0) {
                return err(request, "INVALID_PAYLOAD");
            }
            if (stock < 0) {
                return err(request, "INVALID_STOCK");
            }
            boolean ok = ProductCatalogDAO.updateStock(productId, stock);
            if (!ok) {
                return err(request, "NOT_FOUND");
            }
            return new Message(request.getType(), request.getRequestId(), "SUCCESS", "OK", "");
        } catch (SQLException e) {
            ChrionlineLog.err("ADMIN_PRODUCT_UPDATE_STOCK failed", e);
            return err(request, sqlErrorCode(e));
        } catch (Exception e) {
            ChrionlineLog.err("ADMIN_PRODUCT_UPDATE_STOCK failed", e);
            return err(request, "DB_ERROR");
        }
    }

    public static Message delete(Message request) {
        try {
            Map<String, String> data = parseJsonPayload(request);
            if (data == null) {
                return err(request, "EMPTY_PAYLOAD");
            }
            Integer uid = resolveSessionUser(data);
            if (!isAdminUser(uid)) {
                return uid == null ? err(request, "AUTH_REQUIRED") : err(request, "NOT_ADMIN");
            }
            int productId;
            try {
                productId = Integer.parseInt(data.getOrDefault("productId", "").trim());
            } catch (NumberFormatException e) {
                return err(request, "INVALID_PAYLOAD");
            }
            if (productId <= 0) {
                return err(request, "INVALID_PAYLOAD");
            }
            boolean ok = ProductCatalogDAO.deleteProductById(productId);
            if (!ok) {
                return err(request, "NOT_FOUND");
            }
            return new Message(request.getType(), request.getRequestId(), "SUCCESS", "OK", "");
        } catch (SQLException e) {
            ChrionlineLog.err("ADMIN_PRODUCT_DELETE failed", e);
            return err(request, sqlErrorCode(e));
        } catch (Exception e) {
            ChrionlineLog.err("ADMIN_PRODUCT_DELETE failed", e);
            return err(request, "DB_ERROR");
        }
    }

    private static String validateCreatePayload(String nom, String sku, String marque, String cat, String imageUrl) {
        if (nom.isBlank() || cat.isBlank()) {
            return "MISSING_FIELDS";
        }
        if (!ProductCatalogDAO.isKnownAdminCategory(cat)) {
            return "INVALID_CATEGORY";
        }
        if (nom.length() > NAME_MAX || marque.length() > BRAND_MAX || cat.length() > CATEGORY_MAX) {
            return "FIELD_TOO_LONG";
        }
        if (!sku.isBlank()) {
            if (sku.length() > SKU_MAX) {
                return "SKU_TOO_LONG";
            }
            if (!sku.matches("[A-Za-z0-9._-]+")) {
                return "INVALID_SKU";
            }
        }
        if (!imageUrl.isBlank()) {
            if (imageUrl.length() > IMAGE_URL_MAX) {
                return "IMAGE_URL_TOO_LONG";
            }
            String lower = imageUrl.toLowerCase(Locale.ROOT);
            if (!(lower.startsWith("http://") || lower.startsWith("https://"))) {
                return "INVALID_IMAGE_URL";
            }
        }
        return null;
    }

    private static String sqlErrorCode(SQLException e) {
        String msg = e.getMessage() != null ? e.getMessage() : "";
        String state = e.getSQLState() != null ? e.getSQLState() : "";
        String low = msg.toLowerCase(Locale.ROOT);
        if (e instanceof SQLIntegrityConstraintViolationException
                || "23000".equals(state)
                || low.contains("duplicate")
                || low.contains("duplicata")) {
            if (low.contains("sku") || low.contains("duplicate_sku")) {
                return "SKU_EXISTS";
            }
            return "INTEGRITY_ERROR";
        }
        if (state.startsWith("42") || low.contains("unknown column") || low.contains("product_schema_outdated")) {
            return "PRODUCT_SCHEMA_OUTDATED";
        }
        if (low.contains("data too long") || low.contains("field_too_long")) {
            return "FIELD_TOO_LONG";
        }
        if (low.contains("invalid_sku_format")) {
            return "INVALID_SKU";
        }
        return "DB_ERROR";
    }

    private static boolean isAdminUser(Integer uid) throws Exception {
        if (uid == null || uid <= 0) {
            return false;
        }
        try (Connection c = BaseDonnees.getConnection()) {
            UserDAO dao = new UserDAO(c);
            User u = dao.findById(uid);
            return u != null && isAdmin(u);
        }
    }

    private static boolean isAdmin(User u) {
        return u.get_role() != null && "ADMIN".equalsIgnoreCase(u.get_role().trim());
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

    private static String toJsonArray(List<AdminProductRow> rows) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < rows.size(); i++) {
            AdminProductRow r = rows.get(i);
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
                    .append("\",\"marque\":\"")
                    .append(esc(r.marque()))
                    .append("\",\"categorieMetier\":\"")
                    .append(esc(r.categorieMetier()))
                    .append("\",\"stock\":")
                    .append(r.stock())
                    .append(",\"listingStatus\":\"")
                    .append(esc(r.listingStatus()))
                    .append("\",\"prixUsd\":")
                    .append(r.prixUsd())
                    .append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String toJsonStringArray(List<String> values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append("\"").append(esc(values.get(i))).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    private static String clean(String s) {
        return s != null ? s.trim() : "";
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", " ");
    }

    private static Message err(Message request, String code) {
        return new Message(request.getType(), request.getRequestId(), "ERROR", "", code);
    }

    private static Message err(Message request, String code, String debugPayload) {
        return new Message(request.getType(), request.getRequestId(), "ERROR", debugPayload != null ? debugPayload : "", code);
    }

    private static void logSqlFailure(String context, SQLException e) {
        ChrionlineLog.err(
                context
                        + " SQL failed: state="
                        + e.getSQLState()
                        + ", errorCode="
                        + e.getErrorCode()
                        + ", message="
                        + e.getMessage(),
                e);
    }

    private static String sqlDebugPayload(SQLException e) {
        return "{\"sqlState\":\""
                + esc(e.getSQLState())
                + "\",\"errorCode\":"
                + e.getErrorCode()
                + ",\"message\":\""
                + esc(e.getMessage())
                + "\"}";
    }

    private static String debugPayload(Exception e) {
        return "{\"exception\":\""
                + esc(e.getClass().getSimpleName())
                + "\",\"message\":\""
                + esc(e.getMessage())
                + "\"}";
    }
}
