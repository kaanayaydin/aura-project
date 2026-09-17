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
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.NotBlank;

/**
 * Kullanicinin kisisel parfum rafindaki bir sise.
 *
 * Kuratorlu katalogdaki kayda `catalogId` ile baglanir; brand/name/notes
 * anlik gorunum icin denormalize tutulur.
 */
@Entity
@Table(
        name = "user_perfumes",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_user_perfume_catalog",
                columnNames = {"user_id", "catalog_id"}))
public class UserPerfume {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Kuratorlu katalogdaki benzersiz anahtar (orn. adp-colonia). */
    @NotBlank
    @Column(name = "catalog_id", nullable = false, length = 80)
    private String catalogId;

    @NotBlank
    @Column(nullable = false, length = 80)
    private String brand;

    @NotBlank
    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 40)
    private String concentration;

    /** Virgul ayrimli akorlar (fresh, woody...). */
    @Column(length = 200)
    private String chords;

    /** Ust/kalp/dip notalarinin ozet metni. */
    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(length = 40)
    private String diffusion;

    protected UserPerfume() {
        // JPA icin gerekli
    }

    public UserPerfume(
            String catalogId,
            String brand,
            String name,
            String concentration,
            String chords,
            String notes,
            String diffusion) {
        this.catalogId = catalogId;
        this.brand = brand;
        this.name = name;
        this.concentration = concentration;
        this.chords = chords;
        this.notes = notes;
        this.diffusion = diffusion;
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

    public String getCatalogId() {
        return catalogId;
    }

    public String getBrand() {
        return brand;
    }

    public String getName() {
        return name;
    }

    public String getConcentration() {
        return concentration;
    }

    public String getChords() {
        return chords;
    }

    public String getNotes() {
        return notes;
    }

    public String getDiffusion() {
        return diffusion;
    }
}
