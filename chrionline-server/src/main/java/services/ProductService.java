package services;

import common.Message;
import common.JsonUtil;
import product.Product;
import product.ProductRepository;

import java.util.ArrayList;
import java.util.Base64;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Réponses socket pour le catalogue : liste paginée ou filtrée par catégorie, détail produit, catégories, stock.
 */
public class ProductService {

    /**
     * {@code PRODUCT_LIST} : charge utile vide (tout le catalogue) ou JSON avec {@code category}, et optionnellement
     * {@code limit} / {@code offset} pour la pagination.
     */
    public static Message list(Message request) {
        List<Product> products;
        String raw = request.getPayload();
        if (raw == null || raw.trim().isEmpty()) {
            products = ProductRepository.getAll();
        } else {
            Map<String, String> m = JsonUtil.toMap(raw);
            if (m.containsKey("limit")) {
                try {
                    int limit = Math.min(500, Math.max(1, Integer.parseInt(m.get("limit").trim())));
                    int offset = Math.max(0, Integer.parseInt(m.getOrDefault("offset", "0").trim()));
                    String cat = m.get("category");
                    products = ProductRepository.getPage(cat != null ? cat : "", offset, limit);
                } catch (NumberFormatException e) {
                    String cat = m.get("category");
                    products = ProductRepository.getByCategory(cat != null ? cat : "");
                }
            } else {
                String cat = m.get("category");
                products = ProductRepository.getByCategory(cat != null ? cat : "");
            }
        }
        try {
            byte[] data = JsonUtil.toBinary(products);
            String payload = Base64.getEncoder().encodeToString(data);
            return new Message(
                    "PRODUCT_LIST",
                    request.getRequestId(),
                    "SUCCESS",
                    payload,
                    ""
            );
        } catch (Exception e) {
            return new Message(
                    "PRODUCT_LIST",
                    request.getRequestId(),
                    "ERROR",
                    "",
                    "SERIALIZATION_ERROR"
            );
        }
    }

    /** {@code PRODUCT_DETAILS} : charge utile = identifiant produit (chaîne). */
    public static Message details(Message request) {
        String id = request.getPayload();
        return ProductRepository.findById(id)
                .map(p -> {
                    try {
                        byte[] data = JsonUtil.toBinary(p);
                        String payload = Base64.getEncoder().encodeToString(data);
                        return new Message("PRODUCT_DETAILS", request.getRequestId(), "SUCCESS", payload, "");
                    } catch (Exception e) {
                        return new Message("PRODUCT_DETAILS", request.getRequestId(), "ERROR", "", "SERIALIZATION_ERROR");
                    }
                })
                .orElseGet(() -> new Message("PRODUCT_DETAILS", request.getRequestId(), "ERROR", "", "NOT_FOUND"));
    }

    public static Message updateStock(Message request) {
        try {
            byte[] data = Base64.getDecoder().decode(request.getPayload());
            UpdateStockRequest usr = JsonUtil.fromBinary(data, UpdateStockRequest.class);
            boolean ok = ProductRepository.updateStock(usr.id, usr.stock);
            if (ok) {
                return new Message("STOCK_UPDATE", request.getRequestId(), "SUCCESS", "", "");
            } else {
                return new Message("STOCK_UPDATE", request.getRequestId(), "ERROR", "", "NOT_FOUND");
            }
        } catch (Exception e) {
            return new Message("STOCK_UPDATE", request.getRequestId(), "ERROR", "", "DESERIALIZATION_ERROR");
        }
    }

    /** {@code PRODUCT_CATEGORIES} : renvoie un tableau JSON de noms de catégories (inclut {@code Tous} en tête). */
    public static Message categories(Message request) {
        try {
            List<String> sorted = new ArrayList<>(ProductRepository.distinctCategories());
            sorted.remove("Tous");
            Collections.sort(sorted);
            sorted.add(0, "Tous");
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (String s : sorted) {
                if (!first) {
                    sb.append(",");
                }
                first = false;
                sb.append("\"").append(esc(s)).append("\"");
            }
            sb.append("]");
            return new Message(
                    "PRODUCT_CATEGORIES",
                    request.getRequestId(),
                    "SUCCESS",
                    sb.toString(),
                    "");
        } catch (Exception e) {
            return new Message("PRODUCT_CATEGORIES", request.getRequestId(), "ERROR", "", "ERROR");
        }
    }

    private static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static class UpdateStockRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        public String id;
        public int stock;
    }
}
