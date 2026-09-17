package app.aura.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Kullanicinin kaydettigi favori kombin anligi.
 */
@Entity
@Table(name = "outfit_favorites")
public class OutfitFavorite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(length = 120)
    private String vibe;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(length = 40)
    private String occasion;

    @Column(name = "temperature_celsius")
    private Double temperatureCelsius;

    @Column(name = "season_band", length = 20)
    private String seasonBand;

    @Column(name = "match_score")
    private Double matchScore;

    @Column(name = "color_harmony_type", length = 40)
    private String colorHarmonyType;

    @Column(name = "color_harmony_score")
    private Double colorHarmonyScore;

    @Column(name = "top_item_id")
    private Long topItemId;

    @Column(name = "bottom_item_id")
    private Long bottomItemId;

    @Column(name = "accessory_item_id")
    private Long accessoryItemId;

    @Column(name = "perfume_catalog_id", length = 80)
    private String perfumeCatalogId;

    @Column(name = "perfume_label", length = 200)
    private String perfumeLabel;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OutfitFavorite() {
    }

    public OutfitFavorite(
            String vibe,
            String summary,
            String occasion,
            Double temperatureCelsius,
            String seasonBand,
            Double matchScore,
            String colorHarmonyType,
            Double colorHarmonyScore,
            Long topItemId,
            Long bottomItemId,
            Long accessoryItemId,
            String perfumeCatalogId,
            String perfumeLabel) {
        this.vibe = vibe;
        this.summary = summary;
        this.occasion = occasion;
        this.temperatureCelsius = temperatureCelsius;
        this.seasonBand = seasonBand;
        this.matchScore = matchScore;
        this.colorHarmonyType = colorHarmonyType;
        this.colorHarmonyScore = colorHarmonyScore;
        this.topItemId = topItemId;
        this.bottomItemId = bottomItemId;
        this.accessoryItemId = accessoryItemId;
        this.perfumeCatalogId = perfumeCatalogId;
        this.perfumeLabel = perfumeLabel;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
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

    public String getVibe() {
        return vibe;
    }

    public String getSummary() {
        return summary;
    }

    public String getOccasion() {
        return occasion;
    }

    public Double getTemperatureCelsius() {
        return temperatureCelsius;
    }

    public String getSeasonBand() {
        return seasonBand;
    }

    public Double getMatchScore() {
        return matchScore;
    }

    public String getColorHarmonyType() {
        return colorHarmonyType;
    }

    public Double getColorHarmonyScore() {
        return colorHarmonyScore;
    }

    public Long getTopItemId() {
        return topItemId;
    }

    public Long getBottomItemId() {
        return bottomItemId;
    }

    public Long getAccessoryItemId() {
        return accessoryItemId;
    }

    public String getPerfumeCatalogId() {
        return perfumeCatalogId;
    }

    public String getPerfumeLabel() {
        return perfumeLabel;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
