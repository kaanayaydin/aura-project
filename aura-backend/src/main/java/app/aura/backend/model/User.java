package app.aura.backend.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Uygulama kullanicisi. Sanal dolap ve parfum rafinin sahibi.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false, unique = true, length = 120)
    private String username;

    @NotBlank
    @Email
    @Column(nullable = false, unique = true, length = 120)
    private String email;

    /** BCrypt hash; demo kullanicilarda null olabilir. */
    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 20)
    private AccountStatus accountStatus = AccountStatus.ACTIVE;

    @Column(name = "failed_login_attempts", nullable = false)
    private int failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    /** Gunluk VTON deneme sayaci (maliyet korumasi). */
    @Column(name = "daily_vton_count", nullable = false)
    private int dailyVtonCount = 0;

    /** Son sayac sifirlama gunu (yerel tarih). */
    @Column(name = "last_vton_reset_date")
    private LocalDate lastVtonResetDate;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WardrobeItem> wardrobeItems = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<UserPerfume> perfumes = new ArrayList<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OutfitFavorite> favorites = new ArrayList<>();

    protected User() {
        // JPA icin gerekli
    }

    public User(String username, String email) {
        this.username = username;
        this.email = email;
    }

    public void addWardrobeItem(WardrobeItem item) {
        wardrobeItems.add(item);
        item.setUser(this);
    }

    public void removeWardrobeItem(WardrobeItem item) {
        wardrobeItems.remove(item);
        item.setUser(null);
    }

    public void addPerfume(UserPerfume perfume) {
        perfumes.add(perfume);
        perfume.setUser(this);
    }

    public void removePerfume(UserPerfume perfume) {
        perfumes.remove(perfume);
        perfume.setUser(null);
    }

    public void addFavorite(OutfitFavorite favorite) {
        favorites.add(favorite);
        favorite.setUser(this);
    }

    public void removeFavorite(OutfitFavorite favorite) {
        favorites.remove(favorite);
        favorite.setUser(null);
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public AccountStatus getAccountStatus() {
        return accountStatus;
    }

    public void setAccountStatus(AccountStatus accountStatus) {
        this.accountStatus = accountStatus;
    }

    public int getFailedLoginAttempts() {
        return failedLoginAttempts;
    }

    public void setFailedLoginAttempts(int failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public void setLockedUntil(Instant lockedUntil) {
        this.lockedUntil = lockedUntil;
    }

    public int getDailyVtonCount() {
        return dailyVtonCount;
    }

    public void setDailyVtonCount(int dailyVtonCount) {
        this.dailyVtonCount = dailyVtonCount;
    }

    public LocalDate getLastVtonResetDate() {
        return lastVtonResetDate;
    }

    public void setLastVtonResetDate(LocalDate lastVtonResetDate) {
        this.lastVtonResetDate = lastVtonResetDate;
    }

    public List<WardrobeItem> getWardrobeItems() {
        return wardrobeItems;
    }

    public List<UserPerfume> getPerfumes() {
        return perfumes;
    }

    public List<OutfitFavorite> getFavorites() {
        return favorites;
    }
}
