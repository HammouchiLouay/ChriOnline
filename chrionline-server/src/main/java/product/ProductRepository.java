package product;

import common.ChrionlineLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
/**
 * Catalogue produits : source principale MySQL {@code products} via {@link ProductCatalogDAO}, repli en mémoire si JDBC échoue.
 */
public final class ProductRepository {

    private static final List<Product> FALLBACK = new ArrayList<>();

    static {
        FALLBACK.add(new Product("001", "Widget", "A basic widget", 9.99, 100));
        FALLBACK.add(new Product("002", "Gadget", "A useful gadget", 19.99, 50));
        FALLBACK.add(new Product("003", "Doohickey", "An advanced doohickey", 29.99, 25));
    }

    private ProductRepository() {}

    /** Tous les produits ou liste de repli. */
    public static List<Product> getAll() {
        try {
            return Collections.unmodifiableList(new ArrayList<>(ProductCatalogDAO.loadAll()));
        } catch (Exception e) {
            ChrionlineLog.err("ProductRepository: JDBC load failed, using fallback — " + e.getMessage());
            return Collections.unmodifiableList(new ArrayList<>(FALLBACK));
        }
    }

    public static List<Product> getByCategory(String category) {
        if (category == null || category.isBlank() || "Tous".equalsIgnoreCase(category.trim())) {
            return getAll();
        }
        try {
            return Collections.unmodifiableList(new ArrayList<>(ProductCatalogDAO.loadByCategory(category)));
        } catch (Exception e) {
            ChrionlineLog.err("ProductRepository: category query failed — " + e.getMessage());
            return getAll();
        }
    }

    /**
     * Tranche paginée pour le client JavaFX (réduit mémoire et taille sérialisée).
     */
    public static List<Product> getPage(String category, int offset, int limit) {
        try {
            if (category == null || category.isBlank() || "Tous".equalsIgnoreCase(category.trim())) {
                return new ArrayList<>(ProductCatalogDAO.loadPage(offset, limit));
            }
            return new ArrayList<>(ProductCatalogDAO.loadPageByCategory(category.trim(), offset, limit));
        } catch (Exception e) {
            ChrionlineLog.err("ProductRepository: paged load failed — " + e.getMessage());
            List<Product> all = new ArrayList<>(getByCategory(category != null ? category : ""));
            int from = Math.min(Math.max(0, offset), all.size());
            int to = Math.min(from + Math.max(0, limit), all.size());
            return new ArrayList<>(all.subList(from, to));
        }
    }

    /** Recherche par identifiant numérique en base, sinon correspondance dans le repli mémoire. */
    public static Optional<Product> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        try {
            int pid = Integer.parseInt(id.trim());
            Product p = ProductCatalogDAO.findByProductId(pid);
            if (p != null) {
                return Optional.of(p);
            }
        } catch (Exception ignored) {
        }
        return FALLBACK.stream().filter(p -> p.getId().equals(id)).findFirst();
    }

    /** Catégories distinctes (produits publiés uniquement), avec « Tous » en tête pour l’UI. */
    public static Set<String> distinctCategories() {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        set.add("Tous");
        try {
            for (String cat : ProductCatalogDAO.distinctCategoriesApproved()) {
                if (cat != null && !cat.isBlank()) {
                    set.add(cat);
                }
            }
        } catch (Exception e) {
            for (Product p : FALLBACK) {
                if (p.getCategory() != null && !p.getCategory().isBlank()) {
                    set.add(p.getCategory());
                }
            }
        }
        return set;
    }

    /** Met à jour le stock en base ou dans le repli mémoire. */
    public static boolean updateStock(String id, int newStock) {
        try {
            int pid = Integer.parseInt(id.trim());
            return ProductCatalogDAO.updateStock(pid, newStock);
        } catch (Exception e) {
            Optional<Product> opt = FALLBACK.stream().filter(p -> p.getId().equals(id)).findFirst();
            if (opt.isPresent()) {
                opt.get().setStock(newStock);
                return true;
            }
            return false;
        }
    }
}
