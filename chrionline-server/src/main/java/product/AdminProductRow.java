package product;

/**
 * Ligne catalogue renvoyée aux administrateurs (liste complète, tous statuts).
 *
 * @param productId identifiant MySQL
 * @param nomProduit libellé
 * @param sku référence
 * @param marque marque commerciale
 * @param categorieMetier catégorie affichée dans l’application
 * @param stock quantité disponible
 * @param listingStatus PENDING / APPROVED / REJECTED
 * @param prixUsd prix catalogue USD
 */
public record AdminProductRow(
        int productId,
        String nomProduit,
        String sku,
        String marque,
        String categorieMetier,
        int stock,
        String listingStatus,
        double prixUsd) {}
