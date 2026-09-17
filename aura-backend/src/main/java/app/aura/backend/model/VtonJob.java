package app.aura.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Virtual Try-On is kaydi (`vton_jobs`).
 *
 * Faz 9.1a: Java orkestrasyon iskeleti; sonuc goruntusu sonraki dilimde
 * gercek worker'dan gelecek.
 */
@Entity
@Table(name = "vton_jobs")
public class VtonJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "wardrobe_item_id", nullable = false)
    private Long wardrobeItemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VtonJobStatus status = VtonJobStatus.QUEUED;

    /** Dis worker (Python) is kimligi; mock'ta uretilir. */
    @Column(name = "worker_job_id", length = 80)
    private String workerJobId;

    /** Kullanici kisi fotografi (opsiyonel MVP; sonraki dilimde zorunlu olabilir). */
    @Column(name = "person_image_base64", columnDefinition = "TEXT")
    private String personImageBase64;

    /** Kisi fotografi object storage URL (v0.20+). */
    @Column(name = "person_image_url", length = 1024)
    private String personImageUrl;

    /** Tamamlaninca sonuc URI veya mock placeholder. */
    @Column(name = "result_image_uri", length = 1024)
    private String resultImageUri;

    /** Worker'dan gelen sonuc PNG/JPEG (ciplak base64); mobil Image.memory icin. */
    @Column(name = "result_image_base64", columnDefinition = "TEXT")
    private String resultImageBase64;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    /** Kullanici Lookbook arsivine eklediyse true. */
    @Column(name = "lookbook_saved", nullable = false)
    private boolean lookbookSaved = false;

    /** Bu is icin kota dusuldu mu (iade icin). */
    @Column(name = "quota_charged", nullable = false)
    private boolean quotaCharged = false;

    /** Kota iade edildi mi (cift iade onleme). */
    @Column(name = "quota_refunded", nullable = false)
    private boolean quotaRefunded = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected VtonJob() {
    }

    public VtonJob(User user, Long wardrobeItemId, String personImageBase64) {
        this(user, wardrobeItemId, personImageBase64, null);
    }

    public VtonJob(User user, Long wardrobeItemId, String personImageBase64, String personImageUrl) {
        this.user = user;
        this.wardrobeItemId = wardrobeItemId;
        this.personImageBase64 = personImageBase64;
        this.personImageUrl = personImageUrl;
        this.status = VtonJobStatus.QUEUED;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public void markQueued(String workerJobId) {
        this.workerJobId = workerJobId;
        this.status = VtonJobStatus.QUEUED;
        this.errorMessage = null;
    }

    public void markProcessing() {
        this.status = VtonJobStatus.PROCESSING;
        this.errorMessage = null;
    }

    public void markCompleted(String resultImageUri) {
        markCompleted(resultImageUri, null);
    }

    public void markCompleted(String resultImageUri, String resultImageBase64) {
        this.status = VtonJobStatus.COMPLETED;
        this.resultImageUri = resultImageUri;
        this.resultImageBase64 = resultImageBase64;
        this.errorMessage = null;
    }

    public void markFailed(String errorMessage) {
        this.status = VtonJobStatus.FAILED;
        this.errorMessage = errorMessage;
    }

    public void markQuotaCharged() {
        this.quotaCharged = true;
    }

    public void markQuotaRefunded() {
        this.quotaRefunded = true;
    }

    public void markLookbookSaved() {
        this.lookbookSaved = true;
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

    public Long getWardrobeItemId() {
        return wardrobeItemId;
    }

    public VtonJobStatus getStatus() {
        return status;
    }

    public String getWorkerJobId() {
        return workerJobId;
    }

    public String getPersonImageBase64() {
        return personImageBase64;
    }

    public String getPersonImageUrl() {
        return personImageUrl;
    }

    public void setPersonImageUrl(String personImageUrl) {
        this.personImageUrl = personImageUrl;
    }

    public String getResultImageUri() {
        return resultImageUri;
    }

    public String getResultImageBase64() {
        return resultImageBase64;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean isLookbookSaved() {
        return lookbookSaved;
    }

    public boolean isQuotaCharged() {
        return quotaCharged;
    }

    public boolean isQuotaRefunded() {
        return quotaRefunded;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
