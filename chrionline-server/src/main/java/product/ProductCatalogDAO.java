package product;

import chrionline.BaseDonnees;
import common.ChrionlineLog;
import common.TextUiNormalizer;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.Locale;

/**
 * Charge les lignes du catalogue depuis MySQL {@code products} (voir {@code sql/products_inserts.sql}).
 * Le catalogue public ne montre que les fiches {@code listing_status = APPROVED} (ou anciennes lignes sans
 * colonne migrée : {@code NULL} traité comme approuvé).
 */
public final class ProductCatalogDAO {

    private static final int SKU_MAX = 40;
    private static final int NAME_MAX = 255;
    private static final int BRAND_MAX = 120;
    private static final int CATEGORY_MAX = 120;
    private static final int IMAGE_URL_MAX = 768;

    private static final List<String> DEFAULT_ADMIN_CATEGORIES =
            List.of(
                    "Accessoires sport",
                    "Electronique",
                    "Maison & decoration",
                    "Produits de bain & soin",
                    "Vêtements & mode");

    private ProductCatalogDAO() {}

    /**
     * Filtre catalogue public : vendeur / admin voient tout via d’autres méthodes.
     */
    private static final String WHERE_APPROVED =
            "WHERE (listing_status = 'APPROVED' OR listing_status IS NULL) ";

    /**
     * Alias SQL pour que {@link #mapRow} fonctionne avec le schéma snake_case ({@code product_id}, …) ou mixte.
     */
    private static final String SELECT_ROW =
            "SELECT product_id AS Product_ID, nom_produit AS Nom_produit, "
                    + "COALESCE(description, '') AS Description, "
                    + "COALESCE(prix_net_usd, prix_usd * (1 - COALESCE(remise_pct, 0))) AS Prix_net_USD, "
                    + "stock AS Stock, "
                    + "COALESCE(image_principale, '') AS Image_principale, "
                    + "categorie_metier AS Categorie_metier, "
                    + "COALESCE(marque, '') AS Marque, "
                    + "COALESCE(rating, 0) AS Rating "
                    + "FROM products ";

    /** Tous les produits approuvés, triés par identifiant. */
    public static List<Product> loadAll() throws SQLException {
        String sql = SELECT_ROW + WHERE_APPROVED + "ORDER BY product_id";
        try (Connection c = BaseDonnees.getConnection();
                PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            return mapRows(rs);
        }
    }

    /** Filtre {@code categorie_metier} avec variantes orthographiques ({@link TextUiNormalizer}). */
    public static List<Product> loadByCategory(String category) throws SQLException {
        if (category == null || category.isBlank()) {
            return loadAll();
        }
        List<String> vars = TextUiNormalizer.categoryMatchVariants(category);
        if (vars.isEmpty()) {
            return loadAll();
        }
        String inClause = String.join(",", Collections.nCopies(vars.size(), "?"));
        String sql = SELECT_ROW + WHERE_APPROVED + "AND categorie_metier IN (" + inClause + ") ORDER BY product_id";
        try (Connection c = BaseDonnees.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < vars.size(); i++) {
                ps.setString(i + 1, vars.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                return mapRows(rs);
            }
        }
    }

    /** Page du catalogue complet (LIMIT / OFFSET). */
    public static List<Product> loadPage(int offset, int limit) throws SQLException {
        if (limit <= 0) {
            return List.of();
        }
        int off = Math.max(0, offset);
        String sql = SELECT_ROW + WHERE_APPROVED + "ORDER BY product_id LIMIT ? OFFSET ?";
        try (Connection c = BaseDonnees.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ps.setInt(2, off);
            try (ResultSet rs = ps.executeQuery()) {
                return mapRows(rs);
            }
        }
    }

