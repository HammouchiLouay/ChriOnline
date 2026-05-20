package product;

import java.io.Serializable;

/**
 * Produit du catalogue (sérialisable pour transport socket en binaire) : identifiant, libellés, prix, stock,
 * image, catégorie métier, marque, note.
 */
public class Product implements Serializable {
    private static final long serialVersionUID = 2L;

    private String id;
    private String name;
    private String description;
    private double price;
    private int stock;
    /** URL issue de la colonne {@code Image_principale} (ex. CDN DummyJSON). */
    private String imageUrl = "";
    /** Catégorie métier, ex. {@code Categorie_metier} dans {@code products}. */
    private String category = "";
    private String brand = "";
    private double rating;

    public Product() {}

    /** Constructeur minimal (sans image ni catégorie). */
    public Product(String id, String name, String description, double price, int stock) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
    }

    /** Constructeur complet aligné sur le schéma MySQL / DAO. */
    public Product(
            String id,
            String name,
            String description,
            double price,
            int stock,
            String imageUrl,
            String category,
            String brand,
            double rating) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.imageUrl = imageUrl != null ? imageUrl : "";
        this.category = category != null ? category : "";
        this.brand = brand != null ? brand : "";
        this.rating = rating;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public double getPrice() {
        return price;
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl != null ? imageUrl : "";
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category != null ? category : "";
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand != null ? brand : "";
    }

    public double getRating() {
        return rating;
    }

    public void setRating(double rating) {
        this.rating = rating;
    }
}
