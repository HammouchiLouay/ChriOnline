package product;

/**
 * Fiche produit pour modération ou espace vendeur (hors modèle {@link Product} affiché au catalogue).
 */
public record ProductListingInfo(
        int productId,
        String nomProduit,
        String sku,
        int sellerId,
        String listingStatus,
        long submittedAtMs,
        String rejectionReason) {}
