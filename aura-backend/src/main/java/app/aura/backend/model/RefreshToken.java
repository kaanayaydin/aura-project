package app.aura.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Refresh token kaydi — ham token asla saklanmaz, yalnizca SHA-256 hash.
 */
@Entity
@Table(
        name = "refresh_tokens",
        indexes = @Index(
                name = "idx_refresh_user_revoked_expires",
                columnList = "user_id, revoked, expires_at"))
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    /**
     * Sema nullable. Hibernate ddl-auto=update dolu Postgres tablosuna
     * NOT NULL kolon ekleyemez; ALTER kesilir, login 500 olur.
     * Yeni satirlarda constructor ve {@link #onCreate()} doldurur.
     */
    @Column(name = "issued_at")
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked = false;

    @Column(name = "device_info", length = 255)
    private String deviceInfo;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RefreshToken() {
    }

    public RefreshToken(User user, String tokenHash, Instant expiresAt, String deviceInfo) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.deviceInfo = deviceInfo;
        Instant now = Instant.now();
        this.issuedAt = now;
        this.createdAt = now;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        if (issuedAt == null) {
            issuedAt = now;
        }
        if (createdAt == null) {
            createdAt = issuedAt;
        }
    }

    public void revoke() {
        this.revoked = true;
    }

    public boolean isUsable(Instant now) {
        return !revoked && expiresAt.isAfter(now);
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public String getDeviceInfo() {
        return deviceInfo;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
