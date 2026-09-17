package app.aura.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;

/**
 * Sanal dolaptaki tek bir kiyafet parcasi.
 *
 * `category` alani Vision servisindeki CLIP asamasindan gelen etiketi tutar
 * (orn. "t-shirt", "jacket"). `imageBase64` ise SAM ile arka plani silinmis
 * kesimin base64 kodlanmis halidir.
 */
@Entity
@Table(name = "wardrobe_items")
public class WardrobeItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @NotBlank
    @Column(nullable = false, length = 60)
    private String category;

    /** CLIP'in top-1 tahminine ait guven skoru (0-1). Vision servisi gondermezse null. */
    @Column(name = "category_confidence")
    private Double categoryConfidence;

    // Kesim gorselleri buyuk oldugu icin TEXT; v0.20+ imageUrl tercih edilir.
    @Column(name = "image_base64", columnDefinition = "TEXT")
    private String imageBase64;

    /** Object storage / CDN URL (MinIO, R2) — stüdyo normalize tercih edilir. */
    @Column(name = "image_url", length = 1024)
    private String imageUrl;

    /** Kullanicinin yukledigi ham / cutout URL (normalize oncesi). */
    @Column(name = "original_image_url", length = 1024)
    private String originalImageUrl;

    /** Gorselin MIME tipi; mobil istemci data URI kurarken kullanir. */
    @Column(name = "image_mime_type", length = 40)
    private String imageMimeType;

    @Column(length = 40)
    private String color;

    protected WardrobeItem() {
        // JPA icin gerekli
    }

    public WardrobeItem(String category, String imageBase64, String color) {
        this.category = category;
        this.imageBase64 = imageBase64;
        this.color = color;
    }

    public WardrobeItem(
            String category,
            Double categoryConfidence,
            String imageBase64,
            String imageMimeType,
            String color) {
        this(category, imageBase64, color);
        this.categoryConfidence = categoryConfidence;
        this.imageMimeType = imageMimeType;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    void setUser(User user) {
        this.user = user;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Double getCategoryConfidence() {
        return categoryConfidence;
    }

    public void setCategoryConfidence(Double categoryConfidence) {
        this.categoryConfidence = categoryConfidence;
    }

    public String getImageBase64() {
        return imageBase64;
    }

    public void setImageBase64(String imageBase64) {
        this.imageBase64 = imageBase64;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getOriginalImageUrl() {
        return originalImageUrl;
    }

    public void setOriginalImageUrl(String originalImageUrl) {
        this.originalImageUrl = originalImageUrl;
    }

    public String getImageMimeType() {
        return imageMimeType;
    }

    public void setImageMimeType(String imageMimeType) {
        this.imageMimeType = imageMimeType;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }
}