    /** Page filtrée par catégorie avec variantes de libellé. */
    public static List<Product> loadPageByCategory(String category, int offset, int limit) throws SQLException {
        if (category == null || category.isBlank()) {
            return loadPage(offset, limit);
        }
        if (limit <= 0) {
            return List.of();
        }
        List<String> vars = TextUiNormalizer.categoryMatchVariants(category);
        if (vars.isEmpty()) {
            return loadPage(offset, limit);
        }
        int off = Math.max(0, offset);
        String inClause = String.join(",", Collections.nCopies(vars.size(), "?"));
        String sql =
                SELECT_ROW
                        + WHERE_APPROVED
                        + "AND categorie_metier IN ("
                        + inClause
                        + ") ORDER BY product_id LIMIT ? OFFSET ?";
        try (Connection c = BaseDonnees.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < vars.size(); i++) {
                ps.setString(i + 1, vars.get(i));
            }
            ps.setInt(vars.size() + 1, limit);
            ps.setInt(vars.size() + 2, off);
            try (ResultSet rs = ps.executeQuery()) {
                return mapRows(rs);
            }
        }
    }

    /** Détail catalogue public : uniquement les fiches approuvées. */
    public static Product findByProductId(int productId) throws SQLException {
        String sql = SELECT_ROW + WHERE_APPROVED + "AND product_id = ?";
        try (Connection c = BaseDonnees.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, productId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }
        return null;
    }

    /** Catégories distinctes (exclut fiches non publiées). */
    public static List<String> distinctCategoriesApproved() throws SQLException {
        List<String> out = new ArrayList<>();
        String sql =
                "SELECT DISTINCT categorie_metier FROM products "
                        + WHERE_APPROVED
                        + "AND categorie_metier IS NOT NULL AND TRIM(categorie_metier) <> '' "
                        + "ORDER BY categorie_metier";
        try (Connection c = BaseDonnees.getConnection();
                PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String cat = rs.getString(1);
                if (cat != null && !cat.isBlank()) {
                    out.add(cat.trim());
                }
            }
        }
        return out;
    }

    /** Soumission vendeur : statut {@code PENDING}. */
    public static int insertPendingListing(
            String sku,
            String nomProduit,
            String marque,
            String categorieMetier,
            double prixUsd,
            int stock,
            String description,
            String imageUrl,
            int sellerId)
            throws SQLException {
        String cat = cleanCategory(categorieMetier);
        String skuVal = sku != null && !sku.isBlank() ? sku.trim() : "SELL-TMP";
        double remise = 0;
        double prixNet = prixUsd * (1 - remise);
        String desc = description != null ? description : "";
        String img = imageUrl != null ? imageUrl : "";
        String brand = marque != null && !marque.isBlank() ? marque.trim() : null;
        String sql =
                "INSERT INTO products (sku, nom_produit, marque, categorie_source, categorie_metier, "
                        + "prix_usd, remise_pct, prix_net_usd, rating, stock, disponibilite, description, image_principale, nb_images, source_catalogue, "
                        + "listing_status, seller_id, submitted_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,NOW())";
        try (Connection c = BaseDonnees.getConnection();
                PreparedStatement ps =
                        c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, skuVal);
            ps.setString(2, nomProduit.trim());
            ps.setString(3, brand);
            ps.setString(4, cat);
            ps.setString(5, cat);
            ps.setDouble(6, prixUsd);
            ps.setDouble(7, remise);
            ps.setDouble(8, prixNet);
            ps.setDouble(9, 0);
            ps.setInt(10, stock);
            ps.setString(11, "En attente validation");
            ps.setString(12, desc);
            ps.setString(13, img.isEmpty() ? null : img);
            ps.setInt(14, img.isEmpty() ? 0 : 1);
            ps.setString(15, "VENDEUR");
            ps.setString(16, "PENDING");
            ps.setInt(17, sellerId);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    int id = keys.getInt(1);
                    if (skuVal.equals("SELL-TMP")) {
                        try (PreparedStatement up =
                                c.prepareStatement("UPDATE products SET sku = ? WHERE product_id = ?")) {
                            up.setString(1, "SELL-" + id);
                            up.setInt(2, id);
                            up.executeUpdate();
                        }
                    }
                    return id;
                }
            }
        }
        throw new SQLException("product id not generated");
    }

    /** Fiches en attente de modération. */
    public static List<ProductListingInfo> listPending() throws SQLException {
        List<ProductListingInfo> list = new ArrayList<>();
        String sql =
                "SELECT product_id, nom_produit, sku, seller_id, listing_status, submitted_at, COALESCE(rejection_reason,'') "
                        + "FROM products WHERE listing_status = 'PENDING' ORDER BY submitted_at DESC, product_id DESC";
        try (Connection c = BaseDonnees.getConnection();
                PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapListingInfo(rs));
            }
        }
        return list;
    }

    /** Fiches d’un vendeur (tous statuts). */
    public static List<ProductListingInfo> listBySellerId(int sellerId) throws SQLException {
        List<ProductListingInfo> list = new ArrayList<>();
        String sql =
                "SELECT product_id, nom_produit, sku, seller_id, listing_status, submitted_at, COALESCE(rejection_reason,'') "
                        + "FROM products WHERE seller_id = ? ORDER BY product_id DESC";
        try (Connection c = BaseDonnees.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, sellerId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapListingInfo(rs));
                }
            }
        }
        return list;
    }

    private static ProductListingInfo mapListingInfo(ResultSet rs) throws SQLException {
        int pid = rs.getInt("product_id");
        String name = rs.getString("nom_produit");
        String sku = rs.getString("sku");
        int sid = rs.getInt("seller_id");
        String st = rs.getString("listing_status");
        Timestamp ts = rs.getTimestamp("submitted_at");
        long ms = ts != null ? ts.getTime() : 0L;
        String rr = rs.getString(7);
        return new ProductListingInfo(pid, name != null ? name : "", sku != null ? sku : "", sid, st != null ? st : "", ms, rr != null ? rr : "");
    }

    public static boolean approve(int productId, int adminUserId) throws SQLException {
        String sql =
                "UPDATE products SET listing_status = 'APPROVED', reviewed_at = NOW(), reviewed_by_user_id = ?, "
                        + "rejection_reason = NULL, disponibilite = 'En stock' "
                        + "WHERE product_id = ? AND listing_status = 'PENDING'";
        try (Connection c = BaseDonnees.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, adminUserId);
            ps.setInt(2, productId);
            return ps.executeUpdate() > 0;
        }
    }

    public static boolean reject(int productId, int adminUserId, String reason) throws SQLException {
        String sql =
                "UPDATE products SET listing_status = 'REJECTED', reviewed_at = NOW(), reviewed_by_user_id = ?, "
                        + "rejection_reason = ?, disponibilite = 'Refusé' "
                        + "WHERE product_id = ? AND listing_status = 'PENDING'";
        try (Connection c = BaseDonnees.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, adminUserId);
            String r = reason != null && !reason.isBlank() ? reason.trim() : "Refusé par la modération";
            ps.setString(2, r);
            ps.setInt(3, productId);
            return ps.executeUpdate() > 0;
        }
    }

    /** Met à jour la colonne {@code stock} et synchronise l’état de disponibilité. */
    public static boolean updateStock(int productId, int newStock) throws SQLException {
        String sql = "UPDATE products SET stock = ?, disponibilite = ? WHERE product_id = ?";
        try (Connection c = BaseDonnees.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, newStock);
            ps.setString(2, newStock > 0 ? "En stock" : "Rupture de stock");
            ps.setInt(3, productId);
            return ps.executeUpdate() > 0;
        }
    }

    /**
     * Insertion directe par un administrateur : fiche immédiatement {@code APPROVED}, sans file d’attente vendeur.
     *
     * <p>Important : quand le SKU est vide, on génère un SKU unique avant l’INSERT. L’ancienne version insérait
     * d’abord le SKU temporaire {@code ADMIN-TMP}, puis le remplaçait après génération de l’id ; si une ligne
     * {@code ADMIN-TMP} restait en base ou si {@code sku} était UNIQUE, la création échouait avec DB_ERROR.
     */
    public static int insertAdminDirectApproved(
            String sku,
            String nomProduit,
            String marque,
            String categorieMetier,
            double prixUsd,
            int stock,
            String description,
            String imageUrl,
            int adminUserId)
            throws SQLException {
        String name = cleanText(nomProduit);
        String cat = cleanCategory(categorieMetier);
        String desc = cleanText(description);
        String img = cleanText(imageUrl);
        String brand = cleanText(marque);

        requireLength(name, NAME_MAX, "nom_produit");
        requireLength(cat, CATEGORY_MAX, "categorie_metier");
        requireLength(brand, BRAND_MAX, "marque");
        requireLength(img, IMAGE_URL_MAX, "image_principale");

        double remise = 0;
        double prixNet = prixUsd * (1 - remise);
        String availability = stock > 0 ? "En stock" : "Rupture de stock";

        try (Connection c = BaseDonnees.getConnection()) {
            ProductTableShape shape = inspectProductsTable(c);
            verifyAdminProductCoreSchema(shape);
            String skuVal = prepareAdminSku(c, sku);
            String sql = buildAdminInsertSql(shape);
            ChrionlineLog.info(
                    "ADMIN_PRODUCT_CREATE debug: db="
                            + c.getCatalog()
                            + ", generatedSku="
                            + skuVal
                            + ", sqlPath="
                            + shape.sqlPath());
            try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                int i = 1;
                ps.setString(i++, skuVal);
                ps.setString(i++, name);
                ps.setString(i++, brand.isBlank() ? null : brand);
                ps.setString(i++, cat);
                ps.setString(i++, cat);
                ps.setDouble(i++, prixUsd);
                ps.setDouble(i++, remise);
                ps.setDouble(i++, prixNet);
                ps.setDouble(i++, 0);
                ps.setInt(i++, stock);
                ps.setString(i++, availability);
                ps.setString(i++, desc);
                ps.setString(i++, img);
                ps.setInt(i++, img.isEmpty() ? 0 : 1);
                ps.setString(i++, "ADMIN");
                if (shape.has("status")) {
                    ps.setString(i++, "ACTIVE");
                }
                if (shape.has("listing_status")) {
                    ps.setString(i++, "APPROVED");
                }
                if (shape.has("seller_id")) {
                    ps.setInt(i++, adminUserId);
                }
                if (shape.has("reviewed_by_user_id")) {
                    ps.setInt(i++, adminUserId);
                }
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getInt(1);
                    }
                }
            }
        }
        throw new SQLException("PRODUCT_ID_NOT_GENERATED");
    }

    /** Supprime une ligne catalogue (aucune FK {@code order_lines} → {@code products} dans le schéma fourni). */
    public static boolean deleteProductById(int productId) throws SQLException {
        String sql = "DELETE FROM products WHERE product_id = ?";
        try (Connection c = BaseDonnees.getConnection();
                PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, productId);
            return ps.executeUpdate() > 0;
        }
    }

    /** Liste brute pour console admin (inclut statuts non publiés). */
    public static List<AdminProductRow> listAllForAdmin() throws SQLException {
        return listAllForAdmin(null);
    }

    /**
     * Liste brute pour console admin avec recherche optionnelle par ID, nom, SKU, marque ou catégorie.
     */
    public static List<AdminProductRow> listAllForAdmin(String search) throws SQLException {
        List<AdminProductRow> list = new ArrayList<>();
        String q = search != null ? search.trim() : "";
        boolean hasSearch = !q.isBlank();
        try (Connection c = BaseDonnees.getConnection()) {
            ProductTableShape shape = inspectProductsTable(c);
            String statusExpr = shape.has("listing_status") ? "COALESCE(listing_status,'')" : "'APPROVED'";
            String sql =
                    "SELECT product_id, nom_produit, sku, COALESCE(marque,'') AS marque, "
                            + "COALESCE(categorie_metier,'') AS categorie_metier, stock, "
                            + statusExpr
                            + " AS listing_status, prix_usd "
                            + "FROM products "
                            + (hasSearch
                                    ? "WHERE CAST(product_id AS CHAR) = ? OR LOWER(nom_produit) LIKE ? "
                                            + "OR LOWER(COALESCE(sku,'')) LIKE ? "
                                            + "OR LOWER(COALESCE(marque,'')) LIKE ? "
                                            + "OR LOWER(COALESCE(categorie_metier,'')) LIKE ? "
                                    : "")
                            + "ORDER BY product_id DESC LIMIT 1000";
            try (PreparedStatement ps = c.prepareStatement(sql)) {
            if (hasSearch) {
                String like = "%" + q.toLowerCase(Locale.ROOT) + "%";
                ps.setString(1, q);
                ps.setString(2, like);
                ps.setString(3, like);
                ps.setString(4, like);
                ps.setString(5, like);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String st = rs.getString("listing_status");
                    list.add(
                            new AdminProductRow(
                                    rs.getInt("product_id"),
                                    safe(rs.getString("nom_produit")),
                                    safe(rs.getString("sku")),
                                    safe(rs.getString("marque")),
                                    safe(rs.getString("categorie_metier")),
                                    rs.getInt("stock"),
                                    st != null ? st : "",
                                    rs.getDouble("prix_usd")));
                }
            }
            }
        }
        return list;
    }

    /** Catégories proposées à l’admin pour éviter les erreurs de saisie. */
    public static List<String> distinctCategoriesForAdmin() throws SQLException {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        String sql =
                "SELECT DISTINCT categorie_metier FROM products "
                        + "WHERE categorie_metier IS NOT NULL AND TRIM(categorie_metier) <> '' "
                        + "ORDER BY categorie_metier";
        try (Connection c = BaseDonnees.getConnection();
                PreparedStatement ps = c.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String cat = cleanCategory(rs.getString(1));
                if (!cat.isBlank()) {
                    out.add(cat);
                }
            }
        }
        if (out.isEmpty()) {
            out.addAll(defaultAdminCategories());
        }
        return new ArrayList<>(out);
    }

    /** Catégories métier proposées par défaut quand la base ne contient pas encore de données propres. */
    public static List<String> defaultAdminCategories() {
        return DEFAULT_ADMIN_CATEGORIES;
    }

    public static boolean isKnownAdminCategory(String category) {
        String cat = cleanCategory(category);
        try {
            for (String allowed : distinctCategoriesForAdmin()) {
                if (allowed.equalsIgnoreCase(cat)) {
                    return true;
                }
            }
        } catch (SQLException ignored) {
            for (String allowed : DEFAULT_ADMIN_CATEGORIES) {
                if (allowed.equalsIgnoreCase(cat)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String cleanCategory(String categorieMetier) {
        String cat = categorieMetier != null ? TextUiNormalizer.normalizeFrenchUi(categorieMetier).trim() : "";
        return cat.isBlank() ? "Général" : cat;
    }

    private static String cleanText(String s) {
        return s != null ? TextUiNormalizer.normalizeFrenchUi(s).trim() : "";
    }

    private static void requireLength(String value, int max, String column) throws SQLException {
        if (value != null && value.length() > max) {
            throw new SQLException("FIELD_TOO_LONG:" + column);
        }
    }

    private static String prepareAdminSku(Connection c, String sku) throws SQLException {
        String raw = cleanText(sku);
        String candidate = raw.isBlank() ? generateAdminSku() : raw;
        requireLength(candidate, SKU_MAX, "sku");
        if (!candidate.matches("[A-Za-z0-9._-]+")) {
            throw new SQLException("INVALID_SKU_FORMAT");
        }
        if (skuExists(c, candidate)) {
            throw new SQLIntegrityConstraintViolationException("DUPLICATE_SKU", "23000", 1062);
        }
        return candidate;
    }

    private static String generateAdminSku() {
        long ms = System.currentTimeMillis();
        int rand = (int) (Math.random() * 9000) + 1000;
        return "ADM-" + Long.toString(ms, 36).toUpperCase(Locale.ROOT) + "-" + rand;
    }

    private static boolean skuExists(Connection c, String sku) throws SQLException {
        String sql = "SELECT 1 FROM products WHERE sku = ? LIMIT 1";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, sku);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /** Vérifie les colonnes ajoutées par le mini-projet 2 avant d’écrire, afin de renvoyer une erreur claire. */
    private static void verifyAdminProductSchema(Connection c) throws SQLException {
        Set<String> required =
                Set.of(
                        "sku",
                        "nom_produit",
                        "marque",
                        "categorie_source",
                        "categorie_metier",
                        "prix_usd",
                        "remise_pct",
                        "prix_net_usd",
                        "rating",
                        "stock",
                        "disponibilite",
                        "description",
                        "image_principale",
                        "nb_images",
                        "source_catalogue",
                        "listing_status",
                        "seller_id",
                        "submitted_at",
                        "reviewed_at",
                        "reviewed_by_user_id");
        DatabaseMetaData md = c.getMetaData();
        String catalog = c.getCatalog();
        java.util.HashSet<String> existing = new java.util.HashSet<>();
        try (ResultSet rs = md.getColumns(catalog, null, "products", null)) {
            while (rs.next()) {
                existing.add(rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
        }
        if (!existing.containsAll(required)) {
            java.util.ArrayList<String> missing = new java.util.ArrayList<>();
            for (String col : required) {
                if (!existing.contains(col)) {
                    missing.add(col);
                }
            }
            throw new SQLException("PRODUCT_SCHEMA_OUTDATED missing=" + missing, "42S22");
        }
    }

    private static ProductTableShape inspectProductsTable(Connection c) throws SQLException {
        DatabaseMetaData md = c.getMetaData();
        String catalog = c.getCatalog();
        java.util.HashSet<String> existing = new java.util.HashSet<>();
        try (ResultSet rs = md.getColumns(catalog, null, "products", null)) {
            while (rs.next()) {
                existing.add(rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
        }
        return new ProductTableShape(existing);
    }

    private static void verifyAdminProductCoreSchema(ProductTableShape shape) throws SQLException {
        Set<String> required =
                Set.of(
                        "sku",
                        "nom_produit",
                        "marque",
                        "categorie_source",
                        "categorie_metier",
                        "prix_usd",
                        "remise_pct",
                        "prix_net_usd",
                        "rating",
                        "stock",
                        "disponibilite",
                        "description",
                        "image_principale",
                        "nb_images",
                        "source_catalogue");
        if (!shape.columns.containsAll(required)) {
            java.util.ArrayList<String> missing = new java.util.ArrayList<>();
            for (String col : required) {
                if (!shape.columns.contains(col)) {
                    missing.add(col);
                }
            }
            throw new SQLException("PRODUCT_SCHEMA_OUTDATED missing=" + missing, "42S22");
        }
    }

    private static String buildAdminInsertSql(ProductTableShape shape) {
        StringBuilder columns =
                new StringBuilder(
                        "sku, nom_produit, marque, categorie_source, categorie_metier, "
                                + "prix_usd, remise_pct, prix_net_usd, rating, stock, disponibilite, description, image_principale, nb_images, source_catalogue");
        StringBuilder values = new StringBuilder("?,?,?,?,?,?,?,?,?,?,?,?,?,?,?");
        if (shape.has("status")) {
            columns.append(", status");
            values.append(",?");
        }
        if (shape.has("listing_status")) {
            columns.append(", listing_status");
            values.append(",?");
        }
        if (shape.has("seller_id")) {
            columns.append(", seller_id");
            values.append(",?");
        }
        if (shape.has("submitted_at")) {
            columns.append(", submitted_at");
            values.append(",NOW()");
        }
        if (shape.has("reviewed_at")) {
            columns.append(", reviewed_at");
            values.append(",NOW()");
        }
        if (shape.has("reviewed_by_user_id")) {
            columns.append(", reviewed_by_user_id");
            values.append(",?");
        }
        return "INSERT INTO products (" + columns + ") VALUES (" + values + ")";
    }

    private record ProductTableShape(Set<String> columns) {
        boolean has(String column) {
            return columns.contains(column.toLowerCase(Locale.ROOT));
        }

        String sqlPath() {
            return "columns[status="
                    + has("status")
                    + ",listing_status="
                    + has("listing_status")
                    + ",seller_id="
                    + has("seller_id")
                    + ",submitted_at="
                    + has("submitted_at")
                    + ",reviewed_at="
                    + has("reviewed_at")
                    + ",reviewed_by_user_id="
                    + has("reviewed_by_user_id")
                    + "]";
        }
    }

    private static String safe(String s) {
        return s != null ? TextUiNormalizer.normalizeFrenchUi(s) : "";
    }

    private static List<Product> mapRows(ResultSet rs) throws SQLException {
        List<Product> list = new ArrayList<>();
        while (rs.next()) {
            list.add(mapRow(rs));
        }
        return list;
    }

    /** Mappe une ligne SQL vers {@link Product} avec normalisation UI française. */
    private static Product mapRow(ResultSet rs) throws SQLException {
        int pid = rs.getInt("Product_ID");
        String name = TextUiNormalizer.normalizeFrenchUi(rs.getString("Nom_produit"));
        String desc = TextUiNormalizer.normalizeFrenchUi(rs.getString("Description"));
        double price = rs.getDouble("Prix_net_USD");
        int stock = rs.getInt("Stock");
        String img = rs.getString("Image_principale");
        String cat = TextUiNormalizer.normalizeFrenchUi(rs.getString("Categorie_metier"));
        String brand = TextUiNormalizer.normalizeFrenchUi(rs.getString("Marque"));
        if (rs.wasNull()) {
            brand = "";
        }
        double rating = rs.getDouble("Rating");
        return new Product(
                String.valueOf(pid),
                name != null ? name : "",
                desc != null ? desc : "",
                price,
                stock,
                img != null ? img : "",
                cat != null ? cat : "",
                brand,
                rating);
    }
}
